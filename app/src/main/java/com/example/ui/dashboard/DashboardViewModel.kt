package com.example.ui.dashboard

import android.app.Application
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiClient
import com.example.data.local.AppDatabase
import com.example.data.local.TranscriptEntity
import com.example.data.repository.TranscriptRepository
import com.example.utils.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = TranscriptRepository(database.transcriptDao())
    private val audioRecorder = AudioRecorder(application)

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _simulationScenarioTitle = MutableStateFlow<String?>(null)
    val simulationScenarioTitle: StateFlow<String?> = _simulationScenarioTitle.asStateFlow()

    private val _liveAmplitudes = MutableStateFlow<List<Float>>(List(30) { 0.1f })
    val liveAmplitudes: StateFlow<List<Float>> = _liveAmplitudes.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0L)
    val recordingDurationSeconds: StateFlow<Long> = _recordingDurationSeconds.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _transcriptionOutput = MutableStateFlow("")
    val transcriptionOutput: StateFlow<String> = _transcriptionOutput.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _fontSizeScale = MutableStateFlow(1.2f) // Default scale perfect for deaf readability
    val fontSizeScale: StateFlow<Float> = _fontSizeScale.asStateFlow()

    private val _activeQuickReply = MutableStateFlow<String?>(null)
    val activeQuickReply: StateFlow<String?> = _activeQuickReply.asStateFlow()

    // Loaded API Key status
    private val _isLocalKeyAvailable = MutableStateFlow(false)
    val isLocalKeyAvailable: StateFlow<Boolean> = _isLocalKeyAvailable.asStateFlow()

    // Filtered list of transcripts
    val transcripts: StateFlow<List<TranscriptEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allTranscripts
            } else {
                repository.search(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var activeRecordingFile: File? = null
    private var visualizerJob: Job? = null
    private var timerJob: Job? = null
    private var recordStartTime = 0L

    init {
        // Evaluate if Gemini Key exists in BuildConfig (from secrets)
        val key = com.example.BuildConfig.GEMINI_API_KEY
        if (key.isNotBlank() && key != "MY_GEMINI_API_KEY") {
            _isLocalKeyAvailable.value = true
        }
    }

    fun setCustomApiKey(key: String) {
        _apiKey.value = key
    }

    fun setFontSizeScale(scale: Float) {
        _fontSizeScale.value = scale
    }

    fun setActiveQuickReply(reply: String?) {
        _activeQuickReply.value = reply
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Start recording vocals from mic.
     */
    fun startVoiceRecording() {
        if (_isRecording.value || _isSimulating.value) return
        clearError()
        _transcriptionOutput.value = ""

        val file = audioRecorder.startRecording()
        if (file == null) {
            _errorMessage.value = "فشل في تشغيل اللاقط الصوتي. يرجى تفعيل أذونات التسجيل."
            return
        }

        activeRecordingFile = file
        _isRecording.value = true
        recordStartTime = SystemClock.elapsedRealtime()

        // Amplitudes visualizer loop
        visualizerJob = viewModelScope.launch {
            while (_isRecording.value) {
                delay(80)
                val amp = audioRecorder.getAmplitude()
                // Map the integer amplitude to [0.1f - 1.0f] float scale
                val normalized = (amp / 32767f).coerceIn(0.1f, 1.0f)
                updateLiveAmplitudes(normalized)
            }
        }

        // Timer counter
        timerJob = viewModelScope.launch {
            while (_isRecording.value) {
                _recordingDurationSeconds.value = (SystemClock.elapsedRealtime() - recordStartTime) / 1000
                delay(500)
            }
        }
    }

    /**
     * Stop mic recording and request high-accuracy transcription to Gemini API.
     */
    fun stopVoiceRecordingAndTranscribe() {
        if (!_isRecording.value) return
        _isRecording.value = false
        audioRecorder.stopRecording()
        visualizerJob?.cancel()
        timerJob?.cancel()

        val recordDuration = _recordingDurationSeconds.value * 1000L
        _recordingDurationSeconds.value = 0
        _liveAmplitudes.value = List(30) { 0.1f }

        val file = activeRecordingFile ?: return
        if (!file.exists() || file.length() <= 0) {
            _errorMessage.value = "ملف تسجيل الصوت فارغ أو لم يتم إنشاؤه."
            return
        }

        performTranscription(file, recordDuration)
    }

    private fun performTranscription(file: File, durationMs: Long) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                // Read custom API key or default BuildConfig key
                val userKey = _apiKey.value.ifBlank { null }
                val resultText = GeminiClient.transcribeAudio(file, userKey)

                _transcriptionOutput.value = resultText

                // Save to local SQLite Room Database
                val entity = TranscriptEntity(
                    timestamp = System.currentTimeMillis(),
                    text = resultText,
                    durationMs = durationMs
                )
                repository.insert(entity)

            } catch (e: Exception) {
                Log.e("DashboardVM", "Transcription failed: ${e.message}")
                when (e.message) {
                    "API_KEY_MISSING" -> {
                        _errorMessage.value = "مفتاح Gemini API غير مفعّل. يرجى إدخال مفتاح صالح في الإعدادات أو استخدام المحاكاة التجريبية للتونسي."
                    }
                    "API_KEY_INVALID" -> {
                        _errorMessage.value = "مفتاح API المدخل غير صالح. يرجى التحقق من المفتاح في خانة الإعدادات."
                    }
                    else -> {
                        _errorMessage.value = "فشل في معالجة الصوت: ${e.localizedMessage ?: "مشكلة في الإتصال بالإنترنت"}"
                    }
                }
            } finally {
                _isProcessing.value = false
                // Clean temp audio file
                try {
                    file.delete()
                } catch (ignored: Exception) {}
            }
        }
    }

    /**
     * Run a beautiful authentic simulator representing Tunisian spoken events.
     * Offers high interactive sandbox previews directly in browser-emulators.
     */
    fun runSimulatedScenario(
        title: String,
        spokenArabicText: String,
        simulatedOutput: String
    ) {
        if (_isRecording.value || _isSimulating.value) return
        clearError()
        _transcriptionOutput.value = ""
        _isSimulating.value = true
        _simulationScenarioTitle.value = title

        viewModelScope.launch {
            val simulationDurationS = 4
            val sampleCount = simulationDurationS * 10 // 40 steps of 100ms
            
            // Loop amplitudes visually to show the user sound waves
            launch {
                for (i in 0 until sampleCount) {
                    if (!_isSimulating.value) break
                    val waveFloat = if (i % 5 == 0) 0.15f else (0.2f + Math.sin(i.toDouble() / 2.0).coerceIn(0.0, 1.0).toFloat() * 0.75f)
                    updateLiveAmplitudes(waveFloat)
                    delay(100)
                }
            }

            // Simulated timer countdown
            launch {
                for (s in 0..simulationDurationS) {
                    if (!_isSimulating.value) break
                    _recordingDurationSeconds.value = s.toLong()
                    delay(1000)
                }
            }

            // Keep simulating listening loop
            delay(4000)

            _isSimulating.value = false
            _simulationScenarioTitle.value = null
            _recordingDurationSeconds.value = 0
            _liveAmplitudes.value = List(30) { 0.1f }

            // Trigger mock AI "typing-out" animation of transcription for stunning organic visuals!
            _isProcessing.value = true
            delay(1200) // simulated network delay
            _isProcessing.value = false

            _transcriptionOutput.value = simulatedOutput

            // Persist the simulation to database instantly!
            val entity = TranscriptEntity(
                timestamp = System.currentTimeMillis(),
                text = simulatedOutput,
                durationMs = simulationDurationS * 1000L,
                note = "مُحاكاة للحدث: $title"
            )
            repository.insert(entity)
        }
    }

    private fun updateLiveAmplitudes(amp: Float) {
        val currentList = _liveAmplitudes.value.toMutableList()
        currentList.removeAt(0)
        currentList.add(amp)
        _liveAmplitudes.value = currentList
    }

    fun toggleBookmark(id: Int, currentStatus: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateBookmark(id, !currentStatus)
        }
    }

    fun updateAnnotation(id: Int, annotation: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateNote(id, annotation)
        }
    }

    fun deleteTranscript(entity: TranscriptEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(entity)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }

    fun applySearchQuery(query: String) {
        _searchQuery.value = query
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        visualizerJob?.cancel()
        timerJob?.cancel()
    }
}
