package uk.gov.moj.cpp.defence.persistence;

import uk.gov.moj.cpp.defence.persistence.entity.Instruction;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InstructionRepository extends AbstractDefenceRepository<Instruction, UUID> {

    private static final String DEFENCE_CLIENT_ID = "defenceClientId";

    public InstructionRepository() {
        super(Instruction.class);
    }

    public List<Instruction> findInstructionsByCriteria(final UUID defenceClientId, final UUID userId) {
        return entityManager.createQuery(
                        "SELECT ins FROM Instruction ins WHERE ins.defenceClient.id = :defenceClientId AND ins.userId = :userId",
                        Instruction.class)
                .setParameter(DEFENCE_CLIENT_ID, defenceClientId)
                .setParameter("userId", userId)
                .getResultList();
    }

    public int findNumberOfInstructionsForUserForDefenceClient(final UUID defenceClientId, final UUID userId) {
        return entityManager.createQuery(
                        "SELECT COUNT(ins) FROM Instruction ins WHERE ins.defenceClient.id = :defenceClientId AND ins.userId = :userId",
                        Long.class)
                .setParameter(DEFENCE_CLIENT_ID, defenceClientId)
                .setParameter("userId", userId)
                .getSingleResult().intValue();
    }

    public int findNumberOfInstructionsByCriteria(final UUID defenceClientId, final UUID organisationId) {
        return entityManager.createQuery(
                        "SELECT COUNT(ins) FROM Instruction ins WHERE ins.defenceClient.id = :defenceClientId AND ins.organisationId = :organisationId",
                        Long.class)
                .setParameter(DEFENCE_CLIENT_ID, defenceClientId)
                .setParameter("organisationId", organisationId)
                .getSingleResult().intValue();
    }
}
