package uk.gov.moj.cpp.defence.persistence;

import static java.time.LocalDate.now;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.defence.builder.DefenceClientBuilder.createDefenceClient;

import uk.gov.justice.services.test.utils.persistence.HibernateTestEntityManagerProvider;
import uk.gov.moj.cpp.defence.persistence.entity.Allegation;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class AllegationRepositoryTest {

    @RegisterExtension
    static HibernateTestEntityManagerProvider hibernateTestEntityManagerProvider =
            new HibernateTestEntityManagerProvider("defence-test-persistence-unit");

    private AllegationRepository allegationRepository;

    private DefenceClientRepository defenceClientRepository;

    @BeforeEach
    void createRepositories() {
        allegationRepository = new AllegationRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(allegationRepository);
        defenceClientRepository = new DefenceClientRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceClientRepository);
    }

    @Test
    public void shouldFindAllegationsByDefenceClientId() {

        final DefenceClient defClient = createDefenceClient();
        defenceClientRepository.saveAndFlush(defClient);

        final Allegation allegation = createAllegation(defClient);
        allegationRepository.saveAndFlush(allegation);

        insertOtherDefenceClientAndAllegationData();

        final List<Allegation> allegations = allegationRepository.findAllegationByCriteria(defClient.getId());

        assertThat(allegations.size(), is(1));
        final Allegation savedAllegation = allegations.get(0);
        assertThat(savedAllegation.getId(), is(allegation.getId()));
        assertThat(savedAllegation.getOffenceId(), is(allegation.getOffenceId()));
        assertThat(savedAllegation.getLegislation(), is(allegation.getLegislation()));
        assertThat(savedAllegation.getTitle(), is(allegation.getTitle()));
        assertThat(savedAllegation.getChargeDate(), is(now()));
    }

    @Test
    public void shouldFindAllegationsByDefenceClientIdAndOffenceId() {

        final DefenceClient defClient = createDefenceClient();
        defenceClientRepository.saveAndFlush(defClient);

        final Allegation allegation = createAllegation(defClient);
        allegationRepository.saveAndFlush(allegation);

        insertOtherDefenceClientAndAllegationData();

        final Allegation savedAllegation = allegationRepository.findAllegationByDefenceClientIdAndOffenceId(defClient.getId(), allegation.getOffenceId());

        assertThat(savedAllegation.getId(), is(allegation.getId()));
        assertThat(savedAllegation.getOffenceId(), is(allegation.getOffenceId()));
        assertThat(savedAllegation.getLegislation(), is(allegation.getLegislation()));
        assertThat(savedAllegation.getTitle(), is(allegation.getTitle()));
        assertThat(savedAllegation.getChargeDate(), is(now()));
    }

    private void insertOtherDefenceClientAndAllegationData() {

        DefenceClient defClient = createDefenceClient();
        defenceClientRepository.saveAndFlush(defClient);

        Set<Allegation> allegationList = new HashSet<>();
        allegationList.add(createAllegation(defClient));
        allegationList.add(createAllegation(defClient));
        allegationList.add(createAllegation(defClient));
        allegationList.forEach(allegationRepository::saveAndFlush);



        defClient = createDefenceClient();
        defClient.setVisible(false);
        defenceClientRepository.saveAndFlush(defClient);
        allegationList = new HashSet<>();
        allegationList.add(createAllegation(defClient));
        allegationList.add(createAllegation(defClient));
        allegationList.add(createAllegation(defClient));
        allegationList.add(createAllegation(defClient));
        allegationList.forEach(allegationRepository::saveAndFlush);
    }


    @Test
    public void shouldNotReturnAnyAllegationsWhenDefenceClientMarkedNotVisible() {

        final DefenceClient defClient = createDefenceClient();
        defClient.setVisible(false);
        defenceClientRepository.saveAndFlush(defClient);

        final Allegation allegation = createAllegation(defClient);
        allegationRepository.saveAndFlush(allegation);

        insertOtherDefenceClientAndAllegationData();

        final List<Allegation> allegations = allegationRepository.findAllegationByCriteria(defClient.getId());

        assertThat(allegations.size(), is(0));
    }

    private Allegation createAllegation(final DefenceClient defClient) {
        final UUID allegationId = UUID.randomUUID();
        final String legislation = "s18, Offences Against the Person Act 1861";
        final String title = "Cause grievous bodily harm with intent";
        final UUID offenceId = UUID.randomUUID();
        final LocalDate chargeDate = now();

        return new Allegation(allegationId, defClient.getId(),offenceId, legislation, title, chargeDate);
    }
}
