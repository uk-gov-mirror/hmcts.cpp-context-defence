package uk.gov.moj.cpp.defence.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProsecutionOrganisationCaseKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "assignee_organisation_id", nullable = false)
    private UUID assigneeOrganisationId;


    public ProsecutionOrganisationCaseKey() {
        //
    }

    public ProsecutionOrganisationCaseKey(final UUID caseId, final UUID assigneeOrganisationId) {
        this.caseId = caseId;
        this.assigneeOrganisationId = assigneeOrganisationId;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(final UUID caseId) {
        this.caseId = caseId;
    }

    public UUID getAssigneeOrganisationId() {
        return assigneeOrganisationId;
    }

    public void setAssigneeOrganisationId(UUID assigneeOrganisationId) {
        this.assigneeOrganisationId = assigneeOrganisationId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        return Objects.equals(this.caseId, ((ProsecutionOrganisationCaseKey) o).caseId)
                && Objects.equals(this.assigneeOrganisationId, ((ProsecutionOrganisationCaseKey) o).assigneeOrganisationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseId, assigneeOrganisationId);
    }

    @Override
    public String toString() {
        return "CaseDefendantHearingKey [caseId=" + caseId + ", assigneeOrganisationId=" + assigneeOrganisationId + "]";
    }
}
