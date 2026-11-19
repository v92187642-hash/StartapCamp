package ru.otmetka.core.sharing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ShareRepository extends JpaRepository<Share, UUID> {

    boolean existsByPlanIdAndMemberIdAndStatus(UUID planId, UUID memberId, String status);

    List<Share> findByMemberIdAndStatus(UUID memberId, String status);

    List<Share> findByPlanIdAndStatusOrderByCreatedAt(UUID planId, String status);

    List<Share> findByPlanIdOrderByCreatedAt(UUID planId);

    Optional<Share> findByPlanIdAndInvitedEmail(UUID planId, String invitedEmail);

    Optional<Share> findByTokenHash(String tokenHash);
}
