package uk.gov.moj.cpp.defence.persistence;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.time.LocalDate.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;

import uk.gov.justice.services.test.utils.persistence.HibernateTestEntityManagerProvider;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceCase;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class DefenceClientRepositoryTest {

    public static final String PTI_URN = "01AA1111111";
    public static final String PROSECUTING_AUTHORITY = "TFL";
    public static final String ORGANISATION_NAME = "organisation_name";

    @RegisterExtension
    static HibernateTestEntityManagerProvider hibernateTestEntityManagerProvider =
            new HibernateTestEntityManagerProvider("defence-test-persistence-unit");

    private DefenceClientRepository defenceClientRepository;

    private DefenceCaseRepository defenceCaseRepository;

    @BeforeEach
    void createRepositories() {
        defenceClientRepository = new DefenceClientRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceClientRepository);
        defenceCaseRepository = new DefenceCaseRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceCaseRepository);
    }

    @Test
    public void shouldFindDefenceClientByCriteriaWhenMatchExistsInDB() {


        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, FALSE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(defenceCase.getId());
        final DefenceClient defClient2 = getDefenceClient2(defenceCase.getId());

        defenceClientRepository.save(defClient1);
        final DefenceClient savedClient2 = defenceClientRepository.save(defClient2);
        defenceCaseRepository.save(defenceCase);

        final DefenceClient client = defenceClientRepository.findDefenceClientByCriteria(defClient1.getFirstName(), defClient1.getLastName(), defClient1.getDateOfBirth(), PTI_URN).get(0);
        assertThat("No Entity returned", client, notNullValue());
        assertThat("Unexpected ID value in retrieved DefenceClient Entity", client.getId(), is(defClient1.getId()) );
        assertThat("Unexpected FIRSTNAME value in retrieved DefenceClient Entity", client.getFirstName(), is(defClient1.getFirstName()));
        assertThat("Unexpected LASTNAME value in retrieved DefenceClient Entity", client.getLastName(), is(defClient1.getLastName()));
        assertThat("Unexpected DOB value in retrieved DefenceClient Entity", client.getDateOfBirth(), is(defClient1.getDateOfBirth()));
        assertThat("Unexpected Defendant Id value in retrieved DefenceClient Entity", client.getDefendantId(), is(defClient1.getDefendantId()));

        defenceClientRepository.refresh(savedClient2);
        assertThat("Unexpected isVisible value in saved DefenceClient Entity", savedClient2.getVisible(), is(true) );

        defenceClientRepository.remove(defClient1);
        defenceClientRepository.remove(savedClient2);
    }

    @Test
    public void shouldFindDefenceClientByCriteriaWhenUrnAndIsCivilSet() {
        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, TRUE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(defenceCase.getId());
        final DefenceClient defClient2 = getDefenceClient2(defenceCase.getId());

        defenceClientRepository.save(defClient1);
        defenceClientRepository.save(defClient2);
        defenceCaseRepository.save(defenceCase);

        final DefenceClient client = defenceClientRepository.findDefenceClientByCriteria(defClient1.getFirstName(), defClient1.getLastName(), defClient1.getDateOfBirth(), PTI_URN, TRUE).get(0);
        assertThat("No Entity returned", client, notNullValue());
        assertThat("Unexpected ID value in retrieved DefenceClient Entity", client.getId(), is(defClient1.getId()) );
        assertThat("Unexpected FIRSTNAME value in retrieved DefenceClient Entity", client.getFirstName(), is(defClient1.getFirstName()));
        assertThat("Unexpected LASTNAME value in retrieved DefenceClient Entity", client.getLastName(), is(defClient1.getLastName()));
        assertThat("Unexpected DOB value in retrieved DefenceClient Entity", client.getDateOfBirth(), is(defClient1.getDateOfBirth()));
        assertThat("Unexpected Defendant Id value in retrieved DefenceClient Entity", client.getDefendantId(), is(defClient1.getDefendantId()));

        defenceClientRepository.remove(defClient1);
        defenceClientRepository.remove(defClient2);
    }

    @Test
    public void shouldFindDefenceClientByCriteriaWhenUrnAndIsCivilSet_DobNoPresent() {
        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, TRUE, FALSE);
        final DefenceClient defClient1 = getDefenceClientWithDob(defenceCase.getId());
        final DefenceClient defClient2 = getDefenceClientWithDob(defenceCase.getId());

        defenceClientRepository.save(defClient1);
        defenceClientRepository.save(defClient2);
        defenceCaseRepository.save(defenceCase);

        final DefenceClient client = defenceClientRepository.findDefenceClientByCriteriaWithOutDob(defClient1.getFirstName(), defClient1.getLastName(), PTI_URN, TRUE).get(0);
        assertThat("No Entity returned", client, notNullValue());
        assertThat("Unexpected ID value in retrieved DefenceClient Entity", client.getId(), is(defClient1.getId()) );
        assertThat("Unexpected FIRSTNAME value in retrieved DefenceClient Entity", client.getFirstName(), is(defClient1.getFirstName()));
        assertThat("Unexpected LASTNAME value in retrieved DefenceClient Entity", client.getLastName(), is(defClient1.getLastName()));
        assertThat("Unexpected Defendant Id value in retrieved DefenceClient Entity", client.getDefendantId(), is(defClient1.getDefendantId()));

        defenceClientRepository.remove(defClient1);
        defenceClientRepository.remove(defClient2);
    }

    @Test
    public void shouldFindDefenceClientByCriteriaWhenIsCivilSet() {
        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, TRUE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(defenceCase.getId());
        final DefenceClient defClient2 = getDefenceClient2(defenceCase.getId());

        defenceClientRepository.save(defClient1);
        defenceClientRepository.save(defClient2);
        defenceCaseRepository.save(defenceCase);

        final DefenceClient client = defenceClientRepository.findDefenceClientByCriteria(defClient1.getFirstName(), defClient1.getLastName(), defClient1.getDateOfBirth(), TRUE).get(0);
        assertThat("No Entity returned", client, notNullValue());
        assertThat("Unexpected ID value in retrieved DefenceClient Entity", client.getId(), is(defClient1.getId()) );
        assertThat("Unexpected FIRSTNAME value in retrieved DefenceClient Entity", client.getFirstName(), is(defClient1.getFirstName()));
        assertThat("Unexpected LASTNAME value in retrieved DefenceClient Entity", client.getLastName(), is(defClient1.getLastName()));
        assertThat("Unexpected DOB value in retrieved DefenceClient Entity", client.getDateOfBirth(), is(defClient1.getDateOfBirth()));
        assertThat("Unexpected Defendant Id value in retrieved DefenceClient Entity", client.getDefendantId(), is(defClient1.getDefendantId()));

        defenceClientRepository.remove(defClient1);
        defenceClientRepository.remove(defClient2);
    }

    @Test
    public void shouldFindDefenceClientByCriteriaWhenIsCivilSetWithoutDob() {
        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, TRUE, FALSE);
        final DefenceClient defClient1 = getDefenceClientWithDob(defenceCase.getId());
        final DefenceClient defClient2 = getDefenceClientWithDob(defenceCase.getId());

        defenceClientRepository.save(defClient1);
        defenceClientRepository.save(defClient2);
        defenceCaseRepository.save(defenceCase);

        final DefenceClient client = defenceClientRepository.findDefenceClientByCriteriaWithOutDob(defClient1.getFirstName(), defClient1.getLastName(), TRUE).get(0);
        assertThat("No Entity returned", client, notNullValue());
        assertThat("Unexpected ID value in retrieved DefenceClient Entity", client.getId(), is(defClient1.getId()) );
        assertThat("Unexpected FIRSTNAME value in retrieved DefenceClient Entity", client.getFirstName(), is(defClient1.getFirstName()));
        assertThat("Unexpected LASTNAME value in retrieved DefenceClient Entity", client.getLastName(), is(defClient1.getLastName()));
        assertThat("Unexpected Defendant Id value in retrieved DefenceClient Entity", client.getDefendantId(), is(defClient1.getDefendantId()));

        defenceClientRepository.remove(defClient1);
        defenceClientRepository.remove(defClient2);
    }

    @Test
    public void shouldFindDefenceClientByCriteriaWhenOrganisationNameAndUrnAndIsCivilSet() {
        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, TRUE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(defenceCase.getId());
        defClient1.setOrganisationName(ORGANISATION_NAME);
        final DefenceClient defClient2 = getDefenceClient2(defenceCase.getId());
        defClient2.setOrganisationName(ORGANISATION_NAME);

        defenceClientRepository.save(defClient1);
        final DefenceClient savedClient2 = defenceClientRepository.save(defClient2);
        defenceCaseRepository.save(defenceCase);

        final DefenceClient client = defenceClientRepository.findDefenceClientByCriteria(defClient1.getOrganisationName(), PTI_URN, TRUE).get(0);
        assertThat("No Entity returned", client, notNullValue());
        assertThat("Unexpected ID value in retrieved DefenceClient Entity", client.getId(), is(defClient1.getId()) );
        assertThat("Unexpected FIRSTNAME value in retrieved DefenceClient Entity", client.getFirstName(), is(defClient1.getFirstName()));
        assertThat("Unexpected LASTNAME value in retrieved DefenceClient Entity", client.getLastName(), is(defClient1.getLastName()));
        assertThat("Unexpected DOB value in retrieved DefenceClient Entity", client.getDateOfBirth(), is(defClient1.getDateOfBirth()));
        assertThat("Unexpected Defendant Id value in retrieved DefenceClient Entity", client.getDefendantId(), is(defClient1.getDefendantId()));

        defenceClientRepository.refresh(savedClient2);
        assertThat("Unexpected isVisible value in saved DefenceClient Entity", savedClient2.getVisible(), is(true) );

        defenceClientRepository.remove(defClient1);
        defenceClientRepository.remove(savedClient2);
    }

    @Test
    public void shouldFindDefenceClientByCriteriaWhenOrganisationNameAndIsCivilSet() {
        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, TRUE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(defenceCase.getId());
        defClient1.setOrganisationName(ORGANISATION_NAME);
        final DefenceClient defClient2 = getDefenceClient2(defenceCase.getId());
        defClient2.setOrganisationName(ORGANISATION_NAME);

        defenceClientRepository.save(defClient1);
        final DefenceClient savedClient2 = defenceClientRepository.save(defClient2);
        defenceCaseRepository.save(defenceCase);

        final DefenceClient client = defenceClientRepository.findDefenceClientByCriteria(defClient1.getOrganisationName(), TRUE).get(0);
        assertThat("No Entity returned", client, notNullValue());
        assertThat("Unexpected ID value in retrieved DefenceClient Entity", client.getId(), is(defClient1.getId()) );
        assertThat("Unexpected FIRSTNAME value in retrieved DefenceClient Entity", client.getFirstName(), is(defClient1.getFirstName()));
        assertThat("Unexpected LASTNAME value in retrieved DefenceClient Entity", client.getLastName(), is(defClient1.getLastName()));
        assertThat("Unexpected DOB value in retrieved DefenceClient Entity", client.getDateOfBirth(), is(defClient1.getDateOfBirth()));
        assertThat("Unexpected Defendant Id value in retrieved DefenceClient Entity", client.getDefendantId(), is(defClient1.getDefendantId()));

        defenceClientRepository.refresh(savedClient2);
        assertThat("Unexpected isVisible value in saved DefenceClient Entity", savedClient2.getVisible(), is(true) );

        defenceClientRepository.remove(defClient1);
        defenceClientRepository.remove(savedClient2);
    }

    @Test
    public void shouldFindCasesAssociatedWithDefenceClientByPersonDefendantWhenIsCivilSet() {
        final UUID caseId = randomUUID();
        final DefenceCase defenceCase = new DefenceCase(caseId, PTI_URN, PROSECUTING_AUTHORITY, TRUE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(defenceCase.getId());
        final DefenceClient defClient2 = getDefenceClient2(defenceCase.getId());

        defenceClientRepository.save(defClient1);
        defenceClientRepository.save(defClient2);
        defenceCaseRepository.save(defenceCase);

        final List<UUID> caseIds = defenceClientRepository.findCasesAssociatedWithDefenceClientByPersonDefendant(defClient1.getFirstName(), defClient1.getLastName(), defClient1.getDateOfBirth(), true, false);
        assertThat(caseIds, hasSize(1));
        assertThat(caseIds.get(0), is(caseId) );

        defenceClientRepository.remove(defClient1);
        defenceClientRepository.remove(defClient2);
    }

    @Test
    public void shouldFindCasesAssociatedWithDefenceClientByPersonDefendantWhenIsCivilSet_WithoutDob() {
        final UUID caseId = randomUUID();
        final DefenceCase defenceCase = new DefenceCase(caseId, PTI_URN, PROSECUTING_AUTHORITY, TRUE, FALSE);
        final DefenceClient defClient1 = getDefenceClientWithDob(defenceCase.getId());
        final DefenceClient defClient2 = getDefenceClientWithDob(defenceCase.getId());

        defenceClientRepository.save(defClient1);
        defenceClientRepository.save(defClient2);
        defenceCaseRepository.save(defenceCase);

        final List<UUID> caseIds = defenceClientRepository.findCasesAssociatedWithDefenceClientByPersonDefendantWithoutDob(defClient1.getFirstName(), defClient1.getLastName(), true, false);
        assertThat(caseIds, hasSize(1));
        assertThat(caseIds.get(0), is(caseId) );

        defenceClientRepository.remove(defClient1);
        defenceClientRepository.remove(defClient2);
    }

    @Test
    public void shouldFindDefenceClientByCaseIdInDB() {


        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, FALSE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(defenceCase.getId());
        final DefenceClient defClient2 = getDefenceClient2(defenceCase.getId());

        defenceClientRepository.save(defClient1);
        defenceClientRepository.save(defClient2);
        defenceCaseRepository.save(defenceCase);

        final List<DefenceClient> client = defenceClientRepository.findByCaseId(defenceCase.getId());

        assertThat("Did not find two clients", client.size(), is(2) );
        defenceClientRepository.remove(defClient1);
        defenceClientRepository.remove(defClient2);
    }

    @Test
    public void shouldNotFindAnyDefenceClientWhenLastNameIsIncorrect() {

        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, FALSE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(defenceCase.getId());
        final DefenceClient defClient2 = getDefenceClient2(defenceCase.getId());

        defenceClientRepository.save(defClient1);
        defenceClientRepository.save(defClient2);
        defenceCaseRepository.save(defenceCase);

        final List<DefenceClient> defenceClientList = defenceClientRepository.findDefenceClientByCriteria("TEST ONE FIRST NAME", "NO_SUCH_LAST_NAME", of(1970, 5, 17), "01AA1111111");
        assertThat(defenceClientList, is(empty()));

        defenceClientRepository.remove(defClient1);
        defenceClientRepository.remove(defClient2);
    }

    @Test
    public void shouldNotFindAnyDefenceClientWhenUrnIsIncorrect() {

        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, FALSE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(defenceCase.getId());
        final DefenceClient defClient2 = getDefenceClient2(defenceCase.getId());

        defenceClientRepository.save(defClient1);
        defenceClientRepository.save(defClient2);
        defenceCaseRepository.save(defenceCase);

        final List<DefenceClient> defenceClientList = defenceClientRepository.findDefenceClientByCriteria("TEST ONE FIRST NAME", "TEST ONE LAST NAME", of(1970, 5, 17), "01ZZ0000000", Boolean.FALSE);
        assertThat(defenceClientList, is(empty()));

        defenceClientRepository.remove(defClient1);
        defenceClientRepository.remove(defClient2);
    }

    @Test
    public void shouldNotFindAnyDefenceClientWhenMarkedNotVisible() {

        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, FALSE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(defenceCase.getId());

        defenceClientRepository.save(defClient1);
        defenceCaseRepository.save(defenceCase);

        final DefenceClient client = defenceClientRepository.findDefenceClientByCriteria(defClient1.getFirstName(), defClient1.getLastName(), defClient1.getDateOfBirth(), PTI_URN).get(0);
        assertThat("No Entity returned", client, notNullValue());

        defClient1.setVisible(false);
        defenceClientRepository.save(defClient1);

        final List<DefenceClient> defenceClientList = defenceClientRepository.findDefenceClientByCriteria(defClient1.getFirstName(), defClient1.getLastName(), defClient1.getDateOfBirth(), PTI_URN);
        assertThat(defenceClientList.size(), is(0));

        defenceClientRepository.remove(defClient1);
    }

    @Test
    public void shouldNotCreateNewDefenceClientWhenMaterialIdAdded() {

        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, FALSE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(defenceCase.getId());

        //Create a new Defence Client
        defenceClientRepository.save(defClient1);
        defenceCaseRepository.save(defenceCase);

        final DefenceClient client = defenceClientRepository.findDefenceClientByCriteria(defClient1.getFirstName(), defClient1.getLastName(), defClient1.getDateOfBirth(), PTI_URN).get(0);
        assertThat("No Entity returned", client, notNullValue());

        //Add a material_id to existing Defence Client
        UUID idpcDetailsId = randomUUID();
        defClient1.setIdpcDetailsId(idpcDetailsId);
        defenceClientRepository.save(defClient1);

        List<DefenceClient> defenceClientList = defenceClientRepository.findDefenceClientByCriteria(defClient1.getFirstName(), defClient1.getLastName(), defClient1.getDateOfBirth(), PTI_URN, Boolean.FALSE);
        assertThat(defenceClientList.size(), is(1));
        assertThat(defenceClientList.get(0).getIdpcDetailsId(), is(idpcDetailsId));

        //Change material_id to existing Defence Client
        idpcDetailsId = randomUUID();
        defClient1.setIdpcDetailsId(idpcDetailsId);
        defenceClientRepository.save(defClient1);

        defenceClientList = defenceClientRepository.findDefenceClientByCriteria(defClient1.getFirstName(), defClient1.getLastName(), defClient1.getDateOfBirth(), PTI_URN, Boolean.FALSE);
        assertThat(defenceClientList.size(), is(1));
        assertThat(defenceClientList.get(0).getIdpcDetailsId(), is(idpcDetailsId));

        defenceClientRepository.remove(defClient1);
    }

    @Test
    public void shouldFindLastAssocatedOrganisationForDefendantId() {
        //Given
        UUID defendantId = randomUUID();
        UUID organisationId = randomUUID();
        UUID lastAssociatedOrganisation = saveDefenceClientAndLastAssociatedOrganisation(defendantId, organisationId);

        final DefenceClient optionalByDefendantId = defenceClientRepository.findOptionalByDefendantId(defendantId);
        //When
        UUID savedLastAssociatedOrganisation = optionalByDefendantId.getLastAssociatedOrganisation();
        //Then
        assertThat(optionalByDefendantId.isLockedByRepOrder().booleanValue(), is(false) );
        assertThat(lastAssociatedOrganisation, is(savedLastAssociatedOrganisation));
    }

    @Test
    public void shouldReturnNullWhenLastAssociatedOranisationNotPresent() {

        //Given
        UUID defendantId = randomUUID();
        generateDefenceClient(defendantId);

        //When
        UUID savedLastAssociatedOrganisation = defenceClientRepository.findOptionalByDefendantId(defendantId).getLastAssociatedOrganisation();

        //Then
        assertThat(savedLastAssociatedOrganisation, nullValue());
    }

    @Test
    public void shouldRetrievePersonDefendantId() {

        final UUID defendantId = randomUUID();
        final DefenceClient defenceClient = new DefenceClient(
                randomUUID(),
                "FIRST_NAME",
                "LAST_NAME",
                randomUUID(),
                LocalDate.now(),
                defendantId);
        defenceClientRepository.save(defenceClient);

        final List<UUID> defendantIdFromDB = defenceClientRepository.getPersonDefendant("FIRST_NAME", "LAST_NAME", LocalDate.now());

        assertThat(defendantIdFromDB.get(0), is(defendantId));
    }

    @Test
    public void shouldRetrievePersonDefendantIdWhenIsCivilSet() {
        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, TRUE, FALSE);

        final UUID defendantId = randomUUID();
        final DefenceClient defenceClient = new DefenceClient(
                randomUUID(),
                "FIRST_NAME",
                "LAST_NAME",
                defenceCase.getId(),
                LocalDate.now(),
                defendantId);

        defenceClientRepository.save(defenceClient);
        defenceCaseRepository.save(defenceCase);

        final List<UUID> defendantIdFromDB = defenceClientRepository.getPersonDefendant("FIRST_NAME", "LAST_NAME", LocalDate.now(), TRUE, FALSE);

        assertThat(defendantIdFromDB.get(0), is(defendantId));
    }

    @Test
    public void shouldRetrievePersonDefendantIdWhenIsCivilSetWithoutDob() {
        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, TRUE, FALSE);

        final UUID defendantId = randomUUID();
        final DefenceClient defenceClient = new DefenceClient(
                randomUUID(),
                "FIRST_NAME",
                "LAST_NAME",
                defenceCase.getId(),
                null,
                defendantId);

        defenceClientRepository.save(defenceClient);
        defenceCaseRepository.save(defenceCase);

        final List<UUID> defendantIdFromDB = defenceClientRepository.getPersonDefendantWithOutDob("FIRST_NAME", "LAST_NAME", TRUE, FALSE);

        assertThat(defendantIdFromDB.get(0), is(defendantId));
    }

    @Test
    public void shouldRetrieveOrganisationDefendantId() {

       final UUID defendantId = randomUUID();
       final DefenceClient defenceClient = new DefenceClient(
                randomUUID(),
                "ORGANISATION_NAME",
                randomUUID(),
                defendantId);
        defenceClientRepository.save(defenceClient);

        final List<UUID> defendantIdFromDB = defenceClientRepository.getOrganisationDefendant("ORGANISATION_NAME");

        assertThat(defendantIdFromDB.get(0), is(defendantId));
    }

    @Test
    public void shouldRetrieveOrganisationDefendantIdWhenIsCivilSet() {
        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, TRUE, FALSE);
        final UUID defendantId = randomUUID();
        final DefenceClient defenceClient = new DefenceClient(
                randomUUID(),
                "ORGANISATION_NAME",
                defenceCase.getId(),
                defendantId);
        defenceClientRepository.save(defenceClient);
        defenceCaseRepository.save(defenceCase);

        final List<UUID> defendantIdFromDB = defenceClientRepository.getOrganisationDefendant("ORGANISATION_NAME", TRUE, FALSE);

        assertThat(defendantIdFromDB.get(0), is(defendantId));
    }

    @Test
    public void shouldFindDefenceClientByCriteriaWithFirstNameLastNameAndDob() {
        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, FALSE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(defenceCase.getId());
        final DefenceClient defClient2 = getDefenceClient2(defenceCase.getId());

        defenceClientRepository.save(defClient1);
        defenceClientRepository.save(defClient2);
        defenceCaseRepository.save(defenceCase);

        final List<DefenceClient> clients = defenceClientRepository.findDefenceClientByCriteria(defClient1.getFirstName(), defClient1.getLastName(), defClient1.getDateOfBirth());

        assertThat(clients, hasSize(1));
        assertThat(clients.get(0).getId(), is(defClient1.getId()));
    }

    @Test
    public void shouldFindDefenceClientByCriteriaWithOrganisationNameAndUrn() {
        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, FALSE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(defenceCase.getId());
        defClient1.setOrganisationName(ORGANISATION_NAME);

        defenceClientRepository.save(defClient1);
        defenceCaseRepository.save(defenceCase);

        final List<DefenceClient> clients = defenceClientRepository.findDefenceClientByCriteria(ORGANISATION_NAME, PTI_URN);

        assertThat(clients, hasSize(1));
        assertThat(clients.get(0).getId(), is(defClient1.getId()));
    }

    @Test
    public void shouldFindDefenceClientByCriteriaWithOrganisationName() {
        final DefenceCase defenceCase = new DefenceCase(randomUUID(), PTI_URN, PROSECUTING_AUTHORITY, FALSE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(defenceCase.getId());
        defClient1.setOrganisationName(ORGANISATION_NAME);

        defenceClientRepository.save(defClient1);
        defenceCaseRepository.save(defenceCase);

        final List<DefenceClient> clients = defenceClientRepository.findDefenceClientByCriteria(ORGANISATION_NAME);

        assertThat(clients, hasSize(1));
        assertThat(clients.get(0).getId(), is(defClient1.getId()));
    }

    @Test
    public void shouldFindDefenceClientByCriteriaWithDefendantId() {
        final UUID defendantId = randomUUID();
        final DefenceClient defClient1 = new DefenceClient(randomUUID(), "FIRST", "LAST", randomUUID(), of(1980, 1, 1), defendantId);
        defenceClientRepository.save(defClient1);

        final DefenceClient client = defenceClientRepository.findDefenceClientByCriteria(defendantId);

        assertThat(client, notNullValue());
        assertThat(client.getId(), is(defClient1.getId()));
        assertThat(defenceClientRepository.findDefenceClientByCriteria(randomUUID()), nullValue());
    }

    @Test
    public void shouldFindOptionalByDefendantIdAndCaseId() {
        final UUID defendantId = randomUUID();
        final UUID caseId = randomUUID();
        final DefenceClient defClient1 = new DefenceClient(randomUUID(), "FIRST", "LAST", caseId, of(1980, 1, 1), defendantId);
        defenceClientRepository.save(defClient1);

        final DefenceClient client = defenceClientRepository.findOptionalByDefendantIdAndCaseId(defendantId, caseId);

        assertThat(client, notNullValue());
        assertThat(client.getId(), is(defClient1.getId()));
        assertThat(defenceClientRepository.findOptionalByDefendantIdAndCaseId(defendantId, randomUUID()), nullValue());
    }

    @Test
    public void shouldFindCasesAssociatedWithDefenceClientByPersonDefendant() {
        final UUID caseId = randomUUID();
        final DefenceCase defenceCase = new DefenceCase(caseId, PTI_URN, PROSECUTING_AUTHORITY, FALSE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(caseId);
        final DefenceClient defClient2 = getDefenceClient2(caseId);

        defenceClientRepository.save(defClient1);
        defenceClientRepository.save(defClient2);
        defenceCaseRepository.save(defenceCase);

        final List<UUID> caseIds = defenceClientRepository.findCasesAssociatedWithDefenceClientByPersonDefendant(defClient1.getFirstName(), defClient1.getLastName(), defClient1.getDateOfBirth());

        assertThat(caseIds, hasSize(1));
        assertThat(caseIds.get(0), is(caseId));
    }

    @Test
    public void shouldFindCasesAssociatedWithDefenceClientByOrganisationDefendant() {
        final UUID caseId = randomUUID();
        final DefenceCase defenceCase = new DefenceCase(caseId, PTI_URN, PROSECUTING_AUTHORITY, FALSE, FALSE);
        final DefenceClient defClient1 = getDefenceClient1(caseId);
        defClient1.setOrganisationName(ORGANISATION_NAME);

        defenceClientRepository.save(defClient1);
        defenceCaseRepository.save(defenceCase);

        final List<UUID> caseIds = defenceClientRepository.findCasesAssociatedWithDefenceClientByOrganisationDefendant(ORGANISATION_NAME);

        assertThat(caseIds, hasSize(1));
        assertThat(caseIds.get(0), is(caseId));
    }

    protected UUID saveDefenceClientAndLastAssociatedOrganisation(final UUID defendantId, final UUID organisationId) {
        DefenceClient defenceClient = generateDefenceClient(defendantId);
        UUID lastAssociatedOrganisation = randomUUID();
        defenceClient.setLastAssociatedOrganisation(lastAssociatedOrganisation);
        defenceClientRepository.save(defenceClient);
        return lastAssociatedOrganisation;
    }

    private DefenceClient generateDefenceClient(final UUID defendantId) {
        DefenceClient defenceClient = new DefenceClient(
                randomUUID(),
                "FIRSTNAME",
                "LASTNAME",
                randomUUID(),
                LocalDate.now(),
                defendantId);
        defenceClientRepository.save(defenceClient);
        return defenceClient;
    }

    private DefenceClient getDefenceClient1(final UUID caseId) {
        final String defenceClientOneFirstName = "TEST ONE FIRST NAME";
        final String defenceClientOneLastName = "TEST ONE LAST NAME";
        final LocalDate defenceClientOneDob = of(1985, 10, 21);

        return new DefenceClient(randomUUID(), defenceClientOneFirstName, defenceClientOneLastName, caseId, defenceClientOneDob, randomUUID());
    }

    private DefenceClient getDefenceClientWithDob(final UUID caseId) {
        final String defenceClientOneFirstName = "TEST ONE FIRST NAME";
        final String defenceClientOneLastName = "TEST ONE LAST NAME";

        return new DefenceClient(randomUUID(), defenceClientOneFirstName, defenceClientOneLastName, caseId, null, randomUUID());
    }

    private DefenceClient getDefenceClient2(final UUID caseId) {
        return new DefenceClient(randomUUID(), "TEST TWO FIRST NAME", "TEST TWO LAST NAME", caseId, of(1970, 5, 17), randomUUID());
    }

}
