package ru.otmetka.android.data

import java.util.UUID
import ru.otmetka.android.api.models.Question
import ru.otmetka.android.api.models.QuestionCreate

/**
 * Questions for the doctor and the PDF report for the visit. Always asks the
 * server: nothing here works offline. The app only keeps the list; it never
 * answers a question.
 */
class QuestionsRepository(private val api: ApiFactory) {

    suspend fun questions(planId: String): List<Question> =
        api.questions.listQuestions(UUID.fromString(planId)).bodyOrThrow()

    suspend fun add(planId: String, text: String): Question =
        api.questions.addQuestion(UUID.fromString(planId), QuestionCreate(text)).bodyOrThrow()

    suspend fun setAsked(questionId: UUID, asked: Boolean) {
        if (asked) {
            api.questions.markQuestionAsked(questionId).throwIfFailed()
        } else {
            api.questions.markQuestionNotAsked(questionId).throwIfFailed()
        }
    }

    suspend fun delete(questionId: UUID) = api.questions.deleteQuestion(questionId).throwIfFailed()

    /** The report as the bytes of a PDF file. Premium only: the server answers 403 otherwise. */
    suspend fun report(planId: String): ByteArray =
        api.reports.getReport(UUID.fromString(planId)).bodyOrThrow().bytes()
}
