package com.pawviewer.core.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.pawviewer.core.model.Creator
import com.pawviewer.core.model.Post

object DownloadState {
    const val WAITING = 0
    const val RUNNING = 1
    const val DONE = 2
    const val FAILED = 3
    const val CANCELED = 4
}

data class DownloadRow(
    val id: Long,
    val service: String,
    val uid: String,
    val pid: String,
    val title: String,
    val creator: String,
    val total: Int,
    val done: Int,
    val state: Int,
    val dir: String,
    val created: Long,
)

data class HistoryRow(
    val service: String,
    val uid: String,
    val pid: String,
    val title: String,
    val creator: String,
    val page: Int,
    val total: Int,
    val ts: Long,
)

class Db private constructor(context: Context) : SQLiteOpenHelper(context, "paw.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE downloads(
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                service TEXT NOT NULL, uid TEXT NOT NULL, pid TEXT NOT NULL,
                title TEXT, creator TEXT,
                total INTEGER DEFAULT 0, done INTEGER DEFAULT 0,
                state INTEGER DEFAULT 0, dir TEXT, created INTEGER,
                UNIQUE(service, uid, pid)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE history(
                service TEXT NOT NULL, uid TEXT NOT NULL, pid TEXT NOT NULL,
                title TEXT, creator TEXT, page INTEGER DEFAULT 0, total INTEGER DEFAULT 0,
                ts INTEGER, PRIMARY KEY(service, uid, pid)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE favs(
                service TEXT NOT NULL, uid TEXT NOT NULL, pid TEXT NOT NULL,
                title TEXT, creator TEXT, ts INTEGER,
                PRIMARY KEY(service, uid, pid)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE creators(
                service TEXT NOT NULL, id TEXT NOT NULL, name TEXT, public_id TEXT,
                favorited INTEGER DEFAULT 0, updated INTEGER DEFAULT 0,
                PRIMARY KEY(service, id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_creators_name ON creators(name)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    companion object {
        @Volatile
        private var instance: Db? = null

        fun get(context: Context): Db = instance ?: synchronized(this) {
            instance ?: Db(context.applicationContext).also { instance = it }
        }
    }
}

object Store {

    private fun db(context: Context): SQLiteDatabase = Db.get(context).writableDatabase

    // ------------------------------------------------------------ downloads

    fun addDownload(context: Context, post: Post, dir: String): Long {
        val values = ContentValues().apply {
            put("service", post.service)
            put("uid", post.user)
            put("pid", post.id)
            put("title", post.displayTitle)
            put("creator", post.user)
            put("total", 0)
            put("done", 0)
            put("state", DownloadState.WAITING)
            put("dir", dir)
            put("created", System.currentTimeMillis())
        }
        return db(context).insertWithOnConflict(
            "downloads", null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun updateDownload(context: Context, id: Long, done: Int? = null, total: Int? = null, state: Int? = null) {
        val values = ContentValues()
        done?.let { values.put("done", it) }
        total?.let { values.put("total", it) }
        state?.let { values.put("state", it) }
        if (values.size() == 0) return
        db(context).update("downloads", values, "_id = ?", arrayOf(id.toString()))
    }

    fun downloads(context: Context): List<DownloadRow> {
        val out = ArrayList<DownloadRow>()
        db(context).rawQuery(
            "SELECT _id, service, uid, pid, title, creator, total, done, state, dir, created " +
                "FROM downloads ORDER BY created DESC", null
        ).use { c ->
            while (c.moveToNext()) out.add(c.toDownload())
        }
        return out
    }

    fun download(context: Context, id: Long): DownloadRow? {
        db(context).rawQuery(
            "SELECT _id, service, uid, pid, title, creator, total, done, state, dir, created " +
                "FROM downloads WHERE _id = ?", arrayOf(id.toString())
        ).use { c -> return if (c.moveToFirst()) c.toDownload() else null }
    }

    fun nextWaiting(context: Context): DownloadRow? {
        db(context).rawQuery(
            "SELECT _id, service, uid, pid, title, creator, total, done, state, dir, created " +
                "FROM downloads WHERE state = ? ORDER BY created ASC LIMIT 1",
            arrayOf(DownloadState.WAITING.toString())
        ).use { c -> return if (c.moveToFirst()) c.toDownload() else null }
    }

    fun resetRunning(context: Context) {
        val values = ContentValues().apply { put("state", DownloadState.WAITING) }
        db(context).update("downloads", values, "state = ?", arrayOf(DownloadState.RUNNING.toString()))
    }

    fun deleteDownload(context: Context, id: Long) {
        db(context).delete("downloads", "_id = ?", arrayOf(id.toString()))
    }

    private fun Cursor.toDownload() = DownloadRow(
        id = getLong(0),
        service = getString(1) ?: "",
        uid = getString(2) ?: "",
        pid = getString(3) ?: "",
        title = getString(4) ?: "",
        creator = getString(5) ?: "",
        total = getInt(6),
        done = getInt(7),
        state = getInt(8),
        dir = getString(9) ?: "",
        created = getLong(10),
    )

    // -------------------------------------------------------------- history

    fun touchHistory(context: Context, post: Post, page: Int, total: Int) {
        val values = ContentValues().apply {
            put("service", post.service)
            put("uid", post.user)
            put("pid", post.id)
            put("title", post.displayTitle)
            put("creator", post.user)
            put("page", page)
            put("total", total)
            put("ts", System.currentTimeMillis())
        }
        db(context).insertWithOnConflict("history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun history(context: Context): List<HistoryRow> {
        val out = ArrayList<HistoryRow>()
        db(context).rawQuery(
            "SELECT service, uid, pid, title, creator, page, total, ts FROM history ORDER BY ts DESC LIMIT 200",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    HistoryRow(
                        service = c.getString(0) ?: "",
                        uid = c.getString(1) ?: "",
                        pid = c.getString(2) ?: "",
                        title = c.getString(3) ?: "",
                        creator = c.getString(4) ?: "",
                        page = c.getInt(5),
                        total = c.getInt(6),
                        ts = c.getLong(7),
                    )
                )
            }
        }
        return out
    }

    fun clearHistory(context: Context) {
        db(context).delete("history", null, null)
    }

    // ------------------------------------------------------------ favorites

    fun setLocalFavorite(context: Context, post: Post, favorite: Boolean) {
        if (favorite) {
            val values = ContentValues().apply {
                put("service", post.service)
                put("uid", post.user)
                put("pid", post.id)
                put("title", post.displayTitle)
                put("creator", post.user)
                put("ts", System.currentTimeMillis())
            }
            db(context).insertWithOnConflict("favs", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } else {
            db(context).delete(
                "favs", "service = ? AND uid = ? AND pid = ?",
                arrayOf(post.service, post.user, post.id)
            )
        }
    }

    fun isLocalFavorite(context: Context, post: Post): Boolean {
        db(context).rawQuery(
            "SELECT 1 FROM favs WHERE service = ? AND uid = ? AND pid = ?",
            arrayOf(post.service, post.user, post.id)
        ).use { c -> return c.moveToFirst() }
    }

    fun localFavorites(context: Context): List<Post> {
        val out = ArrayList<Post>()
        db(context).rawQuery(
            "SELECT service, uid, pid, title, creator, ts FROM favs ORDER BY ts DESC", null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Post(
                        id = c.getString(2),
                        user = c.getString(1),
                        service = c.getString(0),
                        title = c.getString(3),
                    )
                )
            }
        }
        return out
    }

    // ------------------------------------------------------------- creators

    fun clearCreators(context: Context) {
        db(context).delete("creators", null, null)
    }

    /** 分批写入，配合流式解析，避免 15 MB 的创作者列表整体进内存。 */
    fun insertCreators(context: Context, creators: List<Creator>): Int {
        if (creators.isEmpty()) return 0
        val database = db(context)
        database.beginTransaction()
        try {
            val statement = database.compileStatement(
                "INSERT OR REPLACE INTO creators(service, id, name, public_id, favorited, updated) VALUES(?,?,?,?,?,?)"
            )
            for (creator in creators) {
                statement.clearBindings()
                statement.bindString(1, creator.service ?: "")
                statement.bindString(2, creator.id ?: "")
                statement.bindString(3, creator.name ?: "")
                statement.bindString(4, creator.publicId ?: "")
                statement.bindLong(5, creator.favorited ?: 0L)
                statement.bindLong(6, (creator.updated ?: "0").toLongOrNull() ?: 0L)
                statement.executeInsert()
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        return creators.size
    }

    fun searchCreators(context: Context, query: String, service: String?, limit: Int = 60): List<Creator> {
        val out = ArrayList<Creator>()
        val args = ArrayList<String>()
        val where = StringBuilder()
        if (service != null) {
            where.append("service = ?")
            args.add(service)
        }
        if (query.isNotBlank()) {
            if (where.isNotEmpty()) where.append(" AND ")
            where.append("(name LIKE ? OR public_id LIKE ?)")
            args.add("%$query%")
            args.add("%$query%")
        }
        val sql = "SELECT service, id, name, public_id, favorited, updated FROM creators" +
            (if (where.isNotEmpty()) " WHERE $where" else "") +
            " ORDER BY favorited DESC LIMIT $limit"
        db(context).rawQuery(sql, args.toTypedArray()).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Creator(
                        id = c.getString(1),
                        name = c.getString(2),
                        publicId = c.getString(3),
                        service = c.getString(0),
                        favorited = c.getLong(4),
                        updated = c.getLong(5).toString(),
                    )
                )
            }
        }
        return out
    }

    fun creatorCount(context: Context): Int {
        db(context).rawQuery("SELECT COUNT(*) FROM creators", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }
}
