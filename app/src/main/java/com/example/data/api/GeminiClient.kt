package com.example.data.api

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    private const val DEFAULT_MODEL = "gemini-3.5-flash"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Transcribes Tunisian Arabic audio utilizing the Gemini model.
     * @param audioFile The local recorded audio file (.mp4/aac or .wav)
     * @param userApiKey Optional custom API Key entered by the user
     */
    suspend fun transcribeAudio(
        audioFile: File,
        userApiKey: String? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = if (!userApiKey.isNullOrBlank()) {
            userApiKey.trim()
        } else {
            BuildConfig.GEMINI_API_KEY
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalArgumentException("API_KEY_MISSING")
        }

        val base64Audio = try {
            val bytes = FileInputStream(audioFile).use { it.readBytes() }
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read audio file: ${e.message}")
            throw IOException("فشل في قراءة ملف الصوت: ${e.localizedMessage}")
        }

        // We construct a gorgeous, optimal system instruction for Tunisian Arabic DHH Live transcription.
        val systemInstruction = """
            You are an expert AI speech-to-text transcription engine specializing in real-time dialect-accurate transcription specifically tailored for the Deaf and Hard of Hearing (DHH) community.
            Your core capability is the flawless understanding and transcribing of the Tunisian Arabic dialect (Derja/تونسي) into highly readable, clear, punctuated written text.

            Guidelines:
            1. Dialect Mastery (Tunisian Derja):
               - Transcribe the audio content word-for-word into written Tunisian Arabic.
               - Fully respect and write using authentic Tunisian Derja terms (e.g., "برشة", "شكون", "شنيّة", "توّة", "علاش", "باهي", "عيشك", "بربّي").
               - In cases of Arabic-French code-switching (e.g., "urgence", "c'est urgent", "merci", "normal"), write French words accurately in French characters or clear phonetic Arabic based on spoken style, prioritizing DHH legibility.

            2. DHH Optimization & Punctuation:
               - Always implement exact punctuation (. ، ! ؟) to guide sentence boundary reading and visual speech rhythm.
               - Break long sentences or continuous speakers into short, legible paragraphs.
               - Format non-speech sounds in brackets to preserve speaker emotions and environmental context: [ضحك], [صوت مرتفع], [تصفيق], [صمت], [توقف طويل], [صوت في الخلفية], [موسيقى].

            3. Filtering & Readability:
               - Filter out meaningless repetitive acoustic fillers like "أأأأ", "يعني يعني", "امممم" to maximize clarity, unless they show key hesitation.
               - Do NOT summarize or shorten. Deliver full literal transcriptions optimized for visual readability.
        """.trimIndent()

        // Build request payload using standard JSONObject
        val requestJson = JSONObject().apply {
            // CONTENTS
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        // Prompt text
                        put(JSONObject().apply {
                            put("text", "Transcribe this audio exact word-for-word from Tunisian Arabic (Derja) with DHH styling. If no voice is heard, or if it's purely noise, transcribe what background events take place in brackets. If silent, note it as [صمت].")
                        })
                        // Inline Audio Data
                        put(JSONObject().apply {
                            val inlineDataObj = JSONObject().apply {
                                put("mimeType", "audio/mp4")
                                put("data", base64Audio)
                            }
                            put("inlineData", inlineDataObj)
                        })
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            // SYSTEM INSTRUCTION
            val systemInstructionObj = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstruction)
                    })
                }
                put("parts", partsArray)
            }
            put("systemInstruction", systemInstructionObj)

            // CONFIGURATION
            val generationConfig = JSONObject().apply {
                put("temperature", 0.1) // Low temperature for highly deterministic transcription accuracy
            }
            put("generationConfig", generationConfig)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)
        val url = "$BASE_URL$DEFAULT_MODEL:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    val errorMsg = responseBody ?: "Unknown error"
                    Log.e(TAG, "Network failure: $errorMsg Code: ${response.code}")
                    if (response.code == 400 && errorMsg.contains("API key not valid")) {
                        throw IllegalArgumentException("API_KEY_INVALID")
                    }
                    throw IOException("فشل الاتصال بخادم الذكاء الاصطناعي (كود: ${response.code})")
                }

                if (responseBody.isNullOrBlank()) {
                    throw IOException("استجابة فارغة من خادم الذكاء الاصطناعي")
                }

                val root = JSONObject(responseBody)
                val candidates = root.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val firstPart = parts?.optJSONObject(0)
                val textResult = firstPart?.optString("text")

                if (textResult.isNullOrBlank()) {
                    throw IOException("لم يتم توفير نص مكتوب من خادم الذكاء الاصطناعي")
                }

                return@withContext textResult.trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request exceptional fail: ${e.message}")
            if (e is IllegalArgumentException) {
                throw e
            }
            throw IOException(e.localizedMessage ?: "فشل غير معروف في الاتصال بالخادم")
        }
    }
}
