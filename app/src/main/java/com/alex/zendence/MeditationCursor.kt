package com.alex.zendence

import android.database.MatrixCursor

object MeditationCursor {
    private const val COL_ID = "id"
    private const val COL_TIMESTAMP = "timestamp"
    private const val COL_DURATION_MINUTES = "durationMinutes"
    private const val COL_INSIGHT = "insight"

    private val COLUMNS = arrayOf(
        COL_ID,
        COL_TIMESTAMP,
        COL_DURATION_MINUTES,
        COL_INSIGHT
    )

    fun fromMeditations(meditations: List<Meditation>): MatrixCursor {
        val cursor = MatrixCursor(COLUMNS)
        meditations.forEach { m ->
            cursor.newRow()
                .add(COL_ID, m.id)
                .add(COL_TIMESTAMP, m.timestamp)
                .add(COL_DURATION_MINUTES, m.durationMinutes)
                .add(COL_INSIGHT, m.insight)
        }
        return cursor
    }
}
