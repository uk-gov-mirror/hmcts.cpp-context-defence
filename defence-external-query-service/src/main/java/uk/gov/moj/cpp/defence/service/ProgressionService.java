package uk.gov.moj.cpp.defence.service;

import static com.google.common.collect.Lists.partition;
import static java.util.Objects.nonNull;
import static java.util.UUID.fromString;
import static java.util.stream.Collectors.toList;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_API;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.progression.Prosecutor;
import uk.gov.moj.cpp.defence.query.view.ProsecutionCaseAuthority;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProgressionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressionService.class);
    private static final String PROGRESSION_QUERY_PROSECUTIONCASE_CAAG = "progression.query.prosecutioncase.caag";
    private static final String PROGRESSION_QUERY_PROSECUTION_CASE = "progression.query.prosecutioncase";
    private static final String PROGRESSION_QUERY_PROSECUTION_CASES = "progression.query.prosecutorid-prosecutionauthorityid-by-case-id";
    private static final String CASE_ID = "caseId";
    public static final String PROSECUTION_CASE = "prosecutionCase";
    public static final String PROSECUTION_CASE_IDENTIFIER = "prosecutionCaseIdentifier";
    public static final String PROSECUTION_AUTHORITY_ID = "prosecutionAuthorityId";
    public static final String CASE_IDS = "caseIds";
    public static final String DELIMITER = ",";
    public static final String PROSECUTORS = "prosecutors";
    public static final String PROSECUTOR = "prosecutor";
    public static final String PROSECUTOR_ID = "prosecutorId";

    private int progressionQueryCaseLimit = 20;

    @Inject
    @ServiceComponent(COMMAND_API)
    private Requester requester;


    @SuppressWarnings("squid:S1166")
    public ProsecutionCaseAuthority getProsecutionCaseAuthority(final Metadata metadata, final UUID caseId) {
        final UUID prosecutionAuthorityId = getProsecutorOrProsecutionCaseAuthorityID(metadata, caseId);
        if(nonNull(prosecutionAuthorityId)){
            return ProsecutionCaseAuthority.prosecutionCaseAuthority()
                    .withProsecutionAuthorityId(prosecutionAuthorityId)
                    .build();
        }
        return null;
    }

    public UUID getProsecutorOrProsecutionCaseAuthorityID(final Metadata metadata, final UUID caseId) {
        final JsonObject response = getProsecutionCaseDetails(metadata, caseId);

        if (nonNull(response)) {
            if (hasProsecutorIdFound(response)) {
                return getProsecutorId(response);

            } else if (hasProsecutorDataFound(response)) {
                return getProsecutionAuthorityId(response);
            }
        }
        return null;
    }

    public JsonObject getProsecutionCaseDetails(final Metadata metadata, final UUID caseId) {
        final JsonEnvelope queryEnvelope = envelopeFrom(metadataFrom(metadata)
                        .withName(PROGRESSION_QUERY_PROSECUTION_CASE),
                createObjectBuilder().
                        add(CASE_ID, caseId.toString()).build());

        return requester.requestAsAdmin(queryEnvelope, JsonObject.class).payload();
    }

    @SuppressWarnings("squid:S2629")
    public JsonValue getProsecutionCaseDetailsAsJson(final Metadata metadata, final UUID caseId) {
        final JsonEnvelope queryEnvelope = envelopeFrom(metadataFrom(metadata)
                        .withName(PROGRESSION_QUERY_PROSECUTION_CASE),
                createObjectBuilder().
                        add(CASE_ID, caseId.toString()).build());

        final JsonValue response = requester.requestAsAdmin(queryEnvelope, JsonObject.class).payload();
        if (nonNull(response)) {
            LOGGER.info(response.toString());
        }
        return response;
    }

    public Map<UUID, UUID> getProsecutionAuthorityIdMap(final Metadata metadata, final List<UUID> caseIds) {
        final HashMap<UUID, UUID> prosecutionAuthorityIdMap = new HashMap<>();
        final List<Prosecutor> prosecutors = getProsecutionCaseDetailsForCases(metadata, caseIds);
        prosecutors.stream().forEach(p ->
                prosecutionAuthorityIdMap.putIfAbsent(p.getCaseId(), p.getProsecutionAuthorityId())
        );
        return prosecutionAuthorityIdMap;
    }

    public List<Prosecutor> getProsecutionCaseDetailsForCases(final Metadata metadata, final List<UUID> caseIds) {
        return partition(caseIds, progressionQueryCaseLimit).stream()
                .flatMap(caseIdList -> getProsecutorList(metadata, caseIdList).stream())
                .map(JsonObject.class::cast)
                .map(jsonObject -> Prosecutor.prosecutor()
                        .withCaseId(fromString(jsonObject.getJsonObject(PROSECUTOR).getString(CASE_ID)))
                        //Prosecutor.ProsecutionAuthorityId can contain prosecutorId or prosecution authorityid received from progression.
                        .withProsecutionAuthorityId(jsonObject.getJsonObject(PROSECUTOR).containsKey(PROSECUTOR_ID)
                                        ?fromString(jsonObject.getJsonObject(PROSECUTOR).getString(PROSECUTOR_ID))
                                        :fromString(jsonObject.getJsonObject(PROSECUTOR).getString(PROSECUTION_AUTHORITY_ID)))
                        .build())
                .collect(toList());

    }

    private JsonArray getProsecutorList(final Metadata metadata, final List<UUID> caseIds) {
        final JsonEnvelope queryEnvelope = envelopeFrom(metadataFrom(metadata)
                        .withName(PROGRESSION_QUERY_PROSECUTION_CASES),
                createObjectBuilder().add(CASE_IDS, getCaseIdsAsString(caseIds)).build());

        return requester.requestAsAdmin(queryEnvelope, JsonObject.class).payload().getJsonArray(PROSECUTORS);
    }

    private String getCaseIdsAsString(final List<UUID> caseIds) {
        return caseIds.stream().map(Object::toString).collect(Collectors.joining(DELIMITER));
    }

    public JsonObject getProsecutionCaseDetailsForCaag(final Metadata metadata, final UUID caseId) {
        final JsonEnvelope queryEnvelope = envelopeFrom(metadataFrom(metadata)
                        .withName(PROGRESSION_QUERY_PROSECUTIONCASE_CAAG),
                createObjectBuilder().
                        add(CASE_ID, caseId.toString()).build());

        return requester.requestAsAdmin(queryEnvelope, JsonObject.class).payload();
    }


    private boolean hasProsecutorDataFound(final JsonObject response) {
        return response.containsKey(PROSECUTION_CASE) && response.getJsonObject(PROSECUTION_CASE).containsKey(PROSECUTION_CASE_IDENTIFIER) && response.getJsonObject(PROSECUTION_CASE).getJsonObject(PROSECUTION_CASE_IDENTIFIER).containsKey(PROSECUTION_AUTHORITY_ID);
    }

    private boolean hasProsecutorIdFound(final JsonObject response) {
        return response.containsKey(PROSECUTION_CASE) && response.getJsonObject(PROSECUTION_CASE).containsKey(PROSECUTOR) && response.getJsonObject(PROSECUTION_CASE).getJsonObject(PROSECUTOR).containsKey(PROSECUTOR_ID);
    }

    private UUID getProsecutorId(final JsonObject response) {
        return fromString(response.getJsonObject(PROSECUTION_CASE).getJsonObject(PROSECUTOR).getString(PROSECUTOR_ID));
    }

    private UUID getProsecutionAuthorityId(final JsonObject response) {
        return fromString(response.getJsonObject(PROSECUTION_CASE).getJsonObject(PROSECUTION_CASE_IDENTIFIER).getJsonString(PROSECUTION_AUTHORITY_ID).getString());
    }
}
