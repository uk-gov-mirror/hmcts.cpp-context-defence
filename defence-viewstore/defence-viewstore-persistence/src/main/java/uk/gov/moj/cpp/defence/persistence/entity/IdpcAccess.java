package uk.gov.moj.cpp.defence.persistence.entity;

import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idpc_access_history")
public class IdpcAccess {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "defence_client_id")
    private UUID defenceClientId;

    @Column(name = "idpc_details_id")
    private UUID idpcDetailsId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "organisation_id")
    private UUID organisationId;

    @Column(name = "access_timestamp")
    private ZonedDateTime accessTimestamp;

    public IdpcAccess() {
    }

    public IdpcAccess(UUID id, UUID defenceClientId, UUID idpcDetailsId, UUID userId, UUID organisationId, ZonedDateTime accessTimestamp) {
        this.id = id;
        this.defenceClientId = defenceClientId;
        this.idpcDetailsId = idpcDetailsId;
        this.userId = userId;
        this.organisationId = organisationId;
        this.accessTimestamp = accessTimestamp;
    }

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getDefenceClientId() {
        return defenceClientId;
    }

    public void setDefenceClientId(UUID defenceClientId) {
        this.defenceClientId = defenceClientId;
    }

    public UUID getIdpcDetailsId() {
        return idpcDetailsId;
    }

    public void setIdpcDetailsId(UUID idpcDetailsId) {
        this.idpcDetailsId = idpcDetailsId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(UUID organisationId) {
        this.organisationId = organisationId;
    }

    public ZonedDateTime getAccessTimestamp() {
        return accessTimestamp;
    }

    public void setAccessTimestamp(ZonedDateTime accessTimestamp) {
        this.accessTimestamp = accessTimestamp;
    }
}
