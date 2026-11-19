package ru.otmetka.core.questions;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByPlanIdOrderByCreatedAt(UUID planId);

    List<Question> findByAuthorId(UUID authorId);
}
