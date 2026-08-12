package uk.gov.moj.cpp.defence.event.processor;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.core.courts.Defendant;
import uk.gov.justice.core.courts.Offence;
import uk.gov.justice.core.courts.Organisation;
import uk.gov.justice.core.courts.Person;
import uk.gov.justice.core.courts.PersonDefendant;
import uk.gov.justice.core.courts.ProsecutionCase;
import uk.gov.justice.core.courts.ProsecutionCaseIdentifier;
import uk.gov.justice.cps.defence.AddOrUpdateOffencePleasDocument;
import uk.gov.justice.cps.defence.Address;
import uk.gov.justice.cps.defence.DefendantOnOpa;
import uk.gov.justice.cps.defence.DefendantsOnCase;
import uk.gov.justice.cps.defence.OffencePleaDetails;
import uk.gov.justice.cps.defence.OffencePleasForDocument;
import uk.gov.justice.cps.defence.PleasAllocationDetails;
import uk.gov.justice.cps.defence.YesNoNa;
import uk.gov.justice.cps.defence.plea.PleaDefendantDetails;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.event.service.DefenceService;
import uk.gov.moj.cpp.defence.event.service.DocumentGeneratorService;
import uk.gov.moj.cpp.defence.event.service.ProgressionService;
import uk.gov.moj.cpp.defence.event.service.UserDetails;
import uk.gov.moj.cpp.defence.event.service.UsersGroupService;
import uk.gov.moj.cpp.defence.events.AllocationPleasAdded;
import uk.gov.moj.cpp.defence.events.AllocationPleasUpdated;
import uk.gov.moj.cpp.defence.events.OpaTaskRequested;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"squid:S1067", "squid:S3776", "squid:S1188", "squid:S3655", "squid:S2259"})
@ServiceComponent(EVENT_PROCESSOR)
public class PleaAllocationEventProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(PleaAllocationEventProcessor.class.getName());
    private static final String OPA_TEMPLATE = "plea_indications_on_case";
    private static final String CASE_ID = "caseId";
    private static final String ELECTING_CROWN_COURT_TRIAL_QUESTION_TEXT = "Does defendant elect Crown Court trial without court considering allocation?";
    private static final String SENTENCING_INDICATION_QUESTION_TEXT = "Would defendant ask for sentence indication?";
    private static final String CONSENT_TO_MAGS_COURT_QUESTION_TEXT = "Would defendant consent to magistrates' court trial if no sentence indication given or it doesn't alter not guilty plea?";
    private static final String OBJECTION_TO_CROWN_COURT_QUESTION_TEXT = "Would you object to court sending defendant to Crown Court trial if it considers they would have been highly likely to commit them to Crown Court for sentence?";
    private static final String REPRESENTATION_QUESTION_TEXT = "Do you consider this case is suitable to be dealt with in the Youth Court?";
    private static final String DISPUTE_OFFENCE_QUESTION_TEXT = "Does defendant dispute that the value of offences exceeds £5,000?";
    private static final String YOUTH_QUESTION_TEXT = "I acknowledge that the parent or legal guardian of the defendant is aware of the information provided, the plea submitted, and the court election";
    private static final String DEFENDANT_NAME_DOB_CONF_QUESTION_TEXT = "Are the defendant details correct?";
    private static final String ADDITIONAL_INFORMATION_QUESTION_TEXT = "Please provide any additional relevant information or representations (optional)";
    private static final String EMPTY = "";
    private static final UUID CASE_DOCUMENT_TYPE_ID = fromString("6b9df1fb-7bce-4e33-88dd-db91f75adeb8");
    private static final String PROGRESSION_ADD_COURT_DOCUMENT = "progression.add-court-document";
    private static final String THEFT_FROM_SHOP_AGREE_TEXT = "I agree, they are low-value shoplifting offences";
    private static final String THEFT_FROM_SHOP_DISAGREE_TEXT = "I disagree, they are not low-value shoplifting offences";
    private static final String THEFT_FROM_SHOP_NOT_APPLICABLE_TEXT = "Not applicable";

    @Inject
    private Sender sender;

    @Inject
    private UsersGroupService usersGroupService;

    @Inject
    private DocumentGeneratorService documentGeneratorService;

    @Inject
    private DefenceService defenceService;

    @Inject
    private ProgressionService progressionService;

    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Handles("defence.event.allocation-pleas-added")
    public void handleAllocationPleasAdded(final Envelope<AllocationPleasAdded> envelope) {

        final UserDetails userDetails = usersGroupService.getUserDetails(envelope);
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy 'submitted at' hh:mma");

        final AllocationPleasAdded allocationPleasAdded = envelope.payload();
        final PleasAllocationDetails pleasAllocation = allocationPleasAdded.getPleasAllocation();
        LOGGER.info("PROCESSOR - Received event '{}' with defendanId: {}", "defence.event.allocation-pleas-added", pleasAllocation.getDefendantId());

        final UUID defendantId = pleasAllocation.getDefendantId();
        final JsonArray pleasAndAllocationDetailsJson = defenceService.getPleaAndAllocationDetailsForACase(envelope.metadata(), pleasAllocation.getCaseId());
        final List<PleasAllocationDetails> pleasAndAllocationDetails = getPleaAndAllocationDetails(pleasAndAllocationDetailsJson);

        final UUID materialId = randomUUID();
        final ProsecutionCase prosecutionCase = getProsecutionCase(envelope.metadata(), pleasAllocation.getCaseId());
        final List<Defendant> defendants = ofNullable(prosecutionCase.getDefendants()).orElse(emptyList());
        final Defendant defendantOnOpa = defendants.stream().filter(def -> def.getId().equals(defendantId)).findFirst().get();

        final List<Defendant> defendantsOnCase = defendants.stream().filter(def -> !def.getId().equals(defendantId)).toList();

        final AddOrUpdateOffencePleasDocument document = getAddOrUpdateOffencePleasDocument(userDetails, formatter, pleasAllocation, pleasAndAllocationDetails, defendantOnOpa, defendantsOnCase);
        final JsonEnvelope requestEnvelopeWithDefendant = JsonEnvelope.envelopeFrom(
                metadataFrom(envelope.metadata()),
                createObjectBuilder()
                        .add("defendantId", pleasAllocation
                                .getDefendantId().toString())
        );

        final JsonObject documentPayload = objectToJsonObjectConverter.convert(document);
        final String fileName = "Allocation information for case " + pleasAllocation.getCaseUrn();
        documentGeneratorService.generateOpaDocument(requestEnvelopeWithDefendant, documentPayload, OPA_TEMPLATE, materialId, fileName);
        final JsonObject courtDocument = buildCourtDocument(pleasAllocation.getCaseId(), materialId, fileName);

        final JsonObject jsonObject = createObjectBuilder()
                .add("materialId", materialId.toString())
                .add("courtDocument", courtDocument)
                .build();

        final Metadata metadataWithActionName = metadataFrom(envelope.metadata()).withName(PROGRESSION_ADD_COURT_DOCUMENT).build();
        final JsonEnvelope requestEnvelope = JsonEnvelope.envelopeFrom(metadataWithActionName, jsonObject);
        sender.sendAsAdmin(Enveloper.envelop(jsonObject).withName(PROGRESSION_ADD_COURT_DOCUMENT).withMetadataFrom(requestEnvelope));

        LOGGER.info("public.defence.allocation-pleas-added for defendantOnOpa {} ", pleasAllocation.getDefendantId());
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("public.defence.allocation-pleas-added"),
                allocationPleasAdded));
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    private AddOrUpdateOffencePleasDocument getAddOrUpdateOffencePleasDocument(final UserDetails userDetails, final DateTimeFormatter formatter, final PleasAllocationDetails pleasAllocation, final List<PleasAllocationDetails> pleasAndAllocationDetails, final Defendant defendantOnOpa, final List<Defendant> defendantsOnCase) {
        return AddOrUpdateOffencePleasDocument.addOrUpdateOffencePleasDocument()
                .withCaseUrn(pleasAllocation.getCaseUrn())
                .withDefendantOnOpa(DefendantOnOpa.defendantOnOpa()
                        .withDefendantName(getDefendantName(defendantOnOpa))
                        .withDateOfBirth(getDefendantDoB(defendantOnOpa))
                        .withDefendantOrganisationName(getOrganisationName(defendantOnOpa))
                        .withAddress(getDefendantAddress(defendantOnOpa))
                        .withConsentToMagistratesCourtTrial(pleasAllocation.getConsentToMagistratesCourtTrial())
                        .withConsentToMagistratesCourtTrialQuestion(nonNull(pleasAllocation.getConsentToMagistratesCourtTrial()) ? CONSENT_TO_MAGS_COURT_QUESTION_TEXT : EMPTY)
                        .withDisputeOffenceValue(pleasAllocation.getDisputeOffenceValue())
                        .withDisputeOffenceValueDetails(nonNull(pleasAllocation.getDisputeOffenceValueDetails()) ? pleasAllocation.getDisputeOffenceValueDetails() : EMPTY)
                        .withDisputeOffenceQuestion(nonNull(pleasAllocation.getDisputeOffenceValue()) ? DISPUTE_OFFENCE_QUESTION_TEXT : EMPTY)
                        .withElectingCrownCourtTrial(pleasAllocation.getElectingCrownCourtTrial())
                        .withElectingCrownCourtTrialDetails(nonNull(pleasAllocation.getElectingCrownCourtTrialDetails()) ? pleasAllocation.getElectingCrownCourtTrialDetails() : EMPTY)
                        .withElectingCrownCourtTrialQuestion(nonNull(pleasAllocation.getElectingCrownCourtTrial()) ? ELECTING_CROWN_COURT_TRIAL_QUESTION_TEXT : EMPTY)
                        .withRepresentationsOnGraveCrime(pleasAllocation.getRepresentationsOnGraveCrime())
                        .withRepresentationsOnGraveCrimeDetails(nonNull(pleasAllocation.getRepresentationsOnGraveCrimeDetails()) ? pleasAllocation.getRepresentationsOnGraveCrimeDetails() : EMPTY)
                        .withRepresentationsOnGraveCrimeQuestion(nonNull(pleasAllocation.getRepresentationsOnGraveCrime()) ? REPRESENTATION_QUESTION_TEXT : EMPTY)
                        .withSentencingIndication(pleasAllocation.getSentencingIndication())
                        .withSentencingIndicationQuestion(nonNull(pleasAllocation.getSentencingIndication()) ? SENTENCING_INDICATION_QUESTION_TEXT : EMPTY)
                        .withCrownCourtObjection(pleasAllocation.getCrownCourtObjection())
                        .withCrownCourtObjectionQuestion(nonNull(pleasAllocation.getCrownCourtObjection()) ? OBJECTION_TO_CROWN_COURT_QUESTION_TEXT : EMPTY)
                        .withIsYouthOffence(nonNull(pleasAllocation.getRepresentationsOnGraveCrime()) ? Boolean.TRUE : Boolean.FALSE)
                        .withYouthAcknowledgement(nonNull(pleasAllocation.getYouthAcknowledgement()) ? Boolean.TRUE : Boolean.FALSE)
                        .withYouthAcknowledgementQuestion(YOUTH_QUESTION_TEXT)
                        .withOffencePleasForDocument(getOffencePleas(pleasAllocation.getOffencePleas(), defendantOnOpa))
                        .withDefendantNameDobConfirmation(pleasAllocation.getDefendantNameDobConfirmation())
                        .withDefendantNameDobConfirmationQuestion(DEFENDANT_NAME_DOB_CONF_QUESTION_TEXT)
                        .withDefendantCorrectedName(getDefendantCorrectedName(pleasAllocation.getDefendantDetails()))
                        .withDefendantCorrectedDob(getDefendantCorrectedDob(pleasAllocation.getDefendantDetails()))
                        .withOffenceType(getOffenceType(pleasAllocation))
                        .withAdditionalInformation(pleasAllocation.getAdditionalInformation())
                        .withAdditionalInformationQuestion(ADDITIONAL_INFORMATION_QUESTION_TEXT)
                        .withDefendantTurningEighteenDetails(pleasAllocation.getDefendantTurningEighteenDetails())
                        .withTheftFromShop(getUserSelectedTheftFromShopOption(pleasAllocation.getTheftFromShop()))
                        .withTheftFromShopDetails(nonNull(pleasAllocation.getTheftFromShopDetails()) ? pleasAllocation.getTheftFromShopDetails() : EMPTY)
                        .build())
                .withDefendantsOnCase(getDefendantsOnCase(defendantsOnCase, pleasAndAllocationDetails))
                .withSubmittedBy(userDetails.getFirstName() + " " + userDetails.getLastName())
                .withSubmittedDate(formatter.format(ZonedDateTime.now()))
                .build();
    }

    private String getUserSelectedTheftFromShopOption(final YesNoNa theftFromShop) {
        if (nonNull(theftFromShop)) {
            final String optionSelected = theftFromShop.toString();
            if ("Y".equals(optionSelected)) {
                return THEFT_FROM_SHOP_AGREE_TEXT;
            } else if ("N".equals(optionSelected)) {
                return THEFT_FROM_SHOP_DISAGREE_TEXT;
            } else if ("NA".equals(optionSelected)) {
                return THEFT_FROM_SHOP_NOT_APPLICABLE_TEXT;
            }
        }
        return EMPTY;
    }

    private String getDefendantCorrectedName(final PleaDefendantDetails defendantNameDobDetails) {
        if (nonNull(defendantNameDobDetails)) {
            if (isNull(defendantNameDobDetails.getOrganisationName())) {
                return Stream.of(defendantNameDobDetails.getFirstName(), defendantNameDobDetails.getMiddleName(), defendantNameDobDetails.getSurname())
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.joining(StringUtils.SPACE));
            } else {
                return defendantNameDobDetails.getOrganisationName();
            }
        }
        return EMPTY;
    }

    private String getDefendantCorrectedDob(final PleaDefendantDetails defendantNameDobDetails) {
        if (nonNull(defendantNameDobDetails) && nonNull(defendantNameDobDetails.getDob())
                && isNull(defendantNameDobDetails.getOrganisationName())) {
            final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
            return formatter.format(defendantNameDobDetails.getDob());
        }
        return EMPTY;
    }

    private String getOrganisationName(final Defendant defendant) {
        if (nonNull(defendant) && nonNull(defendant.getLegalEntityDefendant())
                && nonNull(defendant.getLegalEntityDefendant().getOrganisation())
                && nonNull(defendant.getLegalEntityDefendant().getOrganisation().getName())) {
            return defendant.getLegalEntityDefendant().getOrganisation().getName();
        } else {
            return EMPTY;
        }
    }

    private List<PleasAllocationDetails> getPleaAndAllocationDetails(final JsonArray pleasAndAllocationDetailsJson) {
        final List<PleasAllocationDetails> pleasAllocationDetails = new ArrayList<>();
        if (Objects.nonNull(pleasAndAllocationDetailsJson)) {
            for (int i = 0; i < pleasAndAllocationDetailsJson.size(); i++) {
                pleasAllocationDetails.add(jsonObjectToObjectConverter.convert(pleasAndAllocationDetailsJson.getJsonObject(i), PleasAllocationDetails.class));
            }
        }
        return pleasAllocationDetails;
    }

    private String getOffenceType(final PleasAllocationDetails pleasAllocation) {
        if (nonNull(pleasAllocation.getOffenceType())) {
            if (("adultEitherWay").equals(pleasAllocation.getOffenceType().toString())) {
                return "Adult either-way offences";
            } else if (("adultIndictableOnly").equals(pleasAllocation.getOffenceType().toString())) {
                return "Adult indictable only offences";
            } else if (("youthGraveCrime").equals(pleasAllocation.getOffenceType().toString())) {
                return "Youth grave crime";
            }
        }
        return EMPTY;
    }

    @Handles("defence.event.opa-task-requested")
    public void handleOpaTaskRequested(final Envelope<OpaTaskRequested> envelope) {
        final OpaTaskRequested opaTaskRequested = envelope.payload();
        LOGGER.info("public.defence.opa-task-requested for case {} ", opaTaskRequested.getCaseUrn());
        final UUID organisationId = usersGroupService.getOrganisationByType(envelope.metadata());
        final OpaTaskRequested publicOpaTaskRequested = OpaTaskRequested.opaTaskRequested().withValuesFrom(opaTaskRequested).withOrganisationId(organisationId).build();
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("public.defence.opa-task-requested"),
                publicOpaTaskRequested));
    }

    @Handles("defence.event.allocation-pleas-updated")
    public void handleAllocationUpdated(final Envelope<AllocationPleasUpdated> envelope) {

        final UserDetails userDetails = usersGroupService.getUserDetails(envelope);
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy 'submitted at' hh:mma");

        final AllocationPleasUpdated allocationPleasUpdated = envelope.payload();
        LOGGER.info("handleAllocationUpdated {} ", allocationPleasUpdated.getPleasAllocation().getAllocationId());

        final PleasAllocationDetails pleasAllocation = allocationPleasUpdated.getPleasAllocation();
        final UUID defendantId = pleasAllocation.getDefendantId();
        final JsonArray pleasAndAllocationDetailsJson = defenceService.getPleaAndAllocationDetailsForACase(envelope.metadata(), pleasAllocation.getCaseId());
        final List<PleasAllocationDetails> pleasAndAllocationDetails = getPleaAndAllocationDetails(pleasAndAllocationDetailsJson);
        final UUID materialId = randomUUID();
        final ProsecutionCase prosecutionCase = getProsecutionCase(envelope.metadata(), pleasAllocation.getCaseId());
        final Defendant defendantOnOpa = ofNullable(prosecutionCase.getDefendants()).orElse(emptyList()).stream()
                .filter(def -> def.getId().equals(defendantId))
                .findFirst().get();
        final List<Defendant> defendantsOnCase = ofNullable(prosecutionCase.getDefendants()).orElse(emptyList()).stream()
                .filter(def -> !def.getId().equals(defendantId))
                .toList();

        final AddOrUpdateOffencePleasDocument document = getAddOrUpdateOffencePleasDocument(userDetails, formatter, pleasAllocation, pleasAndAllocationDetails, defendantOnOpa, defendantsOnCase);

        final JsonEnvelope requestEnvelopeWithDefendant = JsonEnvelope.envelopeFrom(
                metadataFrom(envelope.metadata()),
                createObjectBuilder()
                        .add("defendantId", pleasAllocation
                                .getDefendantId().toString())
        );

        final JsonObject documentPayload = objectToJsonObjectConverter.convert(document);
        final String caseUrn = getCaseUrn(prosecutionCase.getProsecutionCaseIdentifier());
        final String fileName = "Allocation information for case " + caseUrn;

        documentGeneratorService.generateOpaDocument(requestEnvelopeWithDefendant, documentPayload, OPA_TEMPLATE, materialId, fileName);

        final JsonObject courtDocument = buildCourtDocument(pleasAllocation.getCaseId(), materialId, fileName);

        final JsonObject jsonObject = createObjectBuilder()
                .add("materialId", materialId.toString())
                .add("courtDocument", courtDocument)
                .build();

        final Metadata metadataWithActionName = metadataFrom(envelope.metadata()).withName(PROGRESSION_ADD_COURT_DOCUMENT).build();
        final JsonEnvelope requestEnvelope = JsonEnvelope.envelopeFrom(metadataWithActionName, jsonObject);
        sender.sendAsAdmin(Enveloper.envelop(jsonObject).withName(PROGRESSION_ADD_COURT_DOCUMENT).withMetadataFrom(requestEnvelope));

        LOGGER.info("public.defence.allocation-pleas-updated for defendant {} ", allocationPleasUpdated.getPleasAllocation().getDefendantId());
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("public.defence.allocation-pleas-updated"),
                allocationPleasUpdated));
    }

    private String getCaseUrn(final ProsecutionCaseIdentifier prosecutionCaseIdentifier) {
        if (nonNull(prosecutionCaseIdentifier.getCaseURN())) {
            return prosecutionCaseIdentifier.getCaseURN();
        }
        if (nonNull(prosecutionCaseIdentifier.getProsecutionAuthorityReference())) {
            return prosecutionCaseIdentifier.getProsecutionAuthorityReference();
        }
        return EMPTY;
    }

    private JsonObject buildCourtDocument(final UUID caseId, final UUID materialId, final String filename) {

        return createObjectBuilder()
                .add("courtDocumentId", randomUUID().toString())
                .add("documentCategory", createObjectBuilder()
                        .add("caseDocument", createObjectBuilder()
                                .add("prosecutionCaseId", caseId.toString())
                                .build())
                        .build())
                .add("documentTypeDescription", "Case Management")
                .add("documentTypeId", CASE_DOCUMENT_TYPE_ID.toString())
                .add("name", filename)
                .add("containsFinancialMeans", false)
                .add("mimeType", "application/pdf")
                .add("sendToCps", true)
                .add("notificationType", "opa-form-submitted")
                .add("materials", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("id", materialId.toString())
                                .add("receivedDateTime", ZonedDateTimes.toString(ZonedDateTime.now()))
                                .build())
                        .build())
                .build();
    }

    private String getDefendantName(final Defendant defendant) {
        final PersonDefendant personDefendant = ofNullable(defendant.getPersonDefendant()).orElse(null);
        final Person personDetails = nonNull(personDefendant) ? personDefendant.getPersonDetails() : null;
        if (nonNull(personDetails)) {
            return Stream.of(personDetails.getFirstName(), personDetails.getMiddleName(), personDetails.getLastName())
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.joining(" "));
        }
        return null;
    }

    private String getDefendantDoB(final Defendant defendant) {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        final Person personDetails = nonNull(defendant.getPersonDefendant()) ? defendant.getPersonDefendant().getPersonDetails() : null;
        if (Objects.isNull(personDetails) || Objects.isNull(personDetails.getDateOfBirth())) {
            return null;
        }
        return formatter.format(LocalDate.parse(personDetails.getDateOfBirth()));
    }

    private Address getDefendantAddress(Defendant defendant) {
        Address address = null;
        final Person personDetails = nonNull(defendant.getPersonDefendant()) ? defendant.getPersonDefendant().getPersonDetails() : null;
        final Organisation organisationDetails = nonNull(defendant.getLegalEntityDefendant()) ? defendant.getLegalEntityDefendant().getOrganisation() : null;
        if (nonNull(personDetails) && nonNull(personDetails.getAddress())) {
            address = Address.address()
                    .withAddress1(personDetails.getAddress().getAddress1())
                    .withAddress2(personDetails.getAddress().getAddress2())
                    .withAddress3(personDetails.getAddress().getAddress3())
                    .withAddress4(personDetails.getAddress().getAddress4())
                    .withAddress5(personDetails.getAddress().getAddress5())
                    .withWelshAddress1(personDetails.getAddress().getWelshAddress1())
                    .withWelshAddress2(personDetails.getAddress().getWelshAddress2())
                    .withWelshAddress3(personDetails.getAddress().getWelshAddress3())
                    .withWelshAddress4(personDetails.getAddress().getWelshAddress4())
                    .withWelshAddress5(personDetails.getAddress().getWelshAddress5())
                    .withPostcode(personDetails.getAddress().getPostcode())
                    .build();
        }
        if (nonNull(organisationDetails) && nonNull(organisationDetails.getAddress())) {
            address = Address.address()
                    .withAddress1(organisationDetails.getAddress().getAddress1())
                    .withAddress2(organisationDetails.getAddress().getAddress2())
                    .withAddress3(organisationDetails.getAddress().getAddress3())
                    .withAddress4(organisationDetails.getAddress().getAddress4())
                    .withAddress5(organisationDetails.getAddress().getAddress5())
                    .withWelshAddress1(organisationDetails.getAddress().getWelshAddress1())
                    .withWelshAddress2(organisationDetails.getAddress().getWelshAddress2())
                    .withWelshAddress3(organisationDetails.getAddress().getWelshAddress3())
                    .withWelshAddress4(organisationDetails.getAddress().getWelshAddress4())
                    .withWelshAddress5(organisationDetails.getAddress().getWelshAddress5())
                    .withPostcode(organisationDetails.getAddress().getPostcode())
                    .build();
        }
        return address;
    }

    private ProsecutionCase getProsecutionCase(final Metadata metadata, final UUID caseId) {
        final JsonEnvelope requestEnvelopeWithCaseId = JsonEnvelope.envelopeFrom(
                metadataFrom(metadata),
                createObjectBuilder()
                        .add(CASE_ID, caseId.toString())
        );
        final JsonObject prosecutionCaseJson = progressionService.getProsecutionCaseByCaseId(requestEnvelopeWithCaseId, caseId.toString());
        return jsonObjectToObjectConverter.convert(prosecutionCaseJson, ProsecutionCase.class);
    }

    private List<DefendantsOnCase> getDefendantsOnCase(final List<Defendant> defendantsOnCase, final List<PleasAllocationDetails> pleasAndAllocationDetails) {
        final List<DefendantsOnCase> defendantsOnCases = new ArrayList<>();
        defendantsOnCase.forEach(defendant -> {
            final PleasAllocationDetails pleasAllocationDetailsForADefendant = getPleasAndAllocationDetailsForADefendant(pleasAndAllocationDetails, defendant.getId());
            if (nonNull(pleasAllocationDetailsForADefendant)) {
                defendantsOnCases.add(DefendantsOnCase.defendantsOnCase()
                        .withDefendantName(getDefendantName(defendant))
                        .withDefendantOrganisationName(getOrganisationName(defendant))
                        .withAddress(getDefendantAddress(defendant))
                        .withOffencePleasForDocument(getOffencePleas(defendant.getOffences(), pleasAllocationDetailsForADefendant))
                        .withDateOfBirth(getDefendantDoB(defendant))
                        .withConsentToMagistratesCourtTrial(pleasAllocationDetailsForADefendant.getConsentToMagistratesCourtTrial())
                        .withConsentToMagistratesCourtTrialQuestion(nonNull(pleasAllocationDetailsForADefendant.getConsentToMagistratesCourtTrial()) ? CONSENT_TO_MAGS_COURT_QUESTION_TEXT : EMPTY)
                        .withDisputeOffenceValue(pleasAllocationDetailsForADefendant.getDisputeOffenceValue())
                        .withDisputeOffenceValueDetails(nonNull(pleasAllocationDetailsForADefendant.getDisputeOffenceValueDetails()) ? pleasAllocationDetailsForADefendant.getDisputeOffenceValueDetails() : EMPTY)
                        .withDisputeOffenceQuestion(nonNull(pleasAllocationDetailsForADefendant.getDisputeOffenceValue()) ? DISPUTE_OFFENCE_QUESTION_TEXT : EMPTY)
                        .withElectingCrownCourtTrial(pleasAllocationDetailsForADefendant.getElectingCrownCourtTrial())
                        .withElectingCrownCourtTrialDetails(nonNull(pleasAllocationDetailsForADefendant.getElectingCrownCourtTrialDetails()) ? pleasAllocationDetailsForADefendant.getElectingCrownCourtTrialDetails() : EMPTY)
                        .withElectingCrownCourtTrialQuestion(nonNull(pleasAllocationDetailsForADefendant.getElectingCrownCourtTrial()) ? ELECTING_CROWN_COURT_TRIAL_QUESTION_TEXT : EMPTY)
                        .withRepresentationsOnGraveCrime(pleasAllocationDetailsForADefendant.getRepresentationsOnGraveCrime())
                        .withRepresentationsOnGraveCrimeDetails(nonNull(pleasAllocationDetailsForADefendant.getRepresentationsOnGraveCrimeDetails()) ? pleasAllocationDetailsForADefendant.getRepresentationsOnGraveCrimeDetails() : EMPTY)
                        .withRepresentationsOnGraveCrimeQuestion(nonNull(pleasAllocationDetailsForADefendant.getRepresentationsOnGraveCrime()) ? REPRESENTATION_QUESTION_TEXT : EMPTY)
                        .withSentencingIndication(pleasAllocationDetailsForADefendant.getSentencingIndication())
                        .withSentencingIndicationQuestion(nonNull(pleasAllocationDetailsForADefendant.getSentencingIndication()) ? SENTENCING_INDICATION_QUESTION_TEXT : EMPTY)
                        .withCrownCourtObjection(pleasAllocationDetailsForADefendant.getCrownCourtObjection())
                        .withCrownCourtObjectionQuestion(nonNull(pleasAllocationDetailsForADefendant.getCrownCourtObjection()) ? OBJECTION_TO_CROWN_COURT_QUESTION_TEXT : EMPTY)
                        .withIsYouthOffence(nonNull(pleasAllocationDetailsForADefendant.getRepresentationsOnGraveCrime()) ? Boolean.TRUE : Boolean.FALSE)
                        .withYouthAcknowledgement(nonNull(pleasAllocationDetailsForADefendant.getYouthAcknowledgement()) ? Boolean.TRUE : Boolean.FALSE)
                        .withYouthAcknowledgementQuestion(YOUTH_QUESTION_TEXT)
                        .withDefendantNameDobConfirmation(pleasAllocationDetailsForADefendant.getDefendantNameDobConfirmation())
                        .withDefendantNameDobConfirmationQuestion(DEFENDANT_NAME_DOB_CONF_QUESTION_TEXT)
                        .withDefendantCorrectedName(getDefendantCorrectedName(pleasAllocationDetailsForADefendant.getDefendantDetails()))
                        .withDefendantCorrectedDob(getDefendantCorrectedDob(pleasAllocationDetailsForADefendant.getDefendantDetails()))
                        .withOffenceType(getOffenceType(pleasAllocationDetailsForADefendant))
                        .withAdditionalInformation(pleasAllocationDetailsForADefendant.getAdditionalInformation())
                        .withAdditionalInformationQuestion(ADDITIONAL_INFORMATION_QUESTION_TEXT)
                        .withDefendantTurningEighteenDetails(pleasAllocationDetailsForADefendant.getDefendantTurningEighteenDetails())
                        .withTheftFromShop(getUserSelectedTheftFromShopOption(pleasAllocationDetailsForADefendant.getTheftFromShop()))
                        .withTheftFromShopDetails(nonNull(pleasAllocationDetailsForADefendant.getTheftFromShopDetails()) ? pleasAllocationDetailsForADefendant.getTheftFromShopDetails() : EMPTY)
                        .build());
            } else {
                defendantsOnCases.add(DefendantsOnCase.defendantsOnCase()
                        .withDefendantName(getDefendantName(defendant))
                        .withDateOfBirth(getDefendantDoB(defendant))
                        .withOffencePleasForDocument(getOffencePleas(defendant.getOffences()))
                        .build());
            }


        });
        return CollectionUtils.isNotEmpty(defendantsOnCases) ? defendantsOnCases : null;
    }

    private PleasAllocationDetails getPleasAndAllocationDetailsForADefendant(final List<PleasAllocationDetails> pleasAndAllocationDetails, final UUID defendantId) {
        return pleasAndAllocationDetails.stream()
                .filter(pleasAllocationDetails -> pleasAllocationDetails.getDefendantId().equals(defendantId))
                .findFirst()
                .orElse(null);
    }

    private List<OffencePleasForDocument> getOffencePleas(final List<Offence> offences) {
        return offences.stream()
                .map(offence -> OffencePleasForDocument.offencePleasForDocument()
                        .withOffenceId(offence.getId())
                        .withOffenceTitle(offence.getOffenceTitle())
                        .withOffenceDescription(offence.getWording())
                        .build())
                .toList();
    }

    private List<OffencePleasForDocument> getOffencePleas(final List<Offence> offences, final PleasAllocationDetails pleasAllocationDetailsForADefendant) {
        return offences.stream()
                .map(offence -> OffencePleasForDocument.offencePleasForDocument()
                        .withOffenceId(offence.getId())
                        .withOffenceTitle(offence.getOffenceTitle())
                        .withOffenceDescription(offence.getWording())
                        .withIndicatedPlea(getIndicatedPleaFromAllocationDetails(offence.getId(), pleasAllocationDetailsForADefendant.getOffencePleas()))
                        .build())
                .toList();
    }

    private String getIndicatedPleaFromAllocationDetails(UUID offenceId, final List<OffencePleaDetails> offencePleas) {
        return offencePleas.stream()
                .filter(offencePleaDetails -> offencePleaDetails.getOffenceId().equals(offenceId))
                .findFirst()
                .map(OffencePleaDetails::getIndicatedPlea)
                .orElse(null);
    }

    private List<OffencePleasForDocument> getOffencePleas(final List<OffencePleaDetails> offencePleas, final Defendant defendant) {
        return offencePleas.stream()
                .map(offencePleaDetails -> OffencePleasForDocument.offencePleasForDocument()
                        .withOffenceId(offencePleaDetails.getOffenceId())
                        .withIndicatedPlea(offencePleaDetails.getIndicatedPlea())
                        .withOffenceTitle(getOffenceTitle(offencePleaDetails.getOffenceId(), defendant.getOffences()))
                        .withOffenceDescription(getOffenceDescription(offencePleaDetails.getOffenceId(), defendant.getOffences()))
                        .build())
                .toList();
    }

    private String getOffenceTitle(final UUID offenceId, final List<Offence> offences) {
        return offences.stream()
                .filter(offence -> offence.getId().equals(offenceId))
                .map(Offence::getOffenceTitle)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Offence not found"));
    }

    private String getOffenceDescription(final UUID offenceId, final List<Offence> offences) {
        return offences.stream()
                .filter(offence -> offence.getId().equals(offenceId))
                .map(Offence::getWording)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Offence not found"));
    }

}
