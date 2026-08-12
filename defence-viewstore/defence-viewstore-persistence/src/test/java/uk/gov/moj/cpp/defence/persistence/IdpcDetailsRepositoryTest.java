package uk.gov.moj.cpp.defence.persistence;

import static java.time.LocalDate.now;
import static java.time.LocalDate.of;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import uk.gov.justice.services.test.utils.persistence.HibernateTestEntityManagerProvider;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.persistence.entity.IdpcDetails;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class IdpcDetailsRepositoryTest {

    @RegisterExtension
    static HibernateTestEntityManagerProvider hibernateTestEntityManagerProvider =
            new HibernateTestEntityManagerProvider("defence-test-persistence-unit");

    private IdpcDetailsRepository idpcRepository;

    private DefenceClientRepository defenceClientRepository;

    @BeforeEach
    void createRepositories() {
        idpcRepository = new IdpcDetailsRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(idpcRepository);
        defenceClientRepository = new DefenceClientRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceClientRepository);
    }

    @Test
    public void findIdpcDetailsForDefenceClientId() {

        uk.gov.moj.cpp.defence.IdpcDetails idpcDetailsVo = uk.gov.moj.cpp.defence.IdpcDetails.idpcDetails()
                .withPublishedDate(now())
                .withSize("2.7Mb")
                .withPageCount(20)
                .withMaterialId(randomUUID())
                .build();

        final IdpcDetails expectedIdpcDetails = new IdpcDetails(randomUUID(), randomUUID(), idpcDetailsVo, "SURNAME firstname 11DD0304617 Initial Details Pros Case");
        expectedIdpcDetails.setPageCount(20);
        expectedIdpcDetails.setPublishedDate(now());

        final IdpcDetails savedIdpcDetails = idpcRepository.save(expectedIdpcDetails);

        final IdpcDetails actualIdpcDetails = idpcRepository.findIdpcDetailsForDefenceClient(savedIdpcDetails.getDefenceClientId());

        assertEquals(savedIdpcDetails, actualIdpcDetails);

        final IdpcDetails idpcDetailsById = idpcRepository.findBy(savedIdpcDetails.getId());

        assertEquals(savedIdpcDetails, idpcDetailsById);
    }

    @Test
    public void shouldReturnNullWhenDefenceClientNotKnown() {
        final IdpcDetails actualIdpcDetails = idpcRepository.findOptionalByDefenceClientId(UUID.randomUUID());
        assertEquals(null, actualIdpcDetails);
    }

    @Test
    public void findIdpcDetailsForDefendantId() {
        DefenceClient defenceClient = getDefenceClient1(randomUUID());
        defenceClientRepository.save(defenceClient);

        uk.gov.moj.cpp.defence.IdpcDetails idpcDetailsVo = uk.gov.moj.cpp.defence.IdpcDetails.idpcDetails()
                .withPublishedDate(now())
                .withSize("2.7Mb")
                .withPageCount(20)
                .withMaterialId(randomUUID())
                .build();

        final IdpcDetails expectedIdpcDetails = new IdpcDetails(randomUUID(), defenceClient.getId(), idpcDetailsVo, "SURNAME firstname 11DD0304617 Initial Details Pros Case");
        expectedIdpcDetails.setPageCount(20);
        expectedIdpcDetails.setPublishedDate(now());

        final IdpcDetails savedIdpcDetails = idpcRepository.save(expectedIdpcDetails);

        final IdpcDetails actualIdpcDetails = idpcRepository.findIdpcDetailsForDefendantId(defenceClient.getDefendantId());

        assertEquals(savedIdpcDetails, actualIdpcDetails);

        final IdpcDetails idpcDetailsById = idpcRepository.findBy(savedIdpcDetails.getId());

        assertEquals(savedIdpcDetails, idpcDetailsById);
    }

    @Test
    public void findIdpcDetailsForDefendantIdNotFound() {
        DefenceClient defenceClient = getDefenceClient1(randomUUID());
        defenceClientRepository.save(defenceClient);

        defenceClient.setDefendantId(null);
        IdpcDetails actualIdpcDetails = null;
        actualIdpcDetails = idpcRepository.findIdpcDetailsForDefendantId(defenceClient.getDefendantId());
        assertNull(actualIdpcDetails);
    }

    private DefenceClient getDefenceClient1(final UUID caseId) {
        final String defenceClientOneFirstName = "TEST ONE FIRST NAME";
        final String defenceClientOneLastName = "TEST ONE LAST NAME";
        final LocalDate defenceClientOneDob = of(1985, 10, 21);

        return new DefenceClient(randomUUID(), defenceClientOneFirstName, defenceClientOneLastName, caseId, defenceClientOneDob, randomUUID());
    }
}