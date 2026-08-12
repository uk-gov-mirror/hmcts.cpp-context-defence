package uk.gov.moj.cpp.defence.persistence.entity;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "prosecution_organisation_access")
@SuppressWarnings({"PMD.BeanMembersShouldSerialize","squid:S2384"})
public class ProsecutionOrganisationAccess implements Serializable {

    @Id
    private ProsecutionOrganisationCaseKey id;

    @Column(name = "case_id", insertable = false, updatable = false)
    private UUID caseId;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "assignee_id", referencedColumnName = "id")
    private AssignmentUserDetails assigneeDetails;

    @Column(name = "assignee_organisation_name")
    private String assigneeOrganisationName;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "assignor_id", referencedColumnName = "id")
    private AssignmentUserDetails assignorDetails;

    @Column(name = "assignor_organisation_id")
    private UUID assignorOrganisationId;

    @Column(name = "assignor_organisation_name")
    private String assignorOrganisationName;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, mappedBy = "prosecutionOrganisation", orphanRemoval = true)
    private Set<ProsecutionAdvocateAccess> prosecutionAdvocatesWithAccess = new HashSet<>();

    @Column(name = "representation_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private RepresentationType representationType;

    @Column(name = "representing", nullable = false)
    private String representing;

    @Column(name = "assigned_date", nullable = false)
    private ZonedDateTime assignedDate;

    @Column (name = "assignment_expiry_date")
    private ZonedDateTime assignmentExpiryDate;


    public ProsecutionOrganisationCaseKey getId() {
        return id;
    }

    public void setId(ProsecutionOrganisationCaseKey id) {
        this.id = id;
    }

    public AssignmentUserDetails getAssignorDetails() {
        return assignorDetails;
    }

    public void setAssignorDetails(AssignmentUserDetails assignorDetails) {
        this.assignorDetails = assignorDetails;
    }

    public String getAssignorOrganisationName() {
        return assignorOrganisationName;
    }

    public void setAssignorOrganisationName(String assignorOrganisationName) {
        this.assignorOrganisationName = assignorOrganisationName;
    }

    public UUID getAssignorOrganisationId() {
        return assignorOrganisationId;
    }

    public void setAssignorOrganisationId(UUID assignorOrganisationId) {
        this.assignorOrganisationId = assignorOrganisationId;
    }

    public AssignmentUserDetails getAssigneeDetails() {
        return assigneeDetails;
    }

    public void setAssigneeDetails(AssignmentUserDetails assigneeDetails) {
        this.assigneeDetails = assigneeDetails;
    }

    public String getAssigneeOrganisationName() {
        return assigneeOrganisationName;
    }

    public void setAssigneeOrganisationName(String assigneeOrganisationName) {
        this.assigneeOrganisationName = assigneeOrganisationName;
    }

    public RepresentationType getRepresentationType() {
        return representationType;
    }

    public void setRepresentationType(RepresentationType representationType) {
        this.representationType = representationType;
    }

    public Set<ProsecutionAdvocateAccess> getProsecutionAdvocatesWithAccess() {
        return prosecutionAdvocatesWithAccess;
    }

    public String getRepresenting() {
        return representing;
    }

    public void setRepresenting(String representing) {
        this.representing = representing;
    }

    public ZonedDateTime getAssignedDate() {
        return assignedDate;
    }

    public void setAssignedDate(ZonedDateTime assignedDate) {
        this.assignedDate = assignedDate;
    }

    public ZonedDateTime getAssignmentExpiryDate() {
        return assignmentExpiryDate;
    }

    public void setAssignmentExpiryDate(final ZonedDateTime assignmentExpiryDate) {
        this.assignmentExpiryDate = assignmentExpiryDate;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(final UUID caseId) {
        this.caseId = caseId;
    }
}
