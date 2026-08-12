package uk.gov.moj.cpp.defence.persistence.entity;


import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assignment_user_details")
public class AssignmentUserDetails implements Serializable {
    private UUID id;
    private UUID userId;
    private String firstName;
    private String lastName;

    public AssignmentUserDetails() {
    }

    public AssignmentUserDetails(final UUID id, final UUID userId, final String firstName, final String lastName) {
        this.id = id;
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Id
    @Column(name = "id")
    public UUID getId() {
        return id;
    }

    @Column(name = "user_id")
    public UUID getUserId() {
        return userId;
    }

    @Column(name = "first_name")
    public String getFirstName() {
        return firstName;
    }

    @Column(name = "last_name")
    public String getLastName() {
        return lastName;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public void setUserId(final UUID userId) {
        this.userId = userId;
    }

    public void setFirstName(final String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(final String lastName) {
        this.lastName = lastName;
    }
}
