package uk.gov.moj.cpp.defence.persistence;

import static com.google.common.collect.ImmutableList.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import static java.time.LocalDate.of;

import uk.gov.justice.services.test.utils.persistence.HibernateTestEntityManagerProvider;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociation;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociationDefendant;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class DefenceAssociationRepositoryTest {

    private static final String LAA_CONTRACT_NUMBER = "l1";

    @RegisterExtension
    static HibernateTestEntityManagerProvider hibernateTestEntityManagerProvider =
            new HibernateTestEntityManagerProvider("defence-test-persistence-unit");

    private DefenceAssociationRepository defenceAssociationRepository;

    private DefenceAssociationDefendantRepository defenceAssociationDefendantRepository;

    private DefenceClientRepository defenceClientRepository;

    @BeforeEach
    void createRepositories() {
        defenceAssociationRepository = new DefenceAssociationRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceAssociationRepository);
        defenceAssociationDefendantRepository = new DefenceAssociationDefendantRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceAssociationDefendantRepository);
        defenceClientRepository = new DefenceClientRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceClientRepository);
    }

    @Test
    public void shouldFindByOrganisationIdAndCaseId() {
        final UUID organisationId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();

        final DefenceAssociationDefendant defenceAssociationDefendant = new DefenceAssociationDefendant();
        defenceAssociationDefendant.setDefendantId(defendantId);
        defenceAssociationDefendantRepository.save(defenceAssociationDefendant);

        final DefenceAssociation defenceAssociation = new DefenceAssociation();
        defenceAssociation.setId(randomUUID());
        defenceAssociation.setOrgId(organisationId);
        defenceAssociation.setDefenceAssociationDefendant(defenceAssociationDefendant);
        defenceAssociationRepository.save(defenceAssociation);

        final DefenceClient defenceClient = new DefenceClient(randomUUID(), "FIRST", "LAST", caseId, of(1980, 1, 1), defendantId);
        defenceClientRepository.save(defenceClient);

        final List<DefenceAssociation> result = defenceAssociationRepository.findByOrganisationIdAndCaseId(organisationId, caseId);

        assertThat(result.size(), is(1));
        assertThat(result.get(0).getId(), is(defenceAssociation.getId()));
    }

    @Test
    public void shouldReturnEmptyOnFindByOrganisationIdAndCaseIdWhenNoMatch() {
        final List<DefenceAssociation> result = defenceAssociationRepository.findByOrganisationIdAndCaseId(randomUUID(), randomUUID());
        assertThat(result.size(), is(0));
    }

    @Test
    public void shouldFindDefenceAssociationsByLaaContract() {
        DefenceAssociation defenceAssociation = createDefenceAssociation(LAA_CONTRACT_NUMBER);

        final List<DefenceAssociation> foundDefenceAssociations = defenceAssociationRepository.findByLAAContractNumber(of(LAA_CONTRACT_NUMBER));

        assertThat(foundDefenceAssociations.get(0), is(defenceAssociation));
    }

    @Test
    public void shouldFindDefenceAssociationsByLaaContracts() {
        final String laaContractNumber1 = LAA_CONTRACT_NUMBER + 1;
        final DefenceAssociation defenceAssociation1 = createDefenceAssociation(laaContractNumber1);

        final String laaContractNumber2 = LAA_CONTRACT_NUMBER + 2;
        final DefenceAssociation defenceAssociation2 = createDefenceAssociation(laaContractNumber2);

        final String laaContractNumber3 = LAA_CONTRACT_NUMBER + 3;
        final DefenceAssociation defenceAssociation3 = createDefenceAssociation(laaContractNumber3);

        final List<DefenceAssociation> foundDefenceAssociation1 = defenceAssociationRepository.findByLAAContractNumber(of(laaContractNumber1));
        assertThat(foundDefenceAssociation1, is(of(defenceAssociation1)));

        final List<DefenceAssociation> foundDefenceAssociationAndMore = defenceAssociationRepository.findByLAAContractNumber(of(laaContractNumber1, "some random laa"));
        assertThat(foundDefenceAssociationAndMore, is(of(defenceAssociation1)));

        final List<DefenceAssociation> foundDefenceAssociation2and3 = defenceAssociationRepository.findByLAAContractNumber(of(laaContractNumber2, laaContractNumber3));
        assertThat(foundDefenceAssociation2and3, is(of(defenceAssociation2, defenceAssociation3)));

    }

    @Test
    public void shouldReturnEmptyListOnFindDefenceAssociationsByLaaContractWhenNotFound() {
        final List<DefenceAssociation> foundDefenceAssociations = defenceAssociationRepository.findByLAAContractNumber(of(LAA_CONTRACT_NUMBER));
        assertThat(foundDefenceAssociations.size(), is(0));
    }

    @Test
    public void shouldFindEntitiesByUserIdWhenAssociationsArePresent() {
        final UUID userId = randomUUID();
        final UUID defendantId1 = randomUUID();
        final UUID defendantId2 = randomUUID();
        final ZonedDateTime currentDate = ZonedDateTime.now();
        createDefenceAssociation(userId, defendantId1, null, null);
        createDefenceAssociation(userId, defendantId2, null, null);
        final List<DefenceAssociation> result = defenceAssociationRepository.findByUserIdAndCurrentDate(userId, currentDate);
        assertThat(result.size(), is(2));
    }

    @Test
    public void shouldNotFindEntitiesByUserIdWhenAssociationsAreBeforeStartDate() {
        final UUID userId = randomUUID();
        final UUID defendantId1 = randomUUID();
        final UUID defendantId2 = randomUUID();
        final ZonedDateTime currentDate = ZonedDateTime.now();
        final ZonedDateTime startDate = ZonedDateTime.now().plusDays(1);
        final ZonedDateTime endDate = ZonedDateTime.now().plusDays(10);
        createDefenceAssociation(userId, defendantId1, startDate, endDate);
        createDefenceAssociation(userId, defendantId2, startDate, endDate);
        final List<DefenceAssociation> result = defenceAssociationRepository.findByUserIdAndCurrentDate(userId, currentDate);
        assertThat(result.size(), is(0));
    }

    @Test
    public void shouldNotFindEntitiesByUserIdWhenAssociationsAreAfterEndDate() {
        final UUID userId = randomUUID();
        final UUID defendantId1 = randomUUID();
        final UUID defendantId2 = randomUUID();
        final ZonedDateTime currentDate = ZonedDateTime.now();
        final ZonedDateTime startDate = ZonedDateTime.now().minusDays(10);
        final ZonedDateTime endDate = ZonedDateTime.now().minusDays(1);
        createDefenceAssociation(userId, defendantId1, startDate, endDate);
        createDefenceAssociation(userId, defendantId2, startDate, endDate);
        final List<DefenceAssociation> result = defenceAssociationRepository.findByUserIdAndCurrentDate(userId, currentDate);
        assertThat(result.size(), is(0));
    }

    @Test
    public void shouldNotFindEntitiesByUserIdWhenAssociationsAreBetweenStartDateAndEndDate() {
        final UUID userId = randomUUID();
        final UUID defendantId1 = randomUUID();
        final UUID defendantId2 = randomUUID();
        final ZonedDateTime currentDate = ZonedDateTime.now();
        final ZonedDateTime startDate = ZonedDateTime.now().minusDays(5);
        final ZonedDateTime endDate = ZonedDateTime.now().plusDays(1);
        createDefenceAssociation(userId, defendantId1, startDate, endDate);
        createDefenceAssociation(userId, defendantId2, startDate, endDate);
        final List<DefenceAssociation> result = defenceAssociationRepository.findByUserIdAndCurrentDate(userId, currentDate);
        assertThat(result.size(), is(2));
    }

    private DefenceAssociation createDefenceAssociation(final String laaContractNumber) {
        UUID defendantId = randomUUID();
        final DefenceAssociationDefendant defenceAssociationDefendant = new DefenceAssociationDefendant();
        defenceAssociationDefendant.setDefendantId(defendantId);
        defenceAssociationDefendantRepository.save(defenceAssociationDefendant);

        UUID defenceAssociationId = randomUUID();
        DefenceAssociation defenceAssociation = new DefenceAssociation();
        defenceAssociation.setId(defenceAssociationId);
        defenceAssociation.setLaaContractNumber(laaContractNumber);

        defenceAssociation.setDefenceAssociationDefendant(defenceAssociationDefendant);
        return defenceAssociationRepository.save(defenceAssociation);
    }

    private DefenceAssociation createDefenceAssociation(final UUID userId, final UUID defendantId, final ZonedDateTime startDate, final ZonedDateTime endDate) {

        final DefenceAssociationDefendant defenceAssociationDefendant = new DefenceAssociationDefendant();
        defenceAssociationDefendant.setDefendantId(defendantId);
        defenceAssociationDefendantRepository.save(defenceAssociationDefendant);

        final DefenceAssociation defenceAssociation = new DefenceAssociation();
        defenceAssociation.setUserId(userId);
        defenceAssociation.setId(randomUUID());
        defenceAssociation.setOrgId(randomUUID());
        defenceAssociation.setDescription("description");
        defenceAssociation.setStartDate(startDate);
        defenceAssociation.setEndDate(endDate);
        defenceAssociation.setDefenceAssociationDefendant(defenceAssociationDefendant);
        defenceAssociationRepository.save(defenceAssociation);
        return defenceAssociation;
    }
}
