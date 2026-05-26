package com.example.data.repository

import com.example.data.local.TranscriptDao
import com.example.data.local.TranscriptEntity
import kotlinx.coroutines.flow.Flow

class TranscriptRepository(private val transcriptDao: TranscriptDao) {
    val allTranscripts: Flow<List<TranscriptEntity>> = transcriptDao.getAllTranscripts()

    fun search(query: String): Flow<List<TranscriptEntity>> {
        val searchQuery = "%$query%"
        return transcriptDao.searchTranscripts(searchQuery)
    }

    suspend fun insert(transcript: TranscriptEntity): Long {
        return transcriptDao.insertTranscript(transcript)
    }

    suspend fun updateBookmark(id: Int, isBookmarked: Boolean) {
        transcriptDao.updateBookmark(id, isBookmarked)
    }

    suspend fun updateNote(id: Int, note: String) {
        transcriptDao.updateNote(id, note)
    }

    suspend fun delete(transcript: TranscriptEntity) {
        transcriptDao.deleteTranscript(transcript)
    }

    suspend fun clearAll() {
        transcriptDao.clearAll()
    }
}
