package uk.gov.moj.cpp.defence.query.view;

import static java.time.LocalDate.parse;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.justice.cps.defence.CaseDefendantsOrganisations.caseDefendantsOrganisations;
import static uk.gov.justice.services.core.annotation.Component.QUERY_API;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.moj.cpp.defence.CaseDefendantsWithOrganisation.caseDefendantsWithOrganisation;

import uk.gov.justice.cps.defence.AllegationDetail;
import uk.gov.justice.cps.defence.Allegations;
import uk.gov.justice.cps.defence.CaseDefendantsOrganisations;
import uk.gov.justice.cps.defence.DefenceClientId;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.CaseDefendantsWithOrganisation;
import uk.gov.moj.cpp.defence.IdpcMetadata;
import uk.gov.moj.cpp.defence.common.Defendant;
import uk.gov.moj.cpp.defence.persistence.DefenceCaseRepository;
import uk.gov.moj.cpp.defence.persistence.DefenceClientRepository;
import uk.gov.moj.cpp.defence.persistence.IdpcAccessHistoryRepository;
import uk.gov.moj.cpp.defence.persistence.IdpcDetailsRepository;
import uk.gov.moj.cpp.defence.persistence.InstructionRepository;
import uk.gov.moj.cpp.defence.persistence.entity.Allegation;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceCase;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.persistence.entity.IdpcDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

public class DefenceQueryService {

    private static final String DEFAULT_FILE_NAME = "IDPC.pdf";

    @Inject
    private DefenceClientRepository defenceClientRepository;

    @Inject
    private IdpcAccessHistoryRepository idpcAccessHistoryRepository;

    @Inject
    private InstructionRepository instructionRepository;

    @Inject
    private IdpcDetailsRepository idpcDetailsRepository;

    @Inject
    private DefenceCaseRepository defenceCaseRepository;

    @Inject
    @ServiceComponent(QUERY_API)
    private Requester requester;

    public DefenceClientIdpcAccessOrganisations getClientAndIDPCAccessOrganisations(final String firstName, final String lastName,
                                                                                    final String dob, final String urn, final Optional<Boolean> isCivil) {
        requireNonNull(firstName, "firstName must not be null");
        requireNonNull(lastName, "lastName must not be null");
        requireNonNull(dob, "dob must not be null");

        if (nonNull(urn)) {
            return getDefenceClientIdpcAccessOrganisations(findDefenceClientByCriteria(firstName, lastName, dob, urn.toUpperCase(), isCivil));
        } else{
            return getDefenceClientIdpcAccessOrganisations(findDefenceClientByCriteria(firstName, lastName, dob, isCivil));
        }
    }

    public List<UUID> getCasesAssociatedWithDefenceClientByPersonDefendant(final String firstName, final String lastName, final String dob, final Optional<Boolean> isCivil) {
        return getCasesAssociatedWithDefenceClientByPersonDefendant(firstName, lastName, dob, isCivil, Optional.empty());
    }

    public List<UUID> getCasesAssociatedWithDefenceClientByPersonDefendant(final String firstName, final String lastName, final String dob) {
        return defenceClientRepository.findCasesAssociatedWithDefenceClientByPersonDefendant(firstName, lastName, parse(dob));
    }

    public List<UUID> getCasesAssociatedWithDefenceClientByPersonDefendant(final String firstName, final String lastName, final String dob, final Optional<Boolean> isCivil, final Optional<Boolean> isGroupMemberOpt) {
        if (isCivil.isPresent()) {
            final boolean isGroupMember = isGroupMemberOpt.orElse(false);
            if (EMPTY.equalsIgnoreCase(dob)) {
                return defenceClientRepository.findCasesAssociatedWithDefenceClientByPersonDefendantWithoutDob(firstName, lastName, isCivil.get(), isGroupMember);
            } else {
                return defenceClientRepository.findCasesAssociatedWithDefenceClientByPersonDefendant(firstName, lastName, parse(dob), isCivil.get(), isGroupMember);
            }
        } else {
            return getCasesAssociatedWithDefenceClientByPersonDefendant(firstName, lastName, dob);
        }
    }

