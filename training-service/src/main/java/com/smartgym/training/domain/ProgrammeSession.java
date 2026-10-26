package com.smartgym.training.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "programme_sessions")
public class ProgrammeSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_id", nullable = false)
    private TrainingProgramme programme;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "day_order", nullable = false)
    private int dayOrder;

    @Column(length = 1000)
    private String notes;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgrammeItem> items = new ArrayList<>();

    protected ProgrammeSession() {}

    public ProgrammeSession(String title, int dayOrder, String notes, List<ProgrammeItem> items) {
        this.title = title;
        this.dayOrder = dayOrder;
        this.notes = notes;
        items.forEach(item -> {
            item.attachTo(this);
            this.items.add(item);
        });
    }

    void attachTo(TrainingProgramme programme) {
        this.programme = programme;
    }

    public void update(String title, String notes) {
        this.title = title;
        this.notes = notes;
    }

    public void setDayOrder(int dayOrder) {
        this.dayOrder = dayOrder;
    }

    public void addItem(ProgrammeItem item) {
        item.attachTo(this);
        items.add(item);
    }

    public void addItems(List<ProgrammeItem> replacements) {
        replacements.forEach(this::addItem);
    }

    public void removeItem(ProgrammeItem item) {
        items.remove(item);
    }

    public void clearItems() {
        items.clear();
    }

    public UUID getId() { return id; }
    public TrainingProgramme getProgramme() { return programme; }
    public String getTitle() { return title; }
    public int getDayOrder() { return dayOrder; }
    public String getNotes() { return notes; }
    public List<ProgrammeItem> getItems() { return items; }
}
