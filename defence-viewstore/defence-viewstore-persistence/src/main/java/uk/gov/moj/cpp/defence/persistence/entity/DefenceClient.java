package uk.gov.moj.cpp.defence.persistence.entity;


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "defence_client")
@SuppressWarnings({"PMD.BeanMembersShouldSerialize","squid:S2384"})
public class DefenceClient {

    private UUID id;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private Set<Allegation> allegationList = new HashSet<>();
    @JsonIgnore
    private List<Instruction> instructionHistory = new ArrayList<>();
    private Boolean isVisible = true;
    private UUID idpcDetailsId;
    private String organisationName;
    private UUID caseId;
    private UUID defendantId;
    private UUID associatedOrganisation;
    private UUID lastAssociatedOrganisation;
    private Boolean lockedByRepOrder = false;

    public DefenceClient() {

    }

    public DefenceClient(final UUID id, final String firstName, final String lastName, final UUID caseId, final LocalDate dateOfBirth,
                         final UUID defendantId) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.caseId = caseId;
        this.defendantId = defendantId;
    }

    public DefenceClient(final UUID id, final String organisationName, final UUID caseId, final UUID defendantId) {
        this.id = id;
        this.organisationName = organisationName;
        this.caseId = caseId;
        this.defendantId = defendantId;
    }

    @Id
    @Column(name = "id")
    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    @Column(name = "first_name")
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(final String firstName) {
        this.firstName = firstName;
    }

    @Column(name = "last_name")
    public String getLastName() {
        return lastName;
    }

    public void setLastName(final String lastName) {
        this.lastName = lastName;
    }

    @Column(name = "date_of_birth")
    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(final LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "defence_client_id")
    public Set<Allegation> getAllegationList() {
        return allegationList;
    }

    public void setAllegationList(final Set<Allegation> allegationList) {
        this.allegationList.addAll(allegationList);
    }

    @OneToMany(mappedBy = "defenceClient", fetch = FetchType.EAGER, cascade = {CascadeType.ALL})
    public List<Instruction> getInstructionHistory() {
        return instructionHistory;
    }

    public void setInstructionHistory(final List<Instruction> instructionHistory) {
        this.instructionHistory = instructionHistory;
    }

    @Column(name = "is_visible", nullable = false)
    public Boolean getVisible() {
        return isVisible;
    }

    public void setVisible(final Boolean visible) {
        isVisible = visible;
    }

    @Column(name = "idpc_details_id")
    public UUID getIdpcDetailsId() {
        return idpcDetailsId;
    }

    public void setIdpcDetailsId(final UUID idpcDetailsId) {
        this.idpcDetailsId = idpcDetailsId;
    }

    @Column(name = "case_id")
    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(final UUID caseId) {
        this.caseId = caseId;
    }

    @Column(name = "defendant_id")
    public UUID getDefendantId() {
        return defendantId;
    }

    public void setDefendantId(final UUID defendantId) {
        this.defendantId = defendantId;
    }

    @Column(name = "associated_organisation_id")
    public UUID getAssociatedOrganisation() {
        return associatedOrganisation;
    }

    public void setAssociatedOrganisation(final UUID associatedOrganisation) {
        this.associatedOrganisation = associatedOrganisation;
    }

    @Column(name = "last_associated_organisation_id")
    public UUID getLastAssociatedOrganisation() {
        return lastAssociatedOrganisation;
    }

    public void setLastAssociatedOrganisation(final UUID lastAssociatedOrganisation) {
        this.lastAssociatedOrganisation = lastAssociatedOrganisation;
    }

    @Column(name = "organisation_name")
    public String getOrganisationName() {
        return organisationName;
    }

    public void setOrganisationName(final String organisationName) {
        this.organisationName = organisationName;
    }

    @Column(name = "is_locked_by_rep_order", nullable = false)
    public Boolean isLockedByRepOrder() {
        return lockedByRepOrder;
    }

    public void setLockedByRepOrder(final Boolean lockedByRepOrder) {
        this.lockedByRepOrder = lockedByRepOrder;
    }
}
