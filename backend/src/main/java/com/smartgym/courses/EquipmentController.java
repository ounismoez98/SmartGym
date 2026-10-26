package com.smartgym.courses;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses/equipment")
public class EquipmentController {
    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public Page<Equipment> listEquipment(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "100") int size) {
        return equipmentService.listEquipment(PageRequest.of(page, size));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Equipment createEquipment(@RequestBody Equipment equipment) {
        return equipmentService.createEquipment(equipment);
    }

    @PutMapping("/{equipmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Equipment updateEquipment(@PathVariable UUID equipmentId,
                                     @RequestBody EquipmentService.EquipmentUpdateRequest request) {
        return equipmentService.updateEquipment(equipmentId, request);
    }

    @DeleteMapping("/{equipmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEquipment(@PathVariable UUID equipmentId) {
        equipmentService.deleteEquipment(equipmentId);
    }
}
