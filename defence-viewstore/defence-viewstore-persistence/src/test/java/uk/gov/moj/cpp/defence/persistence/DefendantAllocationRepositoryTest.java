package uk.gov.moj.cpp.defence.persistence;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.justice.services.test.utils.persistence.HibernateTestEntityManagerProvider;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.persistence.entity.DefendantAllocation;
import uk.gov.moj.cpp.defence.persistence.entity.DefendantAllocationPlea;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.util.UUID.randomUUID;

public class DefendantAllocationRepositoryTest {

    @RegisterExtension
    static HibernateTestEntityManagerProvider hibernateTestEntityManagerProvider =
            new HibernateTestEntityManagerProvider("defence-test-persistence-unit");

    private DefendantAllocationRepository defendantAllocationRepository;

    private DefenceClientRepository defenceClientRepository;

    @BeforeEach
    void createRepositories() {
        defendantAllocationRepository = new DefendantAllocationRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defendantAllocationRepository);
        defenceClientRepository = new DefenceClientRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceClientRepository);
    }

    final UUID caseId= randomUUID();
    final UUID defendantId= randomUUID();
    final UUID offenceId1 = randomUUID();
    final UUID offenceId2 = randomUUID();

    @Test
    public void shouldAddPleaInDB() {
        DefendantAllocation defendantAllocation = createDefendantAllocation(defendantId);
        defendantAllocationRepository.save(defendantAllocation);

        DefendantAllocation found = defendantAllocationRepository.findBy(defendantAllocation.getId());
        List<DefendantAllocationPlea> pleas=found.getDefendantAllocationPleas();
        Assertions.assertSame(defendantAllocation.getId(), found.getId());
        Assertions.assertSame(defendantAllocation.getAcknowledgement(), found.getAcknowledgement());
        Assertions.assertSame(defendantAllocation.getDefendantId(), found.getDefendantId());
        Assertions.assertEquals(2,pleas.size());
        Assertions.assertEquals(pleas.get(0).getOffenceId(), offenceId1);
        Assertions.assertEquals(pleas.get(1).getOffenceId(), offenceId2);
    }

    @Test
    public void shouldUpdatePleaInDB() {
        //create
        DefendantAllocation defendantAllocation = createDefendantAllocation(defendantId);
        defendantAllocationRepository.save(defendantAllocation);

        DefendantAllocation found = defendantAllocationRepository.findBy(defendantAllocation.getId());
        Assertions.assertNull(found.getDefendantAllocationPleas().get(0).getPleaDate());

        //updated
        found.getDefendantAllocationPleas().get(0).setPleaDate(LocalDate.now());
        defendantAllocationRepository.save(found);

        //verify updated
        DefendantAllocation foundAfterUpdate = defendantAllocationRepository.findBy(defendantAllocation.getId());
        Assertions.assertNotNull(foundAfterUpdate.getDefendantAllocationPleas().get(0).getPleaDate());

        Assertions.assertSame(defendantAllocation.getId(), foundAfterUpdate.getId());
        Assertions.assertSame(defendantAllocation.getAcknowledgement(), foundAfterUpdate.getAcknowledgement());
        Assertions.assertSame(defendantAllocation.getDefendantId(), foundAfterUpdate.getDefendantId());

    }

    @Test
    public void shouldFindPleasByCaseId(){

        DefenceClient defenceClient = new DefenceClient();
        defenceClient.setDefendantId(defendantId);
        defenceClient.setCaseId(caseId);
        defenceClient.setId(randomUUID());
        defenceClientRepository.save(defenceClient);

        DefendantAllocation defendantAllocation = createDefendantAllocation(defendantId);
        defendantAllocationRepository.save(defendantAllocation);

        List<DefendantAllocation> pleasForCase = defendantAllocationRepository.findDefendantAllocationByCaseId(caseId);
        Assertions.assertNotNull(pleasForCase);
        Assertions.assertEquals(defendantId,pleasForCase.get(0).getDefendantId());
    }

    private DefendantAllocation createDefendantAllocation(UUID defendantId) {
        DefendantAllocation defendantAllocation = new DefendantAllocation();

        final List<DefendantAllocationPlea> defendantAllocationPleaList = new ArrayList<>();
        final LocalDate pleaDate= null;
        final String indicatedPlea="NOTGUILTY";
        final DefendantAllocationPlea allocationPlea = new DefendantAllocationPlea(offenceId1, pleaDate, indicatedPlea, defendantAllocation);

        defendantAllocationPleaList.add(allocationPlea);

        final DefendantAllocationPlea allocationPlea2 = new DefendantAllocationPlea(offenceId2, pleaDate, indicatedPlea, defendantAllocation);
        defendantAllocationPleaList.add(allocationPlea2);

        defendantAllocation.setId(randomUUID());
        defendantAllocation.setDefendantId(defendantId);
        defendantAllocation.setDefendantAllocationPleas(defendantAllocationPleaList);

        return defendantAllocation;
    }


}
