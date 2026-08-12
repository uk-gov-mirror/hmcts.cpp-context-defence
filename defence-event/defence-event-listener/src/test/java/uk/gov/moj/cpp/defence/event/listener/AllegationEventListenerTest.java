package uk.gov.moj.cpp.defence.event.listener;

import static java.time.LocalDate.now;
import static java.util.Arrays.asList;
import static java.util.UUID.randomUUID;
import java.util.HashSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.cps.defence.OffenceCodeReferenceData.offenceCodeReferenceData;

import uk.gov.justice.cps.defence.AllegationsReceivedAgainstADefenceClient;
import uk.gov.justice.cps.defence.Offence;
import uk.gov.justice.cps.defence.OffenceCode;
import uk.gov.justice.cps.defence.OffenceCodeReferenceData;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.event.listener.events.AddedOffences;
import uk.gov.moj.cpp.defence.event.listener.events.DefendantOffencesUpdated;
import uk.gov.moj.cpp.defence.event.listener.events.DeletedOffences;
import uk.gov.moj.cpp.defence.persistence.AllegationRepository;
import uk.gov.moj.cpp.defence.persistence.DefenceClientRepository;
import uk.gov.moj.cpp.defence.persistence.entity.Allegation;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;

import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AllegationEventListenerTest {

    private static final UUID DEFENCE_CLIENT_ID = randomUUID();
    private static final String TITLE_FROM_REFDATA = "Title string from reference data";
    private static final String LEGISLATION_FROM_REFDATA = "Legislation String from reference data";
    private static final String CJS_CODE = "PS90010";
    private static final UUID OFFENCE_ID = UUID.randomUUID();


    @Mock
    private AllegationRepository allegationRepositoryMock;

    @Mock
    private DefenceClientRepository defenceClientRepository;

    @Mock
    private Envelope<AllegationsReceivedAgainstADefenceClient> allegationsReceivedAgainstADefenceClientEnvelope;

    @Mock
    private Envelope<DefendantOffencesUpdated> defendantOffencesUpdatedEnvelope;

    @InjectMocks
    private AllegationEventListener allegationEventListener;

    @Test
    public void shouldSaveAllegationToRepository() {

        final AllegationsReceivedAgainstADefenceClient allegationsReceivedAgainstADefenceClient = createAllegationsReceivedAgainstADefenceClient();
        when(allegationsReceivedAgainstADefenceClientEnvelope.payload()).thenReturn(allegationsReceivedAgainstADefenceClient);

        final ArgumentCaptor<Allegation> argumentCaptor = ArgumentCaptor.forClass(Allegation.class);

        allegationEventListener.suspectIsCharged(allegationsReceivedAgainstADefenceClientEnvelope);

        verify(allegationRepositoryMock, times(1)).save(argumentCaptor.capture());

        final Allegation savedAllegation = argumentCaptor.getValue();

        assertThat(savedAllegation.getChargeDate(), is(LocalDate.of(2018, 12, 1)));
        assertThat(savedAllegation.getLegislation(), is(LEGISLATION_FROM_REFDATA));
        assertThat(savedAllegation.getTitle(), is(TITLE_FROM_REFDATA));
        assertThat(savedAllegation.getDefenceClientId(), is(DEFENCE_CLIENT_ID));
        assertThat(savedAllegation.getOffenceId(), is(OFFENCE_ID));
        assertThat(savedAllegation.getId(), is(notNullValue()));
    }

    @Test
    public void shouldAddAllegation() {
        final UUID defendantId = randomUUID();
        final DefendantOffencesUpdated defendantOffencesUpdated = DefendantOffencesUpdated.defendantOffencesUpdated()
                .withAddedOffences(asList(AddedOffences.addedOffences()
                        .withDefendantId(defendantId)
                        .withDefenceClientId(defendantId)
                        .withOffences(asList(Offence.offence()
                                .withStartDate(now().toString())
                                .withId(randomUUID())
                                .withOffenceCodeDetails(OffenceCode.offenceCode().withLegislation("legislation").withTitle("offenceTitle").build())
                                .build()))
                        .build()))
                .withModifiedDate(now())
                .build();

        final Metadata metadata = getMetaData(randomUUID(), defendantId);

        final DefenceClient defenceClient = new DefenceClient();
        defenceClient.setId(randomUUID());
        defenceClient.setCaseId(randomUUID());
        defenceClient.setFirstName("firstName");
        defenceClient.setLastName("lastName");
        defenceClient.setDefendantId(defendantId);

        when(defendantOffencesUpdatedEnvelope.payload()).thenReturn(defendantOffencesUpdated);
        when(defendantOffencesUpdatedEnvelope.metadata()).thenReturn(metadata);
        when(defenceClientRepository.findOptionalByDefendantId(metadata.streamId().get())).thenReturn(defenceClient);
        final ArgumentCaptor<DefenceClient> argumentCaptor = ArgumentCaptor.forClass(DefenceClient.class);

        allegationEventListener.defendantOffencesUpdated(defendantOffencesUpdatedEnvelope);

        verify(defenceClientRepository, times(1)).save(argumentCaptor.capture());

        final DefenceClient savedAllegation = argumentCaptor.getValue();

        assertThat(savedAllegation.getId(), notNullValue());
        assertThat(savedAllegation.getDefendantId(), is(defendantOffencesUpdated.getAddedOffences().get(0).getDefendantId()));
        assertThat(savedAllegation.getFirstName(), is(defenceClient.getFirstName()));
        assertThat(savedAllegation.getLastName(), is(defenceClient.getLastName()));
        assertThat(savedAllegation.getCaseId(), is(defenceClient.getCaseId()));
        assertThat(savedAllegation.getAllegationList().size(), is(1));

    }

    @Test
    public void shouldIgnoreIfDefenceClientNotCreatedYet() {
        final UUID defendantId = randomUUID();
        final DefendantOffencesUpdated defendantOffencesUpdated = DefendantOffencesUpdated.defendantOffencesUpdated()
                .withAddedOffences(asList(AddedOffences.addedOffences()
                        .withDefendantId(defendantId)
                        .withDefenceClientId(defendantId)
                        .withOffences(asList(Offence.offence()
                                .withStartDate(now().toString())
                                .withId(randomUUID())
                                .withOffenceCodeDetails(OffenceCode.offenceCode().withLegislation("legislation").withTitle("offenceTitle").build())
                                .build()))
                        .build()))
                .withModifiedDate(now())
                .build();

        final Metadata metadata = getMetaData(randomUUID(), defendantId);

        when(defendantOffencesUpdatedEnvelope.payload()).thenReturn(defendantOffencesUpdated);
        when(defendantOffencesUpdatedEnvelope.metadata()).thenReturn(metadata);
        when(defenceClientRepository.findOptionalByDefendantId(metadata.streamId().get())).thenReturn(null);
        final ArgumentCaptor<DefenceClient> argumentCaptor = ArgumentCaptor.forClass(DefenceClient.class);

        allegationEventListener.defendantOffencesUpdated(defendantOffencesUpdatedEnvelope);

        verify(defenceClientRepository,never()).save(argumentCaptor.capture());

    }

    @Test
    public void shouldDeleteAllegation() {
        final UUID defendantId = randomUUID();
        final UUID deletedOffenceId = randomUUID();
        final DefendantOffencesUpdated defendantOffencesUpdated = DefendantOffencesUpdated.defendantOffencesUpdated()
                .withDeletedOffences(Collections.singletonList(DeletedOffences.deletedOffences()
                        .withDefendantId(defendantId)
                        .withDefenceClientId(defendantId)
                        .withOffences(Collections.singletonList(deletedOffenceId))
                        .build()))
                .withModifiedDate(now())
                .build();

        final Metadata metadata = getMetaData(randomUUID(), defendantId);

        Allegation allegation = new Allegation();
        allegation.setOffenceId(deletedOffenceId);
        allegation.setDefenceClientId(defendantId);

        final DefenceClient defenceClient = new DefenceClient();
        defenceClient.setId(randomUUID());
        defenceClient.setCaseId(randomUUID());
        defenceClient.setFirstName("firstName");
        defenceClient.setLastName("lastName");
        defenceClient.setDefendantId(defendantId);
        defenceClient.setAllegationList(new HashSet<>(asList(allegation)));

        when(defendantOffencesUpdatedEnvelope.payload()).thenReturn(defendantOffencesUpdated);
        when(defendantOffencesUpdatedEnvelope.metadata()).thenReturn(metadata);
        when(defenceClientRepository.findOptionalByDefendantId(metadata.streamId().get())).thenReturn(defenceClient);
        final ArgumentCaptor<DefenceClient> argumentCaptor = ArgumentCaptor.forClass(DefenceClient.class);

        allegationEventListener.defendantOffencesUpdated(defendantOffencesUpdatedEnvelope);

        verify(defenceClientRepository, times(1)).save(argumentCaptor.capture());

        final DefenceClient savedAllegation = argumentCaptor.getValue();

        assertThat(savedAllegation.getId(), notNullValue());
        assertThat(savedAllegation.getDefendantId(), is(defenceClient.getDefendantId()));
        assertThat(savedAllegation.getFirstName(), is(defenceClient.getFirstName()));
        assertThat(savedAllegation.getLastName(), is(defenceClient.getLastName()));
        assertThat(savedAllegation.getCaseId(), is(defenceClient.getCaseId()));
        assertThat(savedAllegation.getAllegationList(), empty());

    }


    private AllegationsReceivedAgainstADefenceClient createAllegationsReceivedAgainstADefenceClient() {
        final OffenceCode offenceCode = new OffenceCode.Builder()
                .withLegislation("LEGISLATION")
                .withTitle("TITLE")
                .build();

        final Offence offence = new Offence.Builder()
                .withId(OFFENCE_ID)
                .withDescription(TITLE_FROM_REFDATA)
                .withWording(LEGISLATION_FROM_REFDATA)
                .withStartDate("2018-12-01")
                .withCjsCode(CJS_CODE)
                .withOffenceCodeDetails(offenceCode)
                .build();

        final OffenceCodeReferenceData offenceCodeReferenceData = offenceCodeReferenceData()
                .withCjsoffencecode(CJS_CODE)
                .withLegislation(LEGISLATION_FROM_REFDATA)
                .withTitle(TITLE_FROM_REFDATA)
                .build();


        return new AllegationsReceivedAgainstADefenceClient.Builder()
                .withDefenceClientId(DEFENCE_CLIENT_ID)
                .withOffences(asList(offence))
                .withOffenceCodeReferenceData(asList(offenceCodeReferenceData))
                .build();
    }

    private Metadata getMetaData(final UUID uuid, final UUID userId) {
        return Envelope
                .metadataBuilder()
                .withStreamId(userId)
                .withName("anyEventName")
                .withId(uuid)
                .withUserId(userId.toString())
                .build();
    }
}
