package uk.gov.moj.cpp.defence.persistence.entity;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "defendant_allocation_pleas")
public class DefendantAllocationPlea {

    @Id
    @Column(name = "offence_id")
    private UUID offenceId;

    @Column(name = "plea_date")
    private LocalDate pleaDate;

    @Column(name = "indicated_plea")
    private String indicatedPlea;
    @ManyToOne
    @JoinColumn(name = "defendant_allocation_id", nullable = false)
    private DefendantAllocation defendantAllocation;

    public DefendantAllocationPlea() {
    }

    public DefendantAllocationPlea(final UUID offenceId, final LocalDate pleaDate, final String indicatedPlea,final  DefendantAllocation defendantAllocation) {
        this.offenceId = offenceId;
        this.pleaDate = pleaDate;
        this.indicatedPlea = indicatedPlea;
        this.defendantAllocation=defendantAllocation;
    }

    public UUID getOffenceId() {
        return offenceId;
    }

    public void setOffenceId(final UUID offenceId) {
        this.offenceId = offenceId;
    }

    public LocalDate getPleaDate() {
        return pleaDate;
    }

    public void setPleaDate(final LocalDate pleaDate) {
        this.pleaDate = pleaDate;
    }

    public String getIndicatedPlea() {
        return indicatedPlea;
    }

    public void setIndicatedPlea(final String indicatedPlea) {
        this.indicatedPlea = indicatedPlea;
    }

    public DefendantAllocation getDefendantAllocation() {
        return defendantAllocation;
    }

    public void setDefendantAllocation(final DefendantAllocation defendantAllocation) {
        this.defendantAllocation = defendantAllocation;
    }
}
