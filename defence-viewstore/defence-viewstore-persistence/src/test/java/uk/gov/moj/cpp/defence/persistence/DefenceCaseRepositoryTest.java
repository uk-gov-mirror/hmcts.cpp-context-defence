package uk.gov.moj.cpp.defence.persistence;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import uk.gov.justice.services.test.utils.persistence.HibernateTestEntityManagerProvider;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceCase;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class DefenceCaseRepositoryTest {

    @RegisterExtension
    static HibernateTestEntityManagerProvider hibernateTestEntityManagerProvider =
            new HibernateTestEntityManagerProvider("defence-test-persistence-unit");

    private DefenceCaseRepository defenceCaseRepository;

    @BeforeEach
    void createRepository() {
        defenceCaseRepository = new DefenceCaseRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceCaseRepository);
    }

    @Test
    public void findIdpcDetailsForDefenceClientId() {

        UUID caseId = randomUUID();
        String urn = "Test URN";

        DefenceCase defenceCase = new DefenceCase();
        defenceCase.setId(caseId);
        defenceCase.setUrn(urn);

        final DefenceCase persistedCase = defenceCaseRepository.save(defenceCase);

        DefenceCase savedCase = defenceCaseRepository.findBy(caseId);
        assertEquals(persistedCase, savedCase);

        savedCase = defenceCaseRepository.findOptionalByUrn(persistedCase.getUrn());
        assertEquals(persistedCase, savedCase);
    }

    @Test
    public void shouldReturnNullWhenDefenceClientNotKnown() {
        DefenceCase random = defenceCaseRepository.findBy(UUID.randomUUID());
        assertNull(random);

        random = defenceCaseRepository.findOptionalByUrn("random");
        assertNull(random);

    }
}
