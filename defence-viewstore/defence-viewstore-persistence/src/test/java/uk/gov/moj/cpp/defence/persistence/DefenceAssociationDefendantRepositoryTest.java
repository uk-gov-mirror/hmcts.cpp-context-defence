package uk.gov.moj.cpp.defence.persistence;

import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import uk.gov.justice.services.test.utils.persistence.HibernateTestEntityManagerProvider;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociation;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociationDefendant;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class DefenceAssociationDefendantRepositoryTest {

    @RegisterExtension
    static HibernateTestEntityManagerProvider hibernateTestEntityManagerProvider =
            new HibernateTestEntityManagerProvider("defence-test-persistence-unit");

    private DefenceAssociationDefendantRepository defenceAssociationDefendantRepository;

    @BeforeEach
    public void createRepositoryAndClearData() {
        defenceAssociationDefendantRepository = new DefenceAssociationDefendantRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceAssociationDefendantRepository);
        defenceAssociationDefendantRepository.findAll().forEach(defenceAssociationDefendantRepository::remove);
        defenceAssociationDefendantRepository.flush();
    }

    @Test
    public void shouldFindDefendantWithFetchedAssociationsByDefendantId() {
        final UUID defendantId = randomUUID();
        final DefenceAssociationDefendant defendant = new DefenceAssociationDefendant();
        defendant.setDefendantId(defendantId);

        final DefenceAssociation association = new DefenceAssociation();
        association.setId(randomUUID());
        association.setUserId(randomUUID());
        association.setOrgId(randomUUID());
        association.setDefenceAssociationDefendant(defendant);
        defendant.setDefenceAssociations(singletonList(association));

        defenceAssociationDefendantRepository.save(defendant);
        defenceAssociationDefendantRepository.flush();

        final DefenceAssociationDefendant found = defenceAssociationDefendantRepository.findOptionalByDefendantId(defendantId);

        assertThat(found, is(notNullValue()));
        assertThat(found.getDefendantId(), is(defendantId));
        assertThat(found.getDefenceAssociations(), hasSize(1));
        assertThat(found.getDefenceAssociations().get(0).getId(), is(association.getId()));
    }

    @Test
    public void shouldReturnNullWhenDefendantNotFound() {
        assertThat(defenceAssociationDefendantRepository.findOptionalByDefendantId(randomUUID()), is(nullValue()));
    }
}
