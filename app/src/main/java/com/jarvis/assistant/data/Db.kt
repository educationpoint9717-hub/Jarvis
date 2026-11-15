package com.jarvis.assistant.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,            // "user" or "jarvis"
    val text: String,
    val ts: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val ts: Long = System.currentTimeMillis()
)

@Dao
interface MessageDao {
    @Insert suspend fun insert(m: MessageEntity)
    @Query("SELECT * FROM messages ORDER BY id DESC LIMIT :n") suspend fun last(n: Int): List<MessageEntity>
    @Query("DELETE FROM messages") suspend fun clear()
}

@Dao
interface NoteDao {
    @Insert suspend fun insert(n: NoteEntity)
    @Query("SELECT * FROM notes ORDER BY id DESC LIMIT :n") suspend fun last(n: Int): List<NoteEntity>
    @Query("SELECT * FROM notes WHERE text LIKE '%' || :q || '%' ORDER BY id DESC LIMIT 10")
    suspend fun search(q: String): List<NoteEntity>
}

@Database(entities = [MessageEntity::class, NoteEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun notes(): NoteDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "jarvis.db")
                .build().also { inst = it }
        }
    }
}
