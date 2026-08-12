package uk.gov.moj.cpp.defence.persistence;

import static java.util.UUID.randomUUID;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import uk.gov.justice.services.test.utils.persistence.HibernateTestEntityManagerProvider;
import uk.gov.moj.cpp.defence.persistence.entity.IdpcAccess;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.awaitility.Durations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class IdpcAccessHistoryRepositoryTest {

    @RegisterExtension
    static HibernateTestEntityManagerProvider hibernateTestEntityManagerProvider =
            new HibernateTestEntityManagerProvider("defence-test-persistence-unit");

    private IdpcAccessHistoryRepository idpcAccessHistoryRepository;

    private DefenceClientRepository defenceClientRepository;

    @BeforeEach
    void createRepositories() {
        idpcAccessHistoryRepository = new IdpcAccessHistoryRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(idpcAccessHistoryRepository);
        defenceClientRepository = new DefenceClientRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceClientRepository);
    }

    private static UUID DEFENCECLIENT_ID = randomUUID();
    private static UUID USER_ID = randomUUID();
    private static UUID IDPC_ID = randomUUID();
    private static UUID ORGANISATION_ID = randomUUID();
    private static ZonedDateTime NOW = ZonedDateTime.now(ZoneId.of("UTC"));


    @Test
    public void shouldSaveIDPCAccess() {
        final IdpcAccess idpcAccess = createStaticIdpcAccess();
        final IdpcAccess savedIdpcAccess = idpcAccessHistoryRepository.save(idpcAccess);

        final List<IdpcAccess> resultList = idpcAccessHistoryRepository.findIdpcAccessByCriteria(DEFENCECLIENT_ID);
        //ensure it is there
        assertThat(resultList.size(), is(1));
        final IdpcAccess idpcAccessSaved = resultList.get(0);

        assertThat(savedIdpcAccess, is(idpcAccessSaved));
    }

    @Test
    public void shouldSaveMultipleIDPCAccesses() {
        final IdpcAccess idpcAccess1 = createRandomIdpcAccessFor(DEFENCECLIENT_ID);
        final IdpcAccess idpcAccess2 = createRandomIdpcAccessFor(DEFENCECLIENT_ID);
        final IdpcAccess savedIdpcAccess1 = idpcAccessHistoryRepository.save(idpcAccess1);
        final IdpcAccess savedIdpcAccess2 = idpcAccessHistoryRepository.save(idpcAccess2);

        final List<IdpcAccess> resultList = idpcAccessHistoryRepository.findIdpcAccessByCriteria(DEFENCECLIENT_ID);
        //ensure it is there
        assertThat(resultList.size(), is(2));

        assertThat(resultList, containsInAnyOrder(savedIdpcAccess1, savedIdpcAccess2));
    }

    @Test
    public void testAccessFromOrganisationByDifferentUsers() {
        List<UUID> organisations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID orgId = UUID.randomUUID();
            for (int j = 0; j < 5; j++) {
                IdpcAccess idpc = new IdpcAccess(randomUUID(), DEFENCECLIENT_ID, IDPC_ID,
                        randomUUID(), orgId, ZonedDateTime.now(ZoneId.of("UTC")));
                idpcAccessHistoryRepository.save(idpc);
                await().atMost(Durations.ONE_HUNDRED_MILLISECONDS);
            }
            //Latest user will always be at 0th location
            organisations.add(0, orgId);
        }

        final List<UUID> resultList = idpcAccessHistoryRepository.findOrderedDistinctOrgIdsOfIdpcAccessForDefenceClient(DEFENCECLIENT_ID);

        assertThat(resultList, is(organisations));
    }

    @Test
    public void shouldFindIdpcAccessByDefenceClientIdAndIdpcDetailsId() {
        final UUID defenceClientId = randomUUID();
        final UUID idpcDetailsId = randomUUID();
        final IdpcAccess matching = new IdpcAccess(randomUUID(), defenceClientId, idpcDetailsId, randomUUID(), randomUUID(), NOW);
        final IdpcAccess differentIdpcDetails = new IdpcAccess(randomUUID(), defenceClientId, randomUUID(), randomUUID(), randomUUID(), NOW);
        idpcAccessHistoryRepository.save(matching);
        idpcAccessHistoryRepository.save(differentIdpcDetails);

        final List<IdpcAccess> result = idpcAccessHistoryRepository.findIdpcAccessByCriteria(defenceClientId, idpcDetailsId);

        assertThat(result.size(), is(1));
        assertThat(result.get(0).getIdpcDetailsId(), is(idpcDetailsId));
    }

    @Test
    public void shouldFindIdpcAccessOrganisationByDefenceClientIdAndIdpcDetailsId() {
        final UUID defenceClientId = randomUUID();
        final UUID idpcDetailsId = randomUUID();
        final UUID organisationId = randomUUID();
        idpcAccessHistoryRepository.save(new IdpcAccess(randomUUID(), defenceClientId, idpcDetailsId, randomUUID(), organisationId, NOW));

        final List<UUID> result = idpcAccessHistoryRepository.findIdpcAccessOrganisationByCriteria(defenceClientId, idpcDetailsId);

        assertThat(result, is(List.of(organisationId)));
    }

    private IdpcAccess createStaticIdpcAccess() {
        return new IdpcAccess(randomUUID(), DEFENCECLIENT_ID, IDPC_ID, USER_ID, ORGANISATION_ID, NOW);
    }

    private IdpcAccess createRandomIdpcAccess() {
        return createIdpcAccessEntity(randomUUID(), randomUUID(), randomUUID(), randomUUID(), ZonedDateTime.now(ZoneId.of("UTC")));
    }

    private IdpcAccess createRandomIdpcAccessFor(final UUID defenceClientId) {
        return createIdpcAccessEntity(defenceClientId, randomUUID(), randomUUID(), randomUUID(), ZonedDateTime.now(ZoneId.of("UTC")));
    }

    private IdpcAccess createRandomIdpcAccessFor(final UUID defenceClientId, final UUID organisationId) {
        return createIdpcAccessEntity(defenceClientId, randomUUID(), randomUUID(), organisationId, ZonedDateTime.now(ZoneId.of("UTC")));
    }

    private IdpcAccess createIdpcAccessEntity(final UUID defenceClientId, final UUID idpcId, final UUID userId, final UUID orgId, ZonedDateTime timestamp) {
        return new IdpcAccess(randomUUID(), defenceClientId, idpcId, userId, orgId, timestamp);
    }
}