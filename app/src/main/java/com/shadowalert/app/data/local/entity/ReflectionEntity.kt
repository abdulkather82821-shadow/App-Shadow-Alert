package com.shadowalert.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reflections",
    indices = [Index(value = ["sessionId"])]
)
data class ReflectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val response: String,
    val createdAt: Long
)
