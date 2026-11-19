package ru.otmetka.core.sharing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otmetka.core.auth.Hashing;
import ru.otmetka.core.auth.UserDeleting;
import ru.otmetka.core.auth.PremiumPolicy;
import ru.otmetka.core.auth.UserDirectory;
import ru.otmetka.core.common.BadRequestException;
import ru.otmetka.core.common.Mailer;
import ru.otmetka.core.common.NotFoundException;
import ru.otmetka.core.plans.PlanDirectory;
import ru.otmetka.core.plans.PlanRef;

@Service
class ShareService {

    static final Duration INVITATION_LIFETIME = Duration.ofDays(7);

    private final ShareRepository shares;
    private final PlanDirectory plans;
    private final AccessPolicy access;
    private final UserDirectory users;
    private final PremiumPolicy premium;
    private final Mailer mailer;
    private final Clock clock;
    private final String webBaseUrl;

    ShareService(ShareRepository shares, PlanDirectory plans, AccessPolicy access, UserDirectory users,
            PremiumPolicy premium, Mailer mailer, Clock clock, @Value("${otmetka.web-base-url}") String webBaseUrl) {
        this.shares = shares;
        this.plans = plans;
        this.access = access;
        this.users = users;
        this.premium = premium;
        this.mailer = mailer;
        this.clock = clock;
        this.webBaseUrl = webBaseUrl;
    }

    @Transactional(readOnly = true)
    List<ShareResponse> list(UUID userId, UUID planId) {
        access.requireOwner(userId, plan(planId));
        return shares.findByPlanIdOrderByCreatedAt(planId).stream().map(ShareResponse::of).toList();
    }

    /** Inviting the same address again sends a fresh link. */
    @Transactional
    ShareResponse invite(UUID userId, UUID planId, String rawEmail) {
        access.requireOwner(userId, plan(planId));
        premium.requirePremium(userId);
        String email = UserDirectory.normalizeEmail(rawEmail);
        if (users.emailOf(userId).filter(email::equals).isPresent()) {
            throw new BadRequestException("Нельзя пригласить самого себя");
        }

        Instant now = clock.instant();
        Share share = shares.findByPlanIdAndInvitedEmail(planId, email)
                .orElseGet(() -> new Share(planId, email, now));
        if (share.isPending()) {
            String token = Hashing.randomToken();
            share.renewToken(Hashing.sha256(token), now.plus(INVITATION_LIFETIME));
            shares.save(share);
            mailer.send(email, "Приглашение в Отметку",
                    "Вас пригласили следить за дневником выполнения назначений врача.\n"
                            + "Чтобы принять приглашение, откройте ссылку: " + webBaseUrl + "/invite?token=" + token
                            + "\nСсылка действует 7 дней.");
        }
        return ShareResponse.of(share);
    }

    @Transactional
    ShareResponse accept(UUID userId, String token) {
        Share share = shares.findByTokenHash(Hashing.sha256(token))
                .filter(Share::isPending)
                .filter(s -> s.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new NotFoundException("Приглашение не найдено, уже использовано или устарело"));
        String callerEmail = users.emailOf(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (!share.getInvitedEmail().equals(callerEmail)) {
            throw new NotFoundException("Приглашение не найдено, уже использовано или устарело");
        }
        if (plan(share.getPlanId()).ownerId().equals(userId)) {
            throw new BadRequestException("Это ваш собственный план");
        }
        share.accept(userId);
        // The list of people to alert changed.
        plans.touch(share.getPlanId());
        return ShareResponse.of(share);
    }

    @Transactional
    void revoke(UUID userId, UUID shareId) {
        Share share = shares.findById(shareId).orElseThrow(() -> new NotFoundException("Приглашение не найдено"));
        access.requireOwner(userId, plan(share.getPlanId()));
        shares.delete(share);
        plans.touch(share.getPlanId());
    }

    /** An account is being deleted: the person stops being a relative on other people's plans. */
    @EventListener
    @Order(1)
    void on(UserDeleting event) {
        for (Share share : shares.findByMemberIdAndStatus(event.userId(), Share.ACCEPTED)) {
            shares.delete(share);
            plans.touch(share.getPlanId());
        }
        shares.flush();
    }

    private PlanRef plan(UUID planId) {
        return plans.findPlan(planId).orElseThrow(() -> new NotFoundException("План не найден"));
    }
}
