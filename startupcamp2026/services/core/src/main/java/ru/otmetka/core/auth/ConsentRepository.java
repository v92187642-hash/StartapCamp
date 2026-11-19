package ru.otmetka.core.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ConsentRepository extends JpaRepository<Consent, UUID> {

    boolean existsByUserIdAndKind(UUID userId, String kind);

    Optional<Consent> findByUserIdAndKindAndTextVersion(UUID userId, String kind, String textVersion);
}
