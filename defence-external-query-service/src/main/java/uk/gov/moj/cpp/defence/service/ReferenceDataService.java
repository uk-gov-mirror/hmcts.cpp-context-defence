package uk.gov.moj.cpp.defence.service;

import static java.util.Collections.emptyMap;
import static java.util.Objects.isNull;
import static java.util.UUID.fromString;
import static java.util.stream.Collectors.toMap;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_API;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.refdata.ProsecutorDetails;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReferenceDataService {

    public static final String REFERENCEDATA_QUERY_PROSECUTOR = "referencedata.query.prosecutor";
    public static final String REFERENCEDATA_QUERY_PROSECUTORS = "referencedata.query.prosecutors";
    public static final String ID = "id";
    private static final String SHORT_NAME = "shortName";
    public static final String PROSECUTORS = "prosecutors";
    public static final String CPS_FLAG = "cpsFlag";
    public static final String POLICE_FLAG = "policeFlag";

    @Inject
    @ServiceComponent(COMMAND_API)
    private Requester requester;

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceDataService.class);

    public Optional<JsonObject> getProsecutor(final Metadata metadata, final UUID id) {

        LOGGER.info(" Calling {} to get prosecutors for {} ", REFERENCEDATA_QUERY_PROSECUTOR, id);

        final JsonObject payload = createObjectBuilder().add(ID, id.toString()).build();

        final Envelope<JsonObject> response = requester.requestAsAdmin(envelopeFrom(metadataFrom(metadata)
                .withName(REFERENCEDATA_QUERY_PROSECUTOR), payload), JsonObject.class);

        if (isNull(response.payload())) {
            return Optional.empty();
        }

        return Optional.of(response.payload());
    }

    private Optional<JsonObject> getProsecutors(final Metadata metadata) {

        LOGGER.info(" Calling {} to get prosecutors", REFERENCEDATA_QUERY_PROSECUTORS);

        final Envelope<JsonObject> response = requester.requestAsAdmin(envelopeFrom(metadataFrom(metadata)
                .withName(REFERENCEDATA_QUERY_PROSECUTORS), createObjectBuilder().build()), JsonObject.class);

        if (isNull(response.payload())) {
            return Optional.empty();
        }

        return Optional.of(response.payload());
    }

    public Map<UUID, ProsecutorDetails> getProsecutorsAsMap(final Metadata metadata) {
        final Optional<JsonObject> prosecutorJsonObjectOptional = getProsecutors(metadata);

        return prosecutorJsonObjectOptional.map(responseJsonObject -> responseJsonObject.getJsonArray(PROSECUTORS).stream()
                .map(JsonObject.class::cast)
                .map(jsonObject -> ProsecutorDetails.prosecutorDetails()
                        //ProsecutorDetails.ProsecutionAuthorityId contains prosecutor id received from reference data.
                        .withProsecutionAuthorityId(fromString(jsonObject.getString(ID)))
                        .withIsCps(getIsCpsFlag(jsonObject))
                        .withIsPolice(getIsPoliceFlag(jsonObject))
                        .withShortName(jsonObject.getString(SHORT_NAME))
                        .build())
                .collect(toMap(ProsecutorDetails::getProsecutionAuthorityId, prosecutorDetails -> prosecutorDetails))).orElse(emptyMap());

    }

    private Boolean getIsCpsFlag(final JsonObject jsonObject) {
        if (jsonObject.containsKey(CPS_FLAG)) {
            return jsonObject.getBoolean(CPS_FLAG);
        }
        return false;
    }

    private Boolean getIsPoliceFlag(final JsonObject jsonObject) {
        if (jsonObject.containsKey(POLICE_FLAG)) {
            return jsonObject.getBoolean(POLICE_FLAG);
        }
        return false;
    }

}