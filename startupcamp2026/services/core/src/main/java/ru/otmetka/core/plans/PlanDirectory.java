package ru.otmetka.core.plans;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public interface of the plans module. Other modules ask questions about
 * plans here and never query the plans tables themselves.
 */
public interface PlanDirectory {

    Optional<PlanRef> findPlan(UUID planId);

    Optional<PlanItemRef> findItem(UUID itemId);

    List<PlanItemRef> itemsOf(UUID planId);

    /**
     * Records that something about the plan changed outside this module (for
     * example its shares): raises the version and announces {@link PlanChanged}.
     */
    void touch(UUID planId);
}
