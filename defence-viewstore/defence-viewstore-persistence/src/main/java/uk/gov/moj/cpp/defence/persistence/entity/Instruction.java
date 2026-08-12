package uk.gov.moj.cpp.defence.persistence.entity;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "instruction")
public class Instruction {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "organisation_id")
    private UUID organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "defence_client_id")
    private DefenceClient defenceClient;

    @Column(name = "instruction_date")
    private LocalDate instructionDate;

    public Instruction(final UUID id, final UUID userId, final UUID organisationId, final DefenceClient defenceClient, final LocalDate instructionDate) {
        this.id = id;
        this.userId = userId;
        this.organisationId = organisationId;
        this.defenceClient = defenceClient;
        this.instructionDate = instructionDate;
    }

    public Instruction() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(final UUID userId) {
        this.userId = userId;
    }

    public UUID getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(final UUID organisationId) {
        this.organisationId = organisationId;
    }

    public DefenceClient getDefenceClient() {
        return defenceClient;
    }

    public void setDefenceClient(final DefenceClient defenceClient) {
        this.defenceClient = defenceClient;
    }

    public LocalDate getInstructionDate() {
        return instructionDate;
    }

    public void setInstructionDate(final LocalDate instructionDate) {
        this.instructionDate = instructionDate;
    }
}
