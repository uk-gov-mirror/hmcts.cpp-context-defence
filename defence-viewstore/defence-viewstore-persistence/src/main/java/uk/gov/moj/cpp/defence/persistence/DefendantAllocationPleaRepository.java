package uk.gov.moj.cpp.defence.persistence;

import uk.gov.moj.cpp.defence.persistence.entity.DefendantAllocationPlea;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DefendantAllocationPleaRepository extends AbstractDefenceRepository<DefendantAllocationPlea, UUID> {

    public DefendantAllocationPleaRepository() {
        super(DefendantAllocationPlea.class);
    }
}
