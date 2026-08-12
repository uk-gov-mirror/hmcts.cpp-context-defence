package uk.gov.moj.cpp.defence.persistence.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "defendant_allocation")
public class DefendantAllocation {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "defendant_id")
    private UUID defendantId;

    @Column(name = "crown_court_objection")
    private String crownCourtObjection;

    @Column(name = "consent_to_magistrate_trail")
    private String consentToMagistrateTrail;

    @Column(name = "elect_crown_court_trail")
    private Boolean electCrownCourtTrail;

    @Column(name = "elect_crown_court_trail_details")
    private String electCrownCourtTrailDetails;

    @Column(name = "sentencing_indication_requested")
    private String sentencingIndicationRequested;


    @Column(name = "offence_value_disputed")
    private Boolean offenceValueDisputed;

    @Column(name = "offence_value_representations")
    private String offenceValueRepresentations;

    @Column(name = "acknowledgement")
    private Boolean acknowledgement;

    @Column(name = "guardian_consent_provided")
    private Boolean guardianConsentProvided;

    @Column(name = "representations_on_grave_crime")
    private Boolean representationsOnGraveCrime;

    @Column(name = "representations_on_grave_crime_details")
    private String representationsOnGraveCrimeDetails;

    @Column(name = "defendant_name_dob_confirmation")
    private Boolean defendantNameDobConfirmation;

    @Column(name = "def_first_name")
    private String defendantFirstName;

    @Column(name = "def_middle_Name")
    private String defendantMiddleName;

    @Column(name = "def_surname")
    private String defendantSurname;

    @Column(name = "def_dob")
    private LocalDate defendantDateOfBirth;

    @Column(name = "def_org_name")
    private String defendantOrganisationName;

    @Column(name = "offence_type")
    private String offenceType;

    @Column(name = "additional_information")
    private String additionalInformation;

    @Column(name = "def_turning_eighteen_details")
    private String defendantTurningEighteenDetails;

    @Column(name = "theft_from_shop")
    private String theftFromShop;

    @Column(name = "theft_from_shop_details")
    private String theftFromShopDetails;


    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, mappedBy = "defendantAllocation", orphanRemoval = true)
    private List<DefendantAllocationPlea> defendantAllocationPleas = new ArrayList<>();

    public DefendantAllocation() {
        // empty con
    }

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getDefendantId() {
        return defendantId;
    }

    public void setDefendantId(final UUID defendantId) {
        this.defendantId = defendantId;
    }

    public String getCrownCourtObjection() {
        return crownCourtObjection;
    }

    public void setCrownCourtObjection(final String crownCourtObjection) {
        this.crownCourtObjection = crownCourtObjection;
    }

    public String getConsentToMagistrateTrail() {
        return consentToMagistrateTrail;
    }

    public void setConsentToMagistrateTrail(final String consentToMagistrateTrail) {
        this.consentToMagistrateTrail = consentToMagistrateTrail;
    }

    public Boolean getElectCrownCourtTrail() {
        return electCrownCourtTrail;
    }

    public void setElectCrownCourtTrail(final Boolean electCrownCourtTrail) {
        this.electCrownCourtTrail = electCrownCourtTrail;
    }

    public String getElectCrownCourtTrailDetails() {
        return electCrownCourtTrailDetails;
    }

    public void setElectCrownCourtTrailDetails(final String electCrownCourtTrailDetails) {
        this.electCrownCourtTrailDetails = electCrownCourtTrailDetails;
    }

    public String getSentencingIndicationRequested() {
        return sentencingIndicationRequested;
    }

    public void setSentencingIndicationRequested(final String sentencingIndicationRequested) {
        this.sentencingIndicationRequested = sentencingIndicationRequested;
    }

    public Boolean getOffenceValueDisputed() {
        return offenceValueDisputed;
    }

    public void setOffenceValueDisputed(final Boolean offenceValueDisputed) {
        this.offenceValueDisputed = offenceValueDisputed;
    }

    public String getOffenceValueRepresentations() {
        return offenceValueRepresentations;
    }

    public void setOffenceValueRepresentations(final String offenceValueRepresentations) {
        this.offenceValueRepresentations = offenceValueRepresentations;
    }

    public Boolean getAcknowledgement() {
        return acknowledgement;
    }

    public void setAcknowledgement(final Boolean acknowledgement) {
        this.acknowledgement = acknowledgement;
    }

    public Boolean getGuardianConsentProvided() {
        return guardianConsentProvided;
    }

    public void setGuardianConsentProvided(final Boolean guardianConsentProvided) {
        this.guardianConsentProvided = guardianConsentProvided;
    }

    public List<DefendantAllocationPlea> getDefendantAllocationPleas() {
        return defendantAllocationPleas;
    }

    public void setDefendantAllocationPleas(final List<DefendantAllocationPlea> defendantAllocationPleas) {
        this.defendantAllocationPleas = defendantAllocationPleas;
    }

    public Boolean getRepresentationsOnGraveCrime() {
        return representationsOnGraveCrime;
    }

    public void setRepresentationsOnGraveCrime(final Boolean representationsOnGraveCrime) {
        this.representationsOnGraveCrime = representationsOnGraveCrime;
    }

    public String getRepresentationsOnGraveCrimeDetails() {
        return representationsOnGraveCrimeDetails;
    }

    public void setRepresentationsOnGraveCrimeDetails(final String representationsOnGraveCrimeDetails) {
        this.representationsOnGraveCrimeDetails = representationsOnGraveCrimeDetails;
    }

    public Boolean getDefendantNameDobConfirmation() {
        return defendantNameDobConfirmation;
    }

    public void setDefendantNameDobConfirmation(final Boolean defendantNameDobConfirmation) {
        this.defendantNameDobConfirmation = defendantNameDobConfirmation;
    }

    public String getDefendantFirstName() {
        return defendantFirstName;
    }

    public void setDefendantFirstName(final String defendantFirstName) {
        this.defendantFirstName = defendantFirstName;
    }

    public String getDefendantMiddleName() {
        return defendantMiddleName;
    }

    public void setDefendantMiddleName(final String defendantMiddleName) {
        this.defendantMiddleName = defendantMiddleName;
    }

    public String getDefendantSurname() {
        return defendantSurname;
    }

    public void setDefendantSurname(final String defendantSurname) {
        this.defendantSurname = defendantSurname;
    }

    public LocalDate getDefendantDateOfBirth() {
        return defendantDateOfBirth;
    }

    public void setDefendantDateOfBirth(final LocalDate defendantDateOfBirth) {
        this.defendantDateOfBirth = defendantDateOfBirth;
    }

    public String getOffenceType() {
        return offenceType;
    }

    public void setOffenceType(final String offenceType) {
        this.offenceType = offenceType;
    }

    public String getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(final String additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public String getDefendantTurningEighteenDetails() {
        return defendantTurningEighteenDetails;
    }

    public void setDefendantTurningEighteenDetails(final String defendantTurningEighteenDetails) {
        this.defendantTurningEighteenDetails = defendantTurningEighteenDetails;
    }

    public String getTheftFromShop() {
        return theftFromShop;
    }

    public void setTheftFromShop(final String theftFromShop) {
        this.theftFromShop = theftFromShop;
    }

    public String getTheftFromShopDetails() {
        return theftFromShopDetails;
    }

    public void setTheftFromShopDetails(final String theftFromShopDetails) {
        this.theftFromShopDetails = theftFromShopDetails;
    }

    public String getDefendantOrganisationName() {
        return defendantOrganisationName;
    }

    public void setDefendantOrganisationName(final String defendantOrganisationName) {
        this.defendantOrganisationName = defendantOrganisationName;
    }
}