    public List<UUID> getPersonDefendant(final String firstName, final String lastName, final String dateOfBirth){
        return defenceClientRepository.getPersonDefendant(firstName, lastName, parse(dateOfBirth));
    }

    public List<UUID> getPersonDefendant(final String firstName, final String lastName, final String dateOfBirth, final Optional<Boolean> isCivil){
        return getPersonDefendant(firstName, lastName, dateOfBirth, isCivil, Optional.empty());
    }

    public List<UUID> getPersonDefendant(final String firstName, final String lastName, final String dateOfBirth, final Optional<Boolean> isCivil, final Optional<Boolean> isGroupMemberOpt) {
        if (isCivil.isPresent()) {
            final boolean isGroupMember = isGroupMemberOpt.orElse(false);
            if (EMPTY.equalsIgnoreCase(dateOfBirth)) {
                return defenceClientRepository.getPersonDefendantWithOutDob(firstName, lastName, isCivil.get(), isGroupMember);
            } else {
                return defenceClientRepository.getPersonDefendant(firstName, lastName, parse(dateOfBirth), isCivil.get(), isGroupMember);
            }
        } else {
            return getPersonDefendant(firstName, lastName, dateOfBirth);
        }
    }

    public List<UUID> getOrganisationDefendant(final String organisationName){
        return defenceClientRepository.getOrganisationDefendant(organisationName);
    }

    public List<UUID> getOrganisationDefendant(final String organisationName, final Optional<Boolean> isCivil){
        return getOrganisationDefendant(organisationName, isCivil, Optional.empty());
    }

    public List<UUID> getOrganisationDefendant(final String organisationName, final Optional<Boolean> isCivil, final Optional<Boolean> isGroupMemberOpt){
        if(isCivil.isPresent()){
            final boolean isGroupMember = isGroupMemberOpt.orElse(false);
            return defenceClientRepository.getOrganisationDefendant(organisationName, isCivil.get(), isGroupMember);
        }else{
            return getOrganisationDefendant(organisationName);
        }
    }

    public UUID getCaseId(final String urn) {
        final DefenceCase defenceCase = defenceCaseRepository.findOptionalByUrn(urn.toUpperCase());
        if (isNull(defenceCase)) {
            return null;
        }
        return defenceCase.getId();
    }

    private DefenceClientIdpcAccessOrganisations getDefenceClientIdpcAccessOrganisations(final List<DefenceClient> defenceClientList) {
        final DefenceClient defenceClient = defenceClientList.isEmpty() ? null : defenceClientList.get(0);

        if (defenceClient != null) {

            final List<UUID> idsOfOrganisationAccessingIdpc = idpcAccessHistoryRepository.findOrderedDistinctOrgIdsOfIdpcAccessForDefenceClient(defenceClient.getId());

            final DefenceCase defenceCase = defenceCaseRepository.findBy(defenceClient.getCaseId());

            final List<OrderedOrganisationDetailsVO> orgList = IntStream
                    .range(0, idsOfOrganisationAccessingIdpc.size())
                    .mapToObj(i -> new OrderedOrganisationDetailsVO(i + 1, idsOfOrganisationAccessingIdpc.get(i), null))
                    .collect(toList());

            final List<DefenceClientInstructionHistoryVO> instructionHistory =
                    defenceClient.getInstructionHistory().stream().map(ih -> new DefenceClientInstructionHistoryVO(ih.getId(), ih.getUserId(), ih.getOrganisationId(), ih.getInstructionDate())).collect(toList());


            return new DefenceClientIdpcAccessOrganisations(
                    defenceClient.getId(),
                    defenceClient.getCaseId(),
                    defenceClient.getDefendantId(),
                    getAssociatedOrganisation(defenceClient.getAssociatedOrganisation()),
                    getAssociatedOrganisation(defenceClient.getLastAssociatedOrganisation()),
                    orgList,
                    instructionHistory,
                    defenceClient.isLockedByRepOrder(),
                    defenceCase.getProsecutionAuthorityCode(),
                    defenceCase.getUrn());
        }
        return null;
    }

