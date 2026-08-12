package uk.gov.moj.cpp.defence.query.api.service;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.MetadataBuilder;

import jakarta.json.JsonObject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;

@SuppressWarnings({"squid:S00112", "squid:S1192", "squid:CallToDeprecatedMethod"})
public class RefDataService {
    private static final String REFERENCEDATA_QUERY_PUBLIC_HOLIDAYS_NAME = "referencedata.query.public-holidays";
    private static final String DATE = "date";
    private static final String PUBLIC_HOLIDAYS = "publicHolidays";

    public List<LocalDate> getPublicHolidays(final String division, final LocalDate fromDate, final LocalDate toDate, final Requester requester) {

        final MetadataBuilder metadataBuilder = metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_PUBLIC_HOLIDAYS_NAME);

        final JsonObject params = createObjectBuilder()
                .add("division", division)
                .add("dateFrom", fromDate.toString())
                .add("dateTo", toDate.toString())
                .build();

        final JsonObject payload = requester.requestAsAdmin(envelopeFrom(metadataBuilder, params), JsonObject.class).payload();
        if (!payload.containsKey(PUBLIC_HOLIDAYS) || payload.getJsonArray(PUBLIC_HOLIDAYS).isEmpty()) {
            return emptyList();
        }

        final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        return payload.getJsonArray(PUBLIC_HOLIDAYS).getValuesAs(JsonObject.class).stream()
                .map(jsonObject -> jsonObject.getString(DATE))
                .map(date -> LocalDate.parse(date, dateFormat))
                .toList();
    }

}