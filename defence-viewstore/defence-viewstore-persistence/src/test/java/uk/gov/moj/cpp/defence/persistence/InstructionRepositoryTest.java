package uk.gov.moj.cpp.defence.persistence;

import static java.time.LocalDate.now;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.defence.builder.DefenceClientBuilder.createDefenceClient;

import uk.gov.justice.services.test.utils.persistence.HibernateTestEntityManagerProvider;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.persistence.entity.Instruction;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class InstructionRepositoryTest {

    @RegisterExtension
    static HibernateTestEntityManagerProvider hibernateTestEntityManagerProvider =
            new HibernateTestEntityManagerProvider("defence-test-persistence-unit");

    private InstructionRepository instructionRepository;

    private DefenceClientRepository defenceClientRepository;

    @BeforeEach
    void createRepositories() {
        instructionRepository = new InstructionRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(instructionRepository);
        defenceClientRepository = new DefenceClientRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(defenceClientRepository);
    }

    @Test
    public void findAllegationsByDefenceClientId() {

        final DefenceClient defClient = createDefenceClient();

        Instruction instruction = createInstruction(defClient);
        defClient.getInstructionHistory().add(instruction);
        defenceClientRepository.save(defClient);

        final List<Instruction> instructionHistory = instructionRepository.findInstructionsByCriteria(defClient.getId(), instruction.getUserId());

        assertThat(instructionHistory.size(), is(1));
        final Instruction savedInstruction = instructionHistory.get(0);

        assertThat(savedInstruction.getDefenceClient().getId(), is(defClient.getId()));
        assertThat(savedInstruction.getId(), is(instruction.getId()));
        assertThat(savedInstruction.getUserId(), is(instruction.getUserId()));
        assertThat(savedInstruction.getOrganisationId(), is(instruction.getOrganisationId()));
        assertThat(savedInstruction.getInstructionDate(), is(instruction.getInstructionDate()));
    }


    @Test
    public void shouldFindNumberOfInstructionsForUserForDefenceClient() {

        final DefenceClient defClient = createDefenceClient();

        final Instruction instruction = createInstruction(defClient);
        final Instruction sameUserInstruction = new Instruction(randomUUID(), instruction.getUserId(), randomUUID(), defClient, now());
        defClient.getInstructionHistory().add(instruction);
        defClient.getInstructionHistory().add(sameUserInstruction);
        defenceClientRepository.save(defClient);

        final int count = instructionRepository.findNumberOfInstructionsForUserForDefenceClient(defClient.getId(), instruction.getUserId());

        assertThat(count, is(2));
        assertThat(instructionRepository.findNumberOfInstructionsForUserForDefenceClient(defClient.getId(), randomUUID()), is(0));
    }

    @Test
    public void shouldFindNumberOfInstructionsByCriteria() {

        final DefenceClient defClient = createDefenceClient();

        final Instruction instruction = createInstruction(defClient);
        defClient.getInstructionHistory().add(instruction);
        defenceClientRepository.save(defClient);

        final int count = instructionRepository.findNumberOfInstructionsByCriteria(defClient.getId(), instruction.getOrganisationId());

        assertThat(count, is(1));
        assertThat(instructionRepository.findNumberOfInstructionsByCriteria(defClient.getId(), randomUUID()), is(0));
    }

    private Instruction createInstruction(final DefenceClient defClient) {
        return new Instruction(randomUUID(), randomUUID(), randomUUID(), defClient, now());
    }
}