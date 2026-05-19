package com.alex.zendence

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import androidx.room.Room
import kotlinx.coroutines.runBlocking

class MeditationContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.alex.zendence.meditationprovider"
        private const val MEDITATIONS_TABLE = "meditations"
        private const val MEDITATIONS = 1
        private const val MEDITATION_ID = 2

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$MEDITATIONS_TABLE")

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, MEDITATIONS_TABLE, MEDITATIONS)
            addURI(AUTHORITY, "$MEDITATIONS_TABLE/#", MEDITATION_ID)
        }
    }

    private lateinit var db: AppDatabase

    override fun onCreate(): Boolean {
        context?.let { ctx ->
            db = Room.databaseBuilder(
                ctx.applicationContext,
                AppDatabase::class.java,
                "meditations.db"
            ).build()
            return true
        }
        return false
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val dao = db.meditationDao()
        val meditations = runBlocking {
            when (uriMatcher.match(uri)) {
                MEDITATIONS -> dao.getAllList()
                MEDITATION_ID -> {
                    val id = ContentUris.parseId(uri).toInt()
                    dao.getById(id)?.let { listOf(it) } ?: emptyList()
                }
                else -> throw IllegalArgumentException("Unknown URI: $uri")
            }
        }

        val cursor = MeditationCursor.fromMeditations(meditations)
        cursor.setNotificationUri(context?.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            MEDITATIONS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$MEDITATIONS_TABLE"
            MEDITATION_ID -> "vnd.android.cursor.item/vnd.$AUTHORITY.$MEDITATIONS_TABLE"
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Insert not supported via provider")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Delete not supported via provider")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("Update not supported via provider")
    }
}
