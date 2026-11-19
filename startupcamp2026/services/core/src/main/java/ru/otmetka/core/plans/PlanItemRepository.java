package ru.otmetka.core.plans;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PlanItemRepository extends JpaRepository<PlanItem, UUID> {

    List<PlanItem> findByPlanIdOrderByPosition(UUID planId);

    int countByPlanId(UUID planId);
}
