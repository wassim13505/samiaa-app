package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val text: String,
    val durationMs: Long,
    val isBookmarked: Boolean = false,
    val note: String = "" // Optional user annotations to the transcript
)
