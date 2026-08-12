package uk.gov.moj.cpp.defence.persistence.entity;

import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "defence_grant_access")
public class DefenceGrantAccess {

    @Id
    @Column(name = "id", unique = true, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "defence_client_id", nullable = false)
    private DefenceClient defenceClient;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "grantee_id", referencedColumnName = "id")
    private DefenceUserDetails granteeDefenceUserDetails;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "grantor_id", referencedColumnName = "id")
    private DefenceUserDetails grantorDefenceUserDetails;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "grantee_organisation_id", referencedColumnName = "id")
    private OrganisationDetails granteeOrganisationDetails;

    @Column(name = "grantee_access_start_date")
    private ZonedDateTime startDate;

    @Column(name = "grantee_access_end_date")
    private ZonedDateTime endDate;

    @Column(name = "is_removed")
    private boolean removed;


    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public DefenceClient getDefenceClient() {
        return defenceClient;
    }

    public void setDefenceClient(final DefenceClient defenceClient) {
        this.defenceClient = defenceClient;
    }

    public DefenceUserDetails getGranteeDefenceUserDetails() {
        return granteeDefenceUserDetails;
    }

    public void setGranteeDefenceUserDetails(final DefenceUserDetails granteeDefenceUserDetails) {
        this.granteeDefenceUserDetails = granteeDefenceUserDetails;
    }

    public DefenceUserDetails getGrantorDefenceUserDetails() {
        return grantorDefenceUserDetails;
    }

    public void setGrantorDefenceUserDetails(final DefenceUserDetails grantorDefenceUserDetails) {
        this.grantorDefenceUserDetails = grantorDefenceUserDetails;
    }

    public OrganisationDetails getGranteeOrganisationDetails() {
        return granteeOrganisationDetails;
    }

    public void setGranteeOrganisationDetails(final OrganisationDetails granteeOrganisationDetails) {
        this.granteeOrganisationDetails = granteeOrganisationDetails;
    }

    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(final ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(final ZonedDateTime endDate) {
        this.endDate = endDate;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(final boolean removed) {
        this.removed = removed;
    }
}
