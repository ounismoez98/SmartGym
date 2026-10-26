package com.smartgym.training.application;

import java.util.Locale;
import java.util.UUID;

import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.Exercise;
import com.smartgym.training.domain.ExerciseDifficulty;
import com.smartgym.training.domain.MuscleGroup;
import com.smartgym.training.dto.ExercisePageResponse;
import com.smartgym.training.dto.ExercisePatchRequest;
import com.smartgym.training.dto.ExerciseRequest;
import com.smartgym.training.dto.ExerciseResponse;
import com.smartgym.training.repository.ExerciseRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ExerciseService {
    private final ExerciseRepository exercises;

    public ExerciseService(ExerciseRepository exercises) {
        this.exercises = exercises;
    }

    @Transactional(readOnly = true)
    public ExercisePageResponse list(
            String name,
            MuscleGroup muscleGroup,
            ExerciseDifficulty difficulty,
            EquipmentCategory equipmentCategory,
            int page,
            int size) {
        Specification<Exercise> specification = (root, query, builder) -> builder.conjunction();
        if (name != null && !name.isBlank()) {
            String search = "%" + name.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) ->
                    builder.like(builder.lower(root.get("name")), search));
        }
        if (muscleGroup != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("primaryMuscleGroup"), muscleGroup));
        }
        if (difficulty != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("difficulty"), difficulty));
        }
        if (equipmentCategory != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("equipmentCategory"), equipmentCategory));
        }
        specification = specification.and((root, query, builder) -> builder.isTrue(root.get("active")));

        Page<Exercise> result = exercises.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name")));
        return new ExercisePageResponse(
                result.getContent().stream().map(ExerciseResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ExerciseResponse get(UUID id) {
        return ExerciseResponse.from(find(id));
    }

    public ExerciseResponse create(ExerciseRequest request) {
        Exercise exercise = new Exercise(
                request.name().trim(),
                trimToNull(request.description()),
                request.instructions().trim(),
                request.primaryMuscleGroup(),
                request.difficulty(),
                request.equipmentCategory());
        return ExerciseResponse.from(exercises.save(exercise));
    }

    public ExerciseResponse update(UUID id, ExercisePatchRequest patch) {
        Exercise exercise = find(id);
        if (patch.version() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exercise version is required for updates.");
        }
        if (patch.version() != exercise.getVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This exercise changed. Reload it before saving.");
        }
        String name = patch.name() == null ? exercise.getName() : patch.name().trim();
        String instructions = patch.instructions() == null ? exercise.getInstructions() : patch.instructions().trim();
        String description = patch.description() == null ? exercise.getDescription() : trimToNull(patch.description());
        if (name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank.");
        }
        if (instructions.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Instructions must not be blank.");
        }
        exercise.update(
                name,
                description,
                instructions,
                patch.primaryMuscleGroup() == null ? exercise.getPrimaryMuscleGroup() : patch.primaryMuscleGroup(),
                patch.difficulty() == null ? exercise.getDifficulty() : patch.difficulty(),
                patch.equipmentCategory() == null ? exercise.getEquipmentCategory() : patch.equipmentCategory());
        try {
            return ExerciseResponse.from(exercises.saveAndFlush(exercise));
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This exercise changed. Reload it before saving.", exception);
        }
    }

    public void archive(UUID id) {
        Exercise exercise = find(id);
        exercise.archive();
        exercises.saveAndFlush(exercise);
    }

    private Exercise find(UUID id) {
        return exercises.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found."));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
