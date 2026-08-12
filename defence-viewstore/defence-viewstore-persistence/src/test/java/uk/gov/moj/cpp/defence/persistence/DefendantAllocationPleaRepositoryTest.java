package uk.gov.moj.cpp.defence.persistence;

import static java.time.LocalDate.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import uk.gov.justice.services.test.utils.persistence.HibernateTestEntityManagerProvider;
import uk.gov.moj.cpp.defence.persistence.entity.DefendantAllocation;
import uk.gov.moj.cpp.defence.persistence.entity.DefendantAllocationPlea;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class DefendantAllocationPleaRepositoryTest {

    @RegisterExtension
    static HibernateTestEntityManagerProvider hibernateTestEntityManagerProvider =
            new HibernateTestEntityManagerProvider("defence-test-persistence-unit");

    private DefendantAllocationPleaRepository defendantAllocationPleaRepository;

    private DefendantAllocationRepository defendantAllocationRepository;

    @BeforeEach
    public void createRepositoriesAndClearData() {
        defendantAllocationPleaRepository = new DefendantAllocationPleaRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defendantAllocationPleaRepository);
        defendantAllocationRepository = new DefendantAllocationRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defendantAllocationRepository);
        defendantAllocationPleaRepository.findAll().forEach(defendantAllocationPleaRepository::remove);
        defendantAllocationRepository.findAll().forEach(defendantAllocationRepository::remove);
        defendantAllocationPleaRepository.flush();
    }

    @Test
    public void shouldSaveAndFindDefendantAllocationPleaByOffenceId() {
        final DefendantAllocation defendantAllocation = new DefendantAllocation();
        defendantAllocation.setId(randomUUID());
        defendantAllocation.setDefendantId(randomUUID());
        defendantAllocationRepository.save(defendantAllocation);

        final UUID offenceId = randomUUID();
        final LocalDate pleaDate = of(2026, 8, 18);
        final DefendantAllocationPlea plea = new DefendantAllocationPlea(offenceId, pleaDate, "GUILTY", defendantAllocation);
        defendantAllocationPleaRepository.save(plea);
        defendantAllocationPleaRepository.flush();

        final DefendantAllocationPlea found = defendantAllocationPleaRepository.findBy(offenceId);

        assertThat(found, is(notNullValue()));
        assertThat(found.getOffenceId(), is(offenceId));
        assertThat(found.getPleaDate(), is(pleaDate));
        assertThat(found.getIndicatedPlea(), is("GUILTY"));
        assertThat(found.getDefendantAllocation().getId(), is(defendantAllocation.getId()));
    }

    @Test
    public void shouldReturnNullWhenDefendantAllocationPleaNotFound() {
        assertThat(defendantAllocationPleaRepository.findBy(randomUUID()), is(nullValue()));
    }
}
