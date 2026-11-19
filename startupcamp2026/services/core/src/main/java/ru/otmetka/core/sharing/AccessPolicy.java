package ru.otmetka.core.sharing;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.otmetka.core.common.ForbiddenException;
import ru.otmetka.core.common.NotFoundException;
import ru.otmetka.core.plans.PlanRef;

/**
 * The one place that decides who may do what with a plan. Every plan endpoint
 * calls it. The rule: the owner does everything; a relative with an accepted
 * share reads; anyone else is told the plan does not exist.
 */
@Component
public class AccessPolicy {

    private final ShareRepository shares;

    AccessPolicy(ShareRepository shares) {
        this.shares = shares;
    }

    /** The caller's role, or 404 for a stranger, so a plan's existence is not revealed. */
    public PlanRole requireCanView(UUID userId, PlanRef plan) {
        if (plan.ownerId().equals(userId)) {
            return PlanRole.OWNER;
        }
        if (shares.existsByPlanIdAndMemberIdAndStatus(plan.planId(), userId, Share.ACCEPTED)) {
            return PlanRole.VIEWER;
        }
        throw new NotFoundException("План не найден");
    }

    /** Only the owner changes a plan or its entries; a relative is read-only. */
    public void requireOwner(UUID userId, PlanRef plan) {
        if (requireCanView(userId, plan) != PlanRole.OWNER) {
            throw new ForbiddenException("Это может делать только владелец плана");
        }
    }

    /** Plans the user sees as a relative. */
    public List<UUID> sharedPlanIds(UUID userId) {
        return shares.findByMemberIdAndStatus(userId, Share.ACCEPTED).stream().map(Share::getPlanId).toList();
    }

    /** Relatives with an accepted share: the people to alert about the plan. */
    public List<UUID> recipients(UUID planId) {
        return shares.findByPlanIdAndStatusOrderByCreatedAt(planId, Share.ACCEPTED).stream()
                .map(Share::getMemberId).distinct().toList();
    }
}