    public DefenceClientIdpcAccessOrganisations getClientAndIDPCAccessOrganisations(final String organisationName, final String urn, final Optional<Boolean> isCivil) {

        requireNonNull(organisationName, "organisationName must not be null");

        List<DefenceClient> defenceClientList;
        if (nonNull(urn)) {
            defenceClientList = findDefenceClientByCriteria(organisationName, urn.toUpperCase(), isCivil);
        } else{
            defenceClientList = findDefenceClientByCriteria(organisationName, isCivil);
        }
        return getDefenceClientIdpcAccessOrganisations(defenceClientList);
    }

    public List<UUID> getCasesAssociatedWithDefenceClientByOrganisationDefendant(final String organisationName) {
        return defenceClientRepository.findCasesAssociatedWithDefenceClientByOrganisationDefendant(organisationName);
    }

    public List<UUID> getCasesAssociatedWithDefenceClientByOrganisationDefendant(final String organisationName, final Optional<Boolean> isCivil) {
        return getCasesAssociatedWithDefenceClientByOrganisationDefendant(organisationName, isCivil, Optional.empty());
    }

    public List<UUID> getCasesAssociatedWithDefenceClientByOrganisationDefendant(final String organisationName, final Optional<Boolean> isCivil, final Optional<Boolean> isGroupMemberOpt) {
        if(isCivil.isPresent()){
            final boolean isGroupMember = isGroupMemberOpt.orElse(false);
            return defenceClientRepository.findCasesAssociatedWithDefenceClientByOrganisationDefendant(organisationName, isCivil.get(), isGroupMember);
        }else{
            return getCasesAssociatedWithDefenceClientByOrganisationDefendant(organisationName);
        }
    }

    private AssociatedOrganisationVO getAssociatedOrganisation(final UUID organisationId) {
        return new AssociatedOrganisationVO(organisationId, null);
    }

    public Optional<UUID> getMaterialIdForDefenceClient(final UUID defenceClientId) {
        final IdpcDetails idpcDetails = idpcDetailsRepository.findOptionalByDefenceClientId(defenceClientId);
        return idpcDetails != null ? of(idpcDetails.getMaterialId()) : empty();
    }

    public IdpcDetails getIDPCDetailsForDefenceClient(final UUID defenceClientId) {
        return idpcDetailsRepository.findOptionalByDefenceClientId(defenceClientId);
    }

    public boolean hasUserDeclaredAnInstructionForDefenceClient(final UUID userId, final UUID defenceClientId) {
        return instructionRepository.findNumberOfInstructionsForUserForDefenceClient(defenceClientId, userId) > 0;
    }

    public boolean hasSomeUserDeclaredAnInstructionForDefenceClientForOrganisation(final UUID defenceClientId, final UUID organisationId) {
        return instructionRepository.findNumberOfInstructionsByCriteria(defenceClientId, organisationId) > 0;
    }

    public Envelope<Allegations> findAllegationsByDefenceClientId(final Envelope<DefenceClientId> query) {
        final UUID defenceClientId = query.payload().getDefenceClientId();
        final DefenceClient defenceClient = defenceClientRepository.findBy(defenceClientId);

        if (defenceClient == null || !defenceClient.getVisible()) {
            return envelopeFrom(query.metadata(), null);
        }

        final Set<Allegation> allegationList = defenceClient.getAllegationList();
        final List<AllegationDetail> allegationDetails = new ArrayList<>(allegationList.size());

        allegationList.forEach(allegation -> {
            final AllegationDetail allegationDetail = new AllegationDetail.Builder()
                    .withLegislation(allegation.getLegislation())
                    .withTitle(allegation.getTitle())
                    .withChargedDate(allegation.getChargeDate().toString())
                    .build();
            allegationDetails.add(allegationDetail);
        });

        final Allegations allegations = Allegations.allegations()
                .withDefenceClientId(defenceClientId)
                .withAllegationDetail(allegationDetails)
                .withIdpcPublished(defenceClient.getIdpcDetailsId() != null)
                .build();

        return envelopeFrom(query.metadata(), allegations);
    }

