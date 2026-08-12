package uk.gov.moj.cpp.defence.persistence.entity;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "prosecution_advocate_access")
public class ProsecutionAdvocateAccess implements Serializable {

    @Id
    @Column(name = "id", unique = true, nullable = false)
    private UUID id;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "assignor_id", referencedColumnName = "id")
    private AssignmentUserDetails assignorDetails;

    @Column(name = "case_id", insertable = false, updatable = false)
    private UUID caseId;

    @Column(name = "assignor_organisation_id")
    private UUID assignorOrganisationId;

    @Column(name = "assignor_organisation_name")
    private String assignorOrganisationName;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "assignee_id", referencedColumnName = "id")
    private AssignmentUserDetails assigneeDetails;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumns({
            @JoinColumn(name = "assignee_organisation_id"),
            @JoinColumn(name = "case_id")
    })
    private ProsecutionOrganisationAccess prosecutionOrganisation;

    @Column(name = "assigned_date", nullable = false)
    private ZonedDateTime assignedDate;

    @Column(name = "assignment_expiry_date")
    private ZonedDateTime assignmentExpiryDate;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AssignmentUserDetails getAssignorDetails() {
        return assignorDetails;
    }

    public void setAssignorDetails(AssignmentUserDetails assignorDetails) {
        this.assignorDetails = assignorDetails;
    }

    public AssignmentUserDetails getAssigneeDetails() {
        return assigneeDetails;
    }

    public void setAssigneeDetails(AssignmentUserDetails assigneeDetails) {
        this.assigneeDetails = assigneeDetails;
    }

    public ZonedDateTime getAssignedDate() {
        return assignedDate;
    }

    public void setAssignedDate(ZonedDateTime assignedDate) {
        this.assignedDate = assignedDate;
    }

    public UUID getAssignorOrganisationId() {
        return assignorOrganisationId;
    }

    public void setAssignorOrganisationId(UUID assignorOrganisationId) {
        this.assignorOrganisationId = assignorOrganisationId;
    }

    public String getAssignorOrganisationName() {
        return assignorOrganisationName;
    }

    public void setAssignorOrganisationName(String assignorOrganisationName) {
        this.assignorOrganisationName = assignorOrganisationName;
    }

    public ProsecutionOrganisationAccess getProsecutionOrganisation() {
        return prosecutionOrganisation;
    }

    public void setProsecutionOrganisation(ProsecutionOrganisationAccess prosecutionOrganisation) {
        this.prosecutionOrganisation = prosecutionOrganisation;
    }

    public ZonedDateTime getAssignmentExpiryDate() {
        return assignmentExpiryDate;
    }

    public void setAssignmentExpiryDate(final ZonedDateTime assignmentExpiryDate) {
        this.assignmentExpiryDate = assignmentExpiryDate;
    }

    public void setCaseId(UUID caseId) {
        this.caseId = caseId;
    }

    public UUID getCaseId() {
        return caseId;
    }
}
