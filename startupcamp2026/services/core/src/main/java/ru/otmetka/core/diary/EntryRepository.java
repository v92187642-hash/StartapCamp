package ru.otmetka.core.diary;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface EntryRepository extends JpaRepository<Entry, UUID> {

    /** Locks the row, so two taps at the same moment are handled one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Entry e where e.id = :id")
    Optional<Entry> findByIdForUpdate(UUID id);

    @Query("""
            select e from Entry e
            where e.planItemId in :itemIds and e.scheduledAt >= :from and e.scheduledAt < :to
            order by e.scheduledAt, e.id
            """)
    List<Entry> findInRange(Collection<UUID> itemIds, Instant from, Instant to);

    @Query("""
            select e from Entry e
            where e.planItemId in :itemIds and e.status = ru.otmetka.core.diary.EntryStatus.PLANNED
              and e.scheduledAt >= :from
            order by e.scheduledAt, e.id
            """)
    List<Entry> findPlannedFrom(Collection<UUID> itemIds, Instant from);
}
