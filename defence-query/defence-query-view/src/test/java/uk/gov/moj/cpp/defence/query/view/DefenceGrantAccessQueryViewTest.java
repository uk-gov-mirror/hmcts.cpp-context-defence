package uk.gov.moj.cpp.defence.query.view;

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.MetadataBuilder;
import uk.gov.moj.cpp.defence.persistence.DefenceGrantAccessRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceGrantAccess;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceUserDetails;
import uk.gov.moj.cpp.defence.persistence.entity.OrganisationDetails;

import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DefenceGrantAccessQueryViewTest {


    public static final String GRANTEES = "grantees";
    @Mock
    DefenceGrantAccessRepository defenceGrantAccessRepository;

    @Spy
    ObjectMapper mapper = new ObjectMapperProducer().objectMapper();

    @Spy
    ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(mapper);

    @InjectMocks
    private DefenceGrantAccessQueryView defenceGrantAccessQueryView;

    @Test
    public void getEmptyDefenceGrantListFromRepositoryIsEmpty() {

        when(defenceGrantAccessRepository.findByDefenceClient(any())).thenReturn(emptyList());
        final JsonEnvelope defenceClientGrantees = defenceGrantAccessQueryView.getDefenceClientGrantees(stubbedQueryObjectForClientGrantees(randomUUID()));
        final JsonArray grantees = defenceClientGrantees.payloadAsJsonObject().getJsonArray(GRANTEES);

        assertThat(grantees.toString(), equalTo("[]"));
    }

    @Test
    public void getEmptyDefenceGrantAccessFromRepositoryWhenNoDataFoundForUserIdAndCaseId() {

        when(defenceGrantAccessRepository.findByGranteeAndCaseId(any(), any())).thenReturn(null);
        final JsonEnvelope defenceClientGrantee = defenceGrantAccessQueryView.getCaseGrantee(stubbedQueryObjectForDefenceGrantee(randomUUID(), randomUUID()));
        final JsonArray grantee = defenceClientGrantee.payloadAsJsonObject().getJsonArray(GRANTEES);

        assertThat(grantee.toString(), equalTo("[]"));
    }

    @Test
    public void getEmptyDefenceGrantListFromRepositoryIsNull() {

        when(defenceGrantAccessRepository.findByDefenceClient(any())).thenReturn(null);
        final JsonEnvelope defenceClientGrantees = defenceGrantAccessQueryView.getDefenceClientGrantees(stubbedQueryObjectForClientGrantees(randomUUID()));
        final JsonArray grantees = defenceClientGrantees.payloadAsJsonObject().getJsonArray(GRANTEES);

        assertThat(grantees.toString(), equalTo("[]"));
    }

    @Test
    public void getDefenceGranteeList() {

        UUID userId = randomUUID();
        UUID grantorUserId = randomUUID();
        UUID organisationId = randomUUID();

        final ZonedDateTime startDate = new UtcClock().now();

        OrganisationDetails organisationDetails = new OrganisationDetails(randomUUID(), organisationId, "Test Ltd");
        DefenceGrantAccess defenceGrantAccess = new DefenceGrantAccess();
        defenceGrantAccess.setId(randomUUID());
        defenceGrantAccess.setStartDate(startDate);
        defenceGrantAccess.setGranteeDefenceUserDetails(new DefenceUserDetails(randomUUID(), userId, "John", "Trackey"));
        defenceGrantAccess.setGrantorDefenceUserDetails(new DefenceUserDetails(randomUUID(), grantorUserId, "Tim", "Quick"));
        defenceGrantAccess.setGranteeOrganisationDetails(organisationDetails);


        UUID userId1 = randomUUID();
        UUID grantorUserId1 = randomUUID();
        UUID organisationId2 = randomUUID();

        OrganisationDetails organisationDetails1 = new OrganisationDetails(randomUUID(), organisationId2, "Test Ltd 2");

        DefenceGrantAccess dga = new DefenceGrantAccess();
        dga.setId(randomUUID());

        dga.setStartDate(startDate);
        dga.setGranteeDefenceUserDetails(new DefenceUserDetails(randomUUID(), userId1, "Abrham", "Link"));
        dga.setGrantorDefenceUserDetails(new DefenceUserDetails(randomUUID(), grantorUserId1, "Track", "Crack"));
        dga.setGranteeOrganisationDetails(organisationDetails1);

        when(defenceGrantAccessRepository.findByDefenceClient(any())).thenReturn(asList(defenceGrantAccess, dga));
        final JsonEnvelope defenceClientGrantees = defenceGrantAccessQueryView.getDefenceClientGrantees(stubbedQueryObjectForClientGrantees(randomUUID()));
        final JsonArray grantees = defenceClientGrantees.payloadAsJsonObject().getJsonArray(GRANTEES);

        assertThat(grantees, notNullValue());
        assertThat(getValue(grantees.getJsonObject(0), "organisationId"), equalTo(organisationId.toString()));
        assertThat(getValue(grantees.getJsonObject(0), "organisationName"), equalTo("Test Ltd"));
        assertThat(getValue(grantees.getJsonObject(0), "granteeStatus"), equalTo("Granted access by Tim Quick"));
        assertThat(getValue(grantees.getJsonObject(0), "userId"), equalTo(userId.toString()));
        assertThat(ZonedDateTime.parse(getValue(grantees.getJsonObject(0), "startDate")).toLocalDate(), is(startDate.toLocalDate()));


        assertThat(getValue(grantees.getJsonObject(1), "organisationId"), equalTo(organisationId2.toString()));
        assertThat(getValue(grantees.getJsonObject(1), "organisationName"), equalTo("Test Ltd 2"));
        assertThat(getValue(grantees.getJsonObject(1), "granteeStatus"), equalTo("Granted access by Track Crack"));
        assertThat(getValue(grantees.getJsonObject(1), "userId"), equalTo(userId1.toString()));
        assertThat(ZonedDateTime.parse(getValue(grantees.getJsonObject(1), "startDate")).toLocalDate(), is(startDate.toLocalDate()));

    }

    @Test
    public void getCaseGranteeWithCaseIdAndUserId() {

        UUID granteeUserId = randomUUID();
        UUID grantorUserId = randomUUID();
        UUID organisationId = randomUUID();

        OrganisationDetails organisationDetails = new OrganisationDetails(randomUUID(), organisationId, "Test Ltd");
        DefenceGrantAccess defenceGrantAccess = new DefenceGrantAccess();
        defenceGrantAccess.setId(randomUUID());
        defenceGrantAccess.setStartDate(new UtcClock().now().truncatedTo(MILLIS));
        defenceGrantAccess.setGranteeDefenceUserDetails(new DefenceUserDetails(randomUUID(), granteeUserId, "John", "Trackey"));
        defenceGrantAccess.setGrantorDefenceUserDetails(new DefenceUserDetails(randomUUID(), grantorUserId, "Tim", "Quick"));
        defenceGrantAccess.setGranteeOrganisationDetails(organisationDetails);
        defenceGrantAccess.setDefenceClient(new DefenceClient(randomUUID(), null, null, randomUUID(), null, null));

        when(defenceGrantAccessRepository.findByGranteeAndCaseId(defenceGrantAccess.getDefenceClient().getCaseId(), granteeUserId)).thenReturn(singletonList(defenceGrantAccess));
        final JsonEnvelope defenceClientGrantees = defenceGrantAccessQueryView.getCaseGrantee(stubbedQueryObjectForDefenceGrantee(defenceGrantAccess.getDefenceClient().getCaseId(), granteeUserId));
        final JsonArray grantees = defenceClientGrantees.payloadAsJsonObject().getJsonArray(GRANTEES);

        assertThat(grantees, notNullValue());
        assertThat(getValue(grantees.getJsonObject(0), "organisationId"), equalTo(organisationId.toString()));
        assertThat(getValue(grantees.getJsonObject(0), "organisationName"), equalTo("Test Ltd"));
        assertThat(getValue(grantees.getJsonObject(0), "granteeStatus"), equalTo("Granted access by Tim Quick"));
        assertThat(getValue(grantees.getJsonObject(0), "userId"), equalTo(granteeUserId.toString()));
        assertThat(getValue(grantees.getJsonObject(0), "startDate"), equalTo(defenceGrantAccess.getStartDate().toString()));

    }

    private String getValue(final JsonObject defenceGrantList, final String key) {
        return defenceGrantList.getString(key);
    }

    private JsonEnvelope stubbedQueryObjectForClientGrantees(UUID defenceClientId) {
        return JsonEnvelope.envelopeFrom(
                stubbedMetadataBuilder("defence.query.grantees-organisation"),
                stubbedQueryObjectPayloadForClientGrantees(defenceClientId));
    }

    private JsonEnvelope stubbedQueryObjectForDefenceGrantee(final UUID caseId, final UUID granteeUserId) {
        return JsonEnvelope.envelopeFrom(
                stubbedMetadataBuilder("defence.query.defence-grantee"),
                stubbedQueryObjectPayloadForDefenceGrantee(caseId, granteeUserId));
    }

    private JsonObject stubbedQueryObjectPayloadForClientGrantees(UUID defenceClientId) {
        return createObjectBuilder()
                .add("defendantClientId", defenceClientId.toString())
                .build();
    }

    private JsonObject stubbedQueryObjectPayloadForDefenceGrantee(final UUID caseId, final UUID granteeUserId) {
        return createObjectBuilder()
                .add("caseId", caseId.toString())
                .add("granteeUserId", granteeUserId.toString())
                .build();
    }

    private MetadataBuilder stubbedMetadataBuilder(final String queryName) {
        return JsonEnvelope.metadataBuilder()
                .withId(randomUUID())
                .withName(queryName)
                .withCausation(randomUUID())
                .withClientCorrelationId(randomUUID().toString())
                .withStreamId(randomUUID())
                .withUserId(randomUUID().toString());
    }

}