    public Envelope<DefenceClientIdpcMetadata> findIdpcMetadataForDefenceClient(final Envelope<DefenceClientId> query) {
        final UUID defenceClientId = query.payload().getDefenceClientId();
        final IdpcDetails idpcDetails = idpcDetailsRepository.findOptionalByDefenceClientId(defenceClientId);
        if (idpcDetails == null) {
            return envelopeFrom(query.metadata(), null);
        }
        final IdpcMetadata idpcMetadata = new IdpcMetadata(idpcDetails.getDocumentName(), idpcDetails.getPageCount(), idpcDetails.getPublishedDate().toString(), idpcDetails.getSize());
        final DefenceClientIdpcMetadata defenceClientIdpcMetadata = new DefenceClientIdpcMetadata(idpcMetadata);
        return envelopeFrom(query.metadata(), defenceClientIdpcMetadata);
    }

    public Envelope<DefenceClientIdpcMetadata> findIdpcMetadataForDefendant(final Envelope<Defendant> query) {
        final UUID defendantId = query.payload().getDefendantId();
        final IdpcDetails idpcDetails = idpcDetailsRepository.findIdpcDetailsForDefendantId(defendantId);
        if (idpcDetails == null) {
            return envelopeFrom(query.metadata(), null);
        }
        final IdpcMetadata idpcMetadata = new IdpcMetadata(idpcDetails.getDocumentName(), idpcDetails.getPageCount(), idpcDetails.getPublishedDate().toString(), idpcDetails.getSize());
        final DefenceClientIdpcMetadata defenceClientIdpcMetadata = new DefenceClientIdpcMetadata(idpcMetadata);
        return envelopeFrom(query.metadata(), defenceClientIdpcMetadata);
    }

    public String getIdpcFileName(final UUID defenceClientId) {
        final IdpcDetails idpcDetails = idpcDetailsRepository.findOptionalByDefenceClientId(defenceClientId);
        String fileName = DEFAULT_FILE_NAME;
        if (idpcDetails != null) {
            fileName = idpcDetails.getDocumentName().replaceAll(SPACE, "_").concat(".pdf");
        }
        return fileName;
    }

    public Envelope<CaseDefendantsOrganisations> getCaseDefendantsWithOrganisations(final JsonEnvelope query) {
        final UUID caseId = UUID.fromString(query.payloadAsJsonObject().getString("caseId"));
        final DefenceCase defenceCase = defenceCaseRepository.findBy(caseId);
        final List<DefenceClient> defenceClients = defenceClientRepository.findByCaseId(caseId);
        final List<Defendant> defendantsList = defenceClients.stream().map(
                        defenceClient -> Defendant.defendant()
                                .withAssociatedOrganisation(defenceClient.getAssociatedOrganisation())
                                .withDefendantId(defenceClient.getDefendantId())
                                .withDefendantFirstName(defenceClient.getFirstName())
                                .withDefendantLastName(defenceClient.getLastName())
                                .withOrganisationName(defenceClient.getOrganisationName()).build())
                .collect(Collectors.toList());

        final CaseDefendantsWithOrganisation.Builder caseDefendantsBuilder = caseDefendantsWithOrganisation()
                .withDefendants(defendantsList);
        ofNullable(defenceCase).map(DefenceCase::getId).ifPresent(caseDefendantsBuilder::withCaseId);
        ofNullable(defenceCase).map(DefenceCase::getUrn).ifPresent(caseDefendantsBuilder::withUrn);

        final CaseDefendantsOrganisations caseDefendantsOrganisations = caseDefendantsOrganisations()
                .withCaseDefendantOrganisation(caseDefendantsBuilder.build())
                .build();

        return envelopeFrom(query.metadata(), caseDefendantsOrganisations);
    }

