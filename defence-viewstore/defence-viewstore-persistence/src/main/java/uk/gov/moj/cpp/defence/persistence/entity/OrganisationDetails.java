package uk.gov.moj.cpp.defence.persistence.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "organisation_details")
public class OrganisationDetails {
    private UUID id;
    private UUID organisationId;
    private String organisationName;

    public OrganisationDetails() {
    }

    public OrganisationDetails(final UUID id,
                               final UUID organisationId,
                               final String organisationName) {
        this.id = id;
        this.organisationId = organisationId;
        this.organisationName = organisationName;
    }

    @Id
    @Column(name = "id")
    public UUID getId() {
        return id;
    }

    @Column(name = "organisation_id")
    public UUID getOrganisationId() {
        return organisationId;
    }

    @Column(name = "name")
    public String getOrganisationName() {
        return organisationName;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public void setOrganisationId(final UUID organisationId) {
        this.organisationId = organisationId;
    }

    public void setOrganisationName(final String organisationName) {
        this.organisationName = organisationName;
    }
}
