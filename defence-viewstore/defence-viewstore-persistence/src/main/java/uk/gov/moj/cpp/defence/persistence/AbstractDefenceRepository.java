package uk.gov.moj.cpp.defence.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Base viewstore repository providing the common persistence operations previously inherited from
 * DeltaSpike's {@code EntityRepository}. Concrete repositories extend this and add their own JPQL
 * query methods. Part of the Java 25 / Jakarta EE upgrade (DeltaSpike is not available on Jakarta).
 */
public abstract class AbstractDefenceRepository<E, K> {

    @PersistenceContext(unitName = "defence")
    protected EntityManager entityManager;

    private final Class<E> entityType;

    protected AbstractDefenceRepository(final Class<E> entityType) {
        this.entityType = entityType;
    }

    public E findBy(final K id) {
        return entityManager.find(entityType, id);
    }

    public Optional<E> findOptionalBy(final K id) {
        return Optional.ofNullable(entityManager.find(entityType, id));
    }

    public List<E> findAll() {
        return entityManager.createQuery("SELECT e FROM " + entityType.getSimpleName() + " e", entityType).getResultList();
    }

    public E save(final E entity) {
        return entityManager.merge(entity);
    }

    public E saveAndFlush(final E entity) {
        final E merged = entityManager.merge(entity);
        entityManager.flush();
        return merged;
    }

    public void remove(final E entity) {
        entityManager.remove(entityManager.contains(entity) ? entity : entityManager.merge(entity));
    }

    public void refresh(final E entity) {
        entityManager.refresh(entity);
    }

    public E merge(final E entity) {
        return entityManager.merge(entity);
    }

    public void flush() {
        entityManager.flush();
    }

    public long count() {
        return entityManager.createQuery("SELECT COUNT(e) FROM " + entityType.getSimpleName() + " e", Long.class).getSingleResult();
    }
}
