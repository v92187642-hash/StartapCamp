package ru.otmetka.core.diary;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MarkHistoryRepository extends JpaRepository<MarkHistory, UUID> {
}
