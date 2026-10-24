package com.smartgym.membership.api;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public class MemberUpdateRequest {
    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Email
    @Size(max = 254)
    private String email;

    @Size(max = 40)
    private String phone;

    @Past
    private LocalDate birthDate;

    private final Set<String> suppliedFields = new HashSet<>();

    @JsonSetter
    public void setFirstName(String firstName) {
        this.firstName = firstName;
        suppliedFields.add("firstName");
    }

    @JsonSetter
    public void setLastName(String lastName) {
        this.lastName = lastName;
        suppliedFields.add("lastName");
    }

    @JsonSetter
    public void setEmail(String email) {
        this.email = email;
        suppliedFields.add("email");
    }

    @JsonSetter
    public void setPhone(String phone) {
        this.phone = phone;
        suppliedFields.add("phone");
    }

    @JsonSetter
    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
        suppliedFields.add("birthDate");
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Set<String> getSuppliedFields() {
        return Set.copyOf(suppliedFields);
    }
}