    public JsonObject getCaseDetailsByPersonDefendantAndHearingDate(final JsonEnvelope request, final String firstName, final String lastName, final String dateOfBirth, final String hearingDate, final Optional<Boolean> isCivil) {

        final JsonObjectBuilder queryParams = createObjectBuilder()
                .add("firstName", firstName)
                .add("lastName", lastName)
                .add("hearingDate", hearingDate);

        if (isNotEmpty(dateOfBirth)) {
            queryParams.add("dateOfBirth", dateOfBirth);
        }

        if (isCivil.isPresent()) {
            queryParams.add("isCivil", isCivil.get());

            if (isCivil.get()) {
                queryParams.add("isGroupMember", false);
            }
        }

        final Envelope<JsonObject> response = requester.request(JsonEnvelope.envelopeFrom(metadataFrom(request.metadata())
                .withName("listing.get.cases-by-person-defendant"), queryParams.build()), JsonObject.class);

        return response.payload();
    }

    public JsonObject getCaseDetailsByOrganisationDefendantAndHearingDate(final JsonEnvelope request, final String organisationName, final String hearingDate, final Optional<Boolean> isCivil){
        final JsonObjectBuilder queryParams = createObjectBuilder()
                .add("organisationName", organisationName)
                .add("hearingDate", hearingDate);

        if(isCivil.isPresent()){
            queryParams.add("isCivil", isCivil.get());

            if (isCivil.get()) {
                queryParams.add("isGroupMember", false);
            }
        }

        final Envelope<JsonObject> response = requester.request(JsonEnvelope.envelopeFrom(metadataFrom(request.metadata())
                .withName("listing.get.cases-by-organisation-defendant"), queryParams.build()), JsonObject.class);

        return response.payload();
    }

    List<DefenceClient> findDefenceClientByCriteria(final String firstName, final String lastName, final String dob, final String urn, final Optional<Boolean> isCivil) {
        if (isCivil.isPresent()) {
            if (EMPTY.equalsIgnoreCase(dob)) {
                return defenceClientRepository.findDefenceClientByCriteriaWithOutDob(firstName, lastName, urn.toUpperCase(), isCivil.get());
            } else {
                return defenceClientRepository.findDefenceClientByCriteria(firstName, lastName, parse(dob), urn.toUpperCase(), isCivil.get());
            }
        } else {
            return defenceClientRepository.findDefenceClientByCriteria(firstName, lastName, parse(dob), urn.toUpperCase());
        }
    }

    List<DefenceClient> findDefenceClientByCriteria(final String firstName, final String lastName, final String dob, final Optional<Boolean> isCivil) {
        if (isCivil.isPresent()) {
            if (EMPTY.equalsIgnoreCase(dob)) {
                return defenceClientRepository.findDefenceClientByCriteriaWithOutDob(firstName, lastName, isCivil.get());
            } else {
                return defenceClientRepository.findDefenceClientByCriteria(firstName, lastName, parse(dob), isCivil.get());
            }
        } else {
            return defenceClientRepository.findDefenceClientByCriteria(firstName, lastName, parse(dob));
        }
    }

    List<DefenceClient> findDefenceClientByCriteria(final String organisationName, final String urn, final Optional<Boolean> isCivil) {
        if (isCivil.isPresent()) {
            return defenceClientRepository.findDefenceClientByCriteria(organisationName, urn.toUpperCase(), isCivil.get());
        } else {
            return defenceClientRepository.findDefenceClientByCriteria(organisationName, urn.toUpperCase());
        }
    }

    List<DefenceClient> findDefenceClientByCriteria(final String organisationName, final Optional<Boolean> isCivil) {
        if (isCivil.isPresent()) {
            return defenceClientRepository.findDefenceClientByCriteria(organisationName, isCivil.get());
        } else {
            return defenceClientRepository.findDefenceClientByCriteria(organisationName);
        }
    }

}
