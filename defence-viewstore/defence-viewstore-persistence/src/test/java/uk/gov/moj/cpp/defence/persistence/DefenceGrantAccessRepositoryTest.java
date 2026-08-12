package uk.gov.moj.cpp.defence.persistence;

import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.defence.builder.DefenceClientBuilder.createDefenceClient;

import uk.gov.justice.services.test.utils.persistence.HibernateTestEntityManagerProvider;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceGrantAccess;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceUserDetails;
import uk.gov.moj.cpp.defence.persistence.entity.OrganisationDetails;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class DefenceGrantAccessRepositoryTest {

    @RegisterExtension
    static HibernateTestEntityManagerProvider hibernateTestEntityManagerProvider =
            new HibernateTestEntityManagerProvider("defence-test-persistence-unit");

    private DefenceGrantAccessRepository defenceGrantAccessRepository;

    private DefenceClientRepository defenceClientRepository;

    @BeforeEach
    void createRepositories() {
        defenceGrantAccessRepository = new DefenceGrantAccessRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceGrantAccessRepository);
        defenceClientRepository = new DefenceClientRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceClientRepository);
    }

    @Test
    public void shouldFindByDefenceGrantAccessId() {

        final DefenceClient defClient = createDefenceClient();

        defenceClientRepository.save(defClient);

        UUID userId = randomUUID();
        UUID grantorUserId = randomUUID();
        UUID organisationId = randomUUID();


        OrganisationDetails organisationDetails = new OrganisationDetails(randomUUID(), organisationId, "Test Ltd");
        DefenceGrantAccess defenceGrantAccess = new DefenceGrantAccess();
        defenceGrantAccess.setDefenceClient(defClient);
        defenceGrantAccess.setId(randomUUID());
        defenceGrantAccess.setStartDate(ZonedDateTime.now());
        defenceGrantAccess.setGranteeDefenceUserDetails(new DefenceUserDetails(randomUUID(), userId, "John", "Trackey"));
        defenceGrantAccess.setGrantorDefenceUserDetails(new DefenceUserDetails(randomUUID(), grantorUserId, "Tim", "Quick"));
        defenceGrantAccess.setGranteeOrganisationDetails(organisationDetails);


        defenceGrantAccessRepository.save(defenceGrantAccess);

        final DefenceGrantAccess defenceGrantAccessResult = defenceGrantAccessRepository.findBy(defenceGrantAccess.getId());

        assertThat(defenceGrantAccessResult, notNullValue());
        assertThat(defenceGrantAccessResult.getId(), is(defenceGrantAccess.getId()));
        assertThat(defenceGrantAccessResult.getGranteeDefenceUserDetails().getUserId(), is(defenceGrantAccess.getGranteeDefenceUserDetails().getUserId()));
        assertThat(defenceGrantAccessResult.getGrantorDefenceUserDetails().getUserId(), is(defenceGrantAccess.getGrantorDefenceUserDetails().getUserId()));
        assertThat(defenceGrantAccessResult.getGranteeOrganisationDetails().getOrganisationId(), is(defenceGrantAccess.getGranteeOrganisationDetails().getOrganisationId()));
    }

    @Test
    public void shouldFindByActiveDefenceClientId() {

        final DefenceClient defClient = createDefenceClient();
        defenceClientRepository.save(defClient);

        UUID granteeUserId = randomUUID();
        UUID grantorUserId = randomUUID();
        UUID organisationId = randomUUID();

        final DefenceGrantAccess defenceGrantAccess = getDefenceGrantAccess(defClient, granteeUserId, grantorUserId, organisationId, false);
        defenceGrantAccessRepository.save(defenceGrantAccess);

        final DefenceGrantAccess defenceGrantAccess1 = getDefenceGrantAccess(defClient, granteeUserId, grantorUserId, organisationId, true);
        defenceGrantAccessRepository.save(defenceGrantAccess1);

        UUID granteeUserId1 = randomUUID();
        UUID grantorUserId1 = randomUUID();
        UUID organisationId1 = randomUUID();

        final DefenceGrantAccess defenceGrantAccess2 = getDefenceGrantAccess(defClient, granteeUserId1, grantorUserId1, organisationId1, false);
        defenceGrantAccessRepository.save(defenceGrantAccess2);

        final List<DefenceGrantAccess> defenceGrantAccessResultList = defenceGrantAccessRepository.findByDefenceClient(defClient.getId());

        assertThat(nonNull(defenceGrantAccessResultList) && !defenceGrantAccessResultList.isEmpty(), is(true));

        DefenceGrantAccess defenceGrantAccessResult = defenceGrantAccessResultList.get(0);
        assertThat(defenceGrantAccessResult.getId(), is(defenceGrantAccess.getId()));
        assertThat(defenceGrantAccessResult.getGranteeDefenceUserDetails().getUserId(), is(defenceGrantAccess.getGranteeDefenceUserDetails().getUserId()));
        assertThat(defenceGrantAccessResult.getGrantorDefenceUserDetails().getUserId(), is(defenceGrantAccess.getGrantorDefenceUserDetails().getUserId()));
        assertThat(defenceGrantAccessResult.getGranteeOrganisationDetails().getOrganisationId(), is(defenceGrantAccess.getGranteeOrganisationDetails().getOrganisationId()));
    }

    @Test
    public void shouldFindByCaseIdAndGranteeUserId() {

        final DefenceClient defClient = createDefenceClient();
        defenceClientRepository.save(defClient);

        UUID granteeUserId = randomUUID();
        UUID grantorUserId = randomUUID();
        UUID organisationId = randomUUID();

        final DefenceGrantAccess defenceGrantAccess = getDefenceGrantAccess(defClient, granteeUserId, grantorUserId, organisationId, false);
        defenceGrantAccessRepository.save(defenceGrantAccess);

        final DefenceGrantAccess defenceGrantAccess1 = getDefenceGrantAccess(defClient, granteeUserId, grantorUserId, organisationId, true);
        defenceGrantAccessRepository.save(defenceGrantAccess1);

        UUID granteeUserId1 = randomUUID();
        UUID grantorUserId1 = randomUUID();
        UUID organisationId1 = randomUUID();

        final DefenceGrantAccess defenceGrantAccess2 = getDefenceGrantAccess(defClient, granteeUserId1, grantorUserId1, organisationId1, false);
        defenceGrantAccessRepository.save(defenceGrantAccess2);

        final List<DefenceGrantAccess> defenceGrantAccessResults = defenceGrantAccessRepository.findByGranteeAndCaseId(defClient.getCaseId(), granteeUserId);

        assertThat(defenceGrantAccessResults, notNullValue());
        assertThat(defenceGrantAccessResults.get(0).getId(), is(defenceGrantAccess.getId()));
        assertThat(defenceGrantAccessResults.get(0).getGranteeDefenceUserDetails().getUserId(), is(defenceGrantAccess.getGranteeDefenceUserDetails().getUserId()));
        assertThat(defenceGrantAccessResults.get(0).getGrantorDefenceUserDetails().getUserId(), is(defenceGrantAccess.getGrantorDefenceUserDetails().getUserId()));
        assertThat(defenceGrantAccessResults.get(0).getGranteeOrganisationDetails().getOrganisationId(), is(defenceGrantAccess.getGranteeOrganisationDetails().getOrganisationId()));
    }

    @Test
    public void shouldFindByDefenceClientAndUserId() {

        final DefenceClient defClient = createDefenceClient();
        defenceClientRepository.save(defClient);

        final UUID granteeUserId = randomUUID();
        final UUID grantorUserId = randomUUID();
        final UUID organisationId = randomUUID();

        final DefenceGrantAccess defenceGrantAccess = getDefenceGrantAccess(defClient, granteeUserId, grantorUserId, organisationId, false);
        defenceGrantAccessRepository.save(defenceGrantAccess);

        final DefenceGrantAccess otherGrantAccess = getDefenceGrantAccess(defClient, randomUUID(), grantorUserId, organisationId, false);
        defenceGrantAccessRepository.save(otherGrantAccess);

        final DefenceGrantAccess result = defenceGrantAccessRepository.findByDefenceClient(defClient.getId(), granteeUserId);

        assertThat(result, notNullValue());
        assertThat(result.getId(), is(defenceGrantAccess.getId()));
        assertThat(result.getGranteeDefenceUserDetails().getUserId(), is(granteeUserId));
    }

    @Test
    public void shouldReturnNullOnFindByDefenceClientAndUserIdWhenNoMatch() {
        final DefenceClient defClient = createDefenceClient();
        defenceClientRepository.save(defClient);

        final DefenceGrantAccess result = defenceGrantAccessRepository.findByDefenceClient(defClient.getId(), randomUUID());

        assertThat(result, is(nullValue()));
    }

    private DefenceGrantAccess getDefenceGrantAccess(final DefenceClient defClient, final UUID granteeUserId, final UUID grantorUserId, final UUID organisationId, final boolean remove) {
        final DefenceGrantAccess defenceGrantAccess = new DefenceGrantAccess();
        defenceGrantAccess.setDefenceClient(defClient);
        defenceGrantAccess.setId(randomUUID());
        defenceGrantAccess.setStartDate(ZonedDateTime.now());
        defenceGrantAccess.setGranteeDefenceUserDetails(new DefenceUserDetails(randomUUID(), granteeUserId, "John", "Trackey"));
        defenceGrantAccess.setGrantorDefenceUserDetails(new DefenceUserDetails(randomUUID(), grantorUserId, "Tim", "Quick"));
        defenceGrantAccess.setGranteeOrganisationDetails(new OrganisationDetails(randomUUID(), organisationId, "Test Ltd"));
        defenceGrantAccess.setRemoved(remove);
        return defenceGrantAccess;
    }

}
