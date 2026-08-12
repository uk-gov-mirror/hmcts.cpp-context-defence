package uk.gov.moj.cpp.defence.persistence;

import uk.gov.moj.cpp.defence.persistence.entity.IdpcAccess;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class IdpcAccessHistoryRepository extends AbstractDefenceRepository<IdpcAccess, UUID> {

    private static final String DEFENCE_CLIENT_ID = "defenceClientId";

    public IdpcAccessHistoryRepository() {
        super(IdpcAccess.class);
    }

    public List<IdpcAccess> findIdpcAccessByCriteria(final UUID defenceClientId) {
        return entityManager.createQuery(
                        "SELECT ia FROM IdpcAccess ia WHERE ia.defenceClientId = :defenceClientId",
                        IdpcAccess.class)
                .setParameter(DEFENCE_CLIENT_ID, defenceClientId)
                .getResultList();
    }

    public List<IdpcAccess> findIdpcAccessByCriteria(final UUID defenceClientId, final UUID idpcId) {
        return entityManager.createQuery(
                        "SELECT ia FROM IdpcAccess ia WHERE ia.defenceClientId = :defenceClientId AND ia.idpcDetailsId = :idpcId",
                        IdpcAccess.class)
                .setParameter(DEFENCE_CLIENT_ID, defenceClientId)
                .setParameter("idpcId", idpcId)
                .getResultList();
    }

    public List<UUID> findIdpcAccessOrganisationByCriteria(final UUID defenceClientId, final UUID idpcId) {
        return entityManager.createQuery(
                        "SELECT ia.organisationId FROM IdpcAccess ia WHERE ia.defenceClientId = :defenceClientId AND ia.idpcDetailsId = :idpcId",
                        UUID.class)
                .setParameter(DEFENCE_CLIENT_ID, defenceClientId)
                .setParameter("idpcId", idpcId)
                .getResultList();
    }

    public List<UUID> findOrderedDistinctOrgIdsOfIdpcAccessForDefenceClient(final UUID defenceClientId) {
        return entityManager.createQuery(
                        "SELECT ia.organisationId FROM IdpcAccess ia WHERE ia.defenceClientId = :defenceClientId GROUP BY ia.organisationId ORDER BY MAX(ia.accessTimestamp) DESC",
                        UUID.class)
                .setParameter(DEFENCE_CLIENT_ID, defenceClientId)
                .getResultList();
    }
}
