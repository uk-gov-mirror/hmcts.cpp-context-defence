package uk.gov.moj.cpp.defence.persistence.entity;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "allegation")
public class Allegation {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "defence_client_id")
    private UUID defenceClientId;

    @Column(name = "offence_id")
    private UUID offenceId;

    private String legislation;

    private String title;

    @Column(name = "charge_date")
    private LocalDate chargeDate;

    public Allegation(final UUID id, final UUID defenceClientId, final UUID offenceId, final String legislation, final String title, LocalDate chargeDate) {
        this.id = id;
        this.defenceClientId = defenceClientId;
        this.offenceId = offenceId;
        this.legislation = legislation;
        this.title = title;
        this.chargeDate = chargeDate;
    }

    public Allegation() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getDefenceClientId() {
        return defenceClientId;
    }

    public String getLegislation() {
        return legislation;
    }

    public String getTitle() {
        return title;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public void setDefenceClientId(final UUID defenceClientId) {
        this.defenceClientId = defenceClientId;
    }

    public void setLegislation(final String legislation) {
        this.legislation = legislation;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public LocalDate getChargeDate() {
        return chargeDate;
    }

    public void setChargeDate(final LocalDate chargeDate) {
        this.chargeDate = chargeDate;
    }

    public UUID getOffenceId() { return offenceId; }

    public void setOffenceId(final UUID offenceId) { this.offenceId = offenceId; }
}
