package uk.gov.moj.cpp.defence.persistence;

import uk.gov.justice.services.test.utils.persistence.HibernateTestEntityManagerProvider;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociation;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociationDefendant;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class DefenceAssociationRepositoryQueryTest {

    @RegisterExtension
    static HibernateTestEntityManagerProvider hibernateTestEntityManagerProvider =
            new HibernateTestEntityManagerProvider("defence-test-persistence-unit");

    private DefenceAssociationRepository defenceAssociationRepository;
    private DefenceAssociationDefendantRepository defenceAssociationDefendantRepository;

    @BeforeEach
    void createRepositories() {
        defenceAssociationRepository = new DefenceAssociationRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceAssociationRepository);
        defenceAssociationDefendantRepository = new DefenceAssociationDefendantRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceAssociationDefendantRepository);
    }

    @Test
    public void testFindByUserIdAndCurrentDate(){

        UUID userId=UUID.randomUUID();
        DefenceAssociation defenceAssociation=new DefenceAssociation();
        defenceAssociation.setId(UUID.randomUUID());
        defenceAssociation.setUserId(userId);
        ZonedDateTime dateTIme=ZonedDateTime.now();
        defenceAssociation.setStartDate(dateTIme);
        defenceAssociation.setEndDate(dateTIme);
        DefenceAssociationDefendant defenceAssociationDefendant=new DefenceAssociationDefendant();
        defenceAssociationDefendant.setDefendantId(UUID.randomUUID());
        defenceAssociationDefendant.getDefenceAssociations().add(defenceAssociation);
        defenceAssociation.setDefenceAssociationDefendant(defenceAssociationDefendant);
        DefenceAssociationDefendant dbEntity=defenceAssociationDefendantRepository.save(defenceAssociationDefendant);
        Assertions.assertNotNull(dbEntity);
        Assertions.assertEquals(1,dbEntity.getDefenceAssociations().size());

        final List<DefenceAssociation> defenceAssociationList = defenceAssociationRepository.findByUserIdAndCurrentDate(userId, dateTIme);

        if(!defenceAssociationList.isEmpty()) {
            DefenceAssociation defenceAssociationDbEntity = defenceAssociationList.get(0);
            Assertions.assertNotNull(defenceAssociationDbEntity);
            Assertions.assertEquals(userId, defenceAssociationDbEntity.getUserId());
        }
    }
}
