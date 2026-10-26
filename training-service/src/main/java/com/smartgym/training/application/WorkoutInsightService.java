package com.smartgym.training.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.smartgym.training.ai.ProgrammeTextPolicy;
import com.smartgym.training.ai.WorkoutInsightContext;
import com.smartgym.training.ai.WorkoutInsightGenerator;
import com.smartgym.training.dto.WorkoutInsightRequest;
import com.smartgym.training.dto.WorkoutInsightResponse;
import com.smartgym.training.domain.WorkoutLog;
import com.smartgym.training.domain.WorkoutSet;
import com.smartgym.training.integration.MembershipClient;
import com.smartgym.training.repository.WorkoutLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkoutInsightService {
    private static final int PERIOD_DAYS = 28;
    private final WorkoutLogRepository logs;
    private final MembershipClient membership;
    private final WorkoutInsightGenerator generator;

    public WorkoutInsightService(
            WorkoutLogRepository logs,
            MembershipClient membership,
            WorkoutInsightGenerator generator) {
        this.logs = logs;
        this.membership = membership;
        this.generator = generator;
    }

    public WorkoutInsightResponse answer(Jwt jwt, WorkoutInsightRequest request) {
        UUID memberId = memberId(jwt);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime from = now.minusDays(PERIOD_DAYS);
        List<WorkoutLog> recentLogs =
                logs.findAllByMemberIdAndPerformedAtGreaterThanEqualOrderByPerformedAtDesc(memberId, from);

        long setCount = 0;
        long repetitions = 0;
        BigDecimal loadedVolume = BigDecimal.ZERO;
        for (WorkoutLog log : recentLogs) {
            for (WorkoutSet set : log.getSets()) {
                setCount++;
                if (set.getRepetitions() != null) {
                    repetitions += set.getRepetitions();
                    if (set.getLoadKg() != null) {
                        loadedVolume = loadedVolume.add(
                                set.getLoadKg().multiply(BigDecimal.valueOf(set.getRepetitions())));
                    }
                }
            }
        }

        WorkoutInsightContext facts = new WorkoutInsightContext(
                from.toLocalDate(),
                now.toLocalDate(),
                recentLogs.size(),
                setCount,
                repetitions,
                loadedVolume);
        String question = request.question().trim();
        String answer = ProgrammeTextPolicy.isMedicalRequest(question)
                ? "I cannot assess or advise on pain, injury, rehabilitation, or treatment. Stop exercising if you feel unwell and consult a qualified professional. If this is an immediate emergency, contact local emergency services."
                : generator.answer(question, facts);
        String encouragement = recentLogs.isEmpty()
                ? "Every log is a useful starting point. Begin at a pace that feels comfortable."
                : "You recorded " + recentLogs.size()
                        + (recentLogs.size() == 1 ? " workout" : " workouts")
                        + " in this period. Keep your progress steady and comfortable.";
        return new WorkoutInsightResponse(
                facts.periodStart(),
                facts.periodEnd(),
                facts.completedWorkouts(),
                facts.recordedSets(),
                facts.repetitions(),
                facts.loadedVolumeKgRepetitions(),
                answer,
                encouragement);
    }

    private UUID memberId(Jwt jwt) {
        MembershipClient.Identity identity = membership.identity(jwt.getTokenValue());
        if (identity == null
                || !jwt.getSubject().equals(identity.subject())
                || !identity.roles().contains("MEMBER")
                || identity.memberProfileId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A verified member profile is required.");
        }
        return identity.memberProfileId();
    }
}
