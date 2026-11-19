package ru.otmetka.android.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.otmetka.android.OtmetkaApp
import ru.otmetka.android.api.models.Question
import ru.otmetka.android.data.DiaryRepository
import ru.otmetka.android.data.QuestionsRepository

data class QuestionsState(
    val isOwner: Boolean = false,
    /** null until loaded. */
    val questions: List<Question>? = null,
    val busy: Boolean = false,
    /** Grows with every question written, so the screen knows when to empty the text field. */
    val added: Int = 0,
    val downloading: Boolean = false,
    val reportSaved: Boolean = false,
    val error: UiMessage? = null,
)

/** Questions for the doctor and the report. Everything here needs a network. */
class QuestionsViewModel(
    private val planId: String,
    private val diary: DiaryRepository,
    private val repository: QuestionsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QuestionsState())
    val state: StateFlow<QuestionsState> = _state.asStateFlow()

    /** The downloaded report, waiting for the user to choose where to save it. */
    private val _report = MutableStateFlow<ByteArray?>(null)
    val report: StateFlow<ByteArray?> = _report.asStateFlow()

    fun load() = run {}

    fun add(text: String) {
        if (text.isBlank()) return
        run {
            repository.add(planId, text.trim())
            _state.update { it.copy(added = it.added + 1) }
        }
    }

    fun setAsked(questionId: UUID, asked: Boolean) = run { repository.setAsked(questionId, asked) }

    fun delete(questionId: UUID) = run { repository.delete(questionId) }

    fun downloadReport() {
        _state.update { it.copy(downloading = true, reportSaved = false, error = null) }
        viewModelScope.launch {
            try {
                _report.value = repository.report(planId)
            } catch (e: Exception) {
                _state.update { it.copy(downloading = false, error = e.toUiMessage()) }
            }
        }
    }

    /** Writes the report into the file the user chose; null means they closed the chooser. */
    fun saveReport(resolver: ContentResolver, target: Uri?) {
        val pdf = _report.value
        _report.value = null
        if (pdf == null || target == null) {
            _state.update { it.copy(downloading = false) }
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(target)!!.use { it.write(pdf) }
                }
                _state.update { it.copy(downloading = false, reportSaved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(downloading = false, error = e.toUiMessage()) }
            }
        }
    }

    /** Runs a change and then reads the list again. */
    private fun run(action: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null, reportSaved = false) }
        viewModelScope.launch {
            try {
                action()
                val isOwner = diary.isOwner(planId)
                val questions = repository.questions(planId)
                _state.update { it.copy(isOwner = isOwner, questions = questions, busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.toUiMessage()) }
            }
        }
    }

    companion object {
        fun factory(planId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as OtmetkaApp
                QuestionsViewModel(planId, app.diary, app.questions)
            }
        }
    }
}
