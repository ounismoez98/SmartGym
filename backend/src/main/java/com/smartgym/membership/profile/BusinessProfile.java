package com.smartgym.membership.profile;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "business_profiles")
public class BusinessProfile {
    @Id
    @Column(name = "keycloak_subject", nullable = false, length = 64)
    private String subject;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "member_profile_id", unique = true)
    private UUID memberProfileId;

    @Column(name = "employee_profile_id", unique = true)
    private UUID employeeProfileId;

    protected BusinessProfile() {}

    public BusinessProfile(String subject, String username, String displayName) {
        this.subject = subject;
        this.username = username;
        this.displayName = displayName;
    }

    public String getSubject() {
        return subject;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UUID getMemberProfileId() {
        return memberProfileId;
    }

    public UUID getEmployeeProfileId() {
        return employeeProfileId;
    }

    public void updateIdentity(String username, String displayName) {
        this.username = username;
        this.displayName = displayName;
    }

    public void ensureMemberProfile() {
        if (memberProfileId == null) {
            memberProfileId = UUID.randomUUID();
        }
    }

    public void ensureEmployeeProfile() {
        if (employeeProfileId == null) {
            employeeProfileId = UUID.randomUUID();
        }
    }
}
