package ru.otmetka.core.plans;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PlanRepository extends JpaRepository<Plan, UUID> {

    List<Plan> findByOwnerId(UUID ownerId);

    long countByOwnerId(UUID ownerId);
}
