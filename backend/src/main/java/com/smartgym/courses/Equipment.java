package com.smartgym.courses;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "equipment")
public class Equipment {
    @Id
    @Column(name = "equipment_id", nullable = false, updatable = false)
    private UUID equipmentId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "equipment_type", nullable = false, length = 60)
    private String equipmentType;

    @Column(nullable = false)
    private boolean available = true;

    @Column(name = "maintenance_required")
    private boolean maintenanceRequired = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "equipment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<MaintenanceTicket> maintenanceTickets = new ArrayList<>();

    protected Equipment() {
    }

    public Equipment(UUID equipmentId, String name, String description, String equipmentType,
                    boolean available, boolean maintenanceRequired) {
        this.equipmentId = equipmentId;
        this.name = name;
        this.description = description;
        this.equipmentType = equipmentType;
        this.available = available;
        this.maintenanceRequired = maintenanceRequired;
    }

    public static Equipment create(String name, String description, String equipmentType,
                                   boolean available, boolean maintenanceRequired) {
        return new Equipment(UUID.randomUUID(), name, description, equipmentType, available, maintenanceRequired);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.equipmentId == null) {
            this.equipmentId = UUID.randomUUID();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getEquipmentId() {
        return equipmentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEquipmentType() {
        return equipmentType;
    }

    public void setEquipmentType(String equipmentType) {
        this.equipmentType = equipmentType;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public boolean isMaintenanceRequired() {
        return maintenanceRequired;
    }

    public void setMaintenanceRequired(boolean maintenanceRequired) {
        this.maintenanceRequired = maintenanceRequired;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<MaintenanceTicket> getMaintenanceTickets() {
        return maintenanceTickets;
    }
}
