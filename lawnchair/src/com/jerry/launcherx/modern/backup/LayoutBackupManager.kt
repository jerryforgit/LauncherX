package com.jerry.launcherx.modern.backup

import android.content.Context
import android.content.SharedPreferences
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LayoutBackupManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "layout_backup_prefs", Context.MODE_PRIVATE
    )

    data class LayoutSnapshot(
        val timestamp: Long,
        val name: String,
        val profileId: String,
        val workspaceItems: List<WorkspaceEntry>,
        val hotseatItems: List<HotseatEntry>,
        val folders: List<FolderEntry>,
        val gridColumns: Int,
        val gridRows: Int,
        val hotseatColumns: Int
    )

    data class WorkspaceEntry(
        val itemId: Long,
        val itemType: Int,
        val packageName: String,
        val title: String,
        val cellX: Int,
        val cellY: Int,
        val spanX: Int,
        val spanY: Int,
        val screen: Int
    )

    data class HotseatEntry(
        val itemId: Long,
        val packageName: String,
        val title: String,
        val cellX: Int
    )

    data class FolderEntry(
        val folderId: Long,
        val title: String,
        val childItems: List<WorkspaceEntry>
    )

    fun createSnapshot(
        name: String,
        profileId: String,
        workspaceItems: List<ItemInfo>,
        folders: Map<Long, FolderInfo>,
        gridColumns: Int,
        gridRows: Int,
        hotseatColumns: Int
    ): LayoutSnapshot {
        val entries = workspaceItems.map { item ->
            WorkspaceEntry(
                itemId = item.id.toLong(),
                itemType = item.itemType,
                packageName = item.intent?.component?.packageName ?: "",
                title = item.title?.toString() ?: "",
                cellX = item.cellX,
                cellY = item.cellY,
                spanX = item.spanX,
                spanY = item.spanY,
                screen = item.screenId
            )
        }

        val hotseatEntries = workspaceItems
            .filter { it.container == -101 }
            .map { item ->
                HotseatEntry(
                    itemId = item.id.toLong(),
                    packageName = item.intent?.component?.packageName ?: "",
                    title = item.title?.toString() ?: "",
                    cellX = item.cellX
                )
            }

        val folderEntries = folders.map { (id, folderInfo) ->
            FolderEntry(
                folderId = id,
                title = folderInfo.title?.toString() ?: "",
                childItems = folderInfo.getContents().map { child ->
                    WorkspaceEntry(
                        itemId = child.id.toLong(),
                        itemType = child.itemType,
                        packageName = child.intent?.component?.packageName ?: "",
                        title = child.title?.toString() ?: "",
                        cellX = child.cellX,
                        cellY = child.cellY,
                        spanX = child.spanX,
                        spanY = child.spanY,
                        screen = child.screenId
                    )
                }
            )
        }

        return LayoutSnapshot(
            timestamp = System.currentTimeMillis(),
            name = name,
            profileId = profileId,
            workspaceItems = entries,
            hotseatItems = hotseatEntries,
            folders = folderEntries,
            gridColumns = gridColumns,
            gridRows = gridRows,
            hotseatColumns = hotseatColumns
        )
    }

    fun saveSnapshot(snapshot: LayoutSnapshot): Boolean {
        return try {
            val json = snapshotToJson(snapshot)
            val dir = getBackupDir()
            dir.mkdirs()
            val file = File(dir, "layout_${snapshot.timestamp}.json")
            FileWriter(file).use { it.write(json.toString(2)) }

            val index = getIndex().toMutableList()
            index.add(BackupIndexEntry(
                timestamp = snapshot.timestamp,
                name = snapshot.name,
                profileId = snapshot.profileId,
                fileName = file.name
            ))
            saveIndex(index)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun loadSnapshot(timestamp: Long): LayoutSnapshot? {
        return try {
            val file = File(getBackupDir(), "layout_${timestamp}.json")
            if (!file.exists()) return null
            val json = FileReader(file).use { JSONObject(it.readText()) }
            snapshotFromJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun listSnapshots(): List<BackupIndexEntry> {
        return getIndex().sortedByDescending { it.timestamp }
    }

    fun deleteSnapshot(timestamp: Long): Boolean {
        val file = File(getBackupDir(), "layout_${timestamp}.json")
        val deleted = file.delete()
        if (deleted) {
            val index = getIndex().filter { it.timestamp != timestamp }
            saveIndex(index)
        }
        return deleted
    }

    fun exportToFile(snapshot: LayoutSnapshot, file: File): Boolean {
        return try {
            val json = snapshotToJson(snapshot)
            FileWriter(file).use { it.write(json.toString(2)) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importFromFile(file: File): LayoutSnapshot? {
        return try {
            val json = FileReader(file).use { JSONObject(it.readText()) }
            snapshotFromJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun backupExists(timestamp: Long): Boolean {
        return File(getBackupDir(), "layout_${timestamp}.json").exists()
    }

    fun getBackupCount(): Int {
        return getIndex().size
    }

    private fun getBackupDir(): File {
        return File(context.filesDir, "layout_backups")
    }

    data class BackupIndexEntry(
        val timestamp: Long,
        val name: String,
        val profileId: String,
        val fileName: String
    )

    private fun getIndex(): List<BackupIndexEntry> {
        val jsonStr = prefs.getString("backup_index", "[]") ?: "[]"
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<BackupIndexEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                BackupIndexEntry(
                    timestamp = obj.getLong("timestamp"),
                    name = obj.getString("name"),
                    profileId = obj.getString("profileId"),
                    fileName = obj.getString("fileName")
                )
            )
        }
        return list
    }

    private fun saveIndex(index: List<BackupIndexEntry>) {
        val arr = JSONArray()
        for (entry in index) {
            arr.put(JSONObject().apply {
                put("timestamp", entry.timestamp)
                put("name", entry.name)
                put("profileId", entry.profileId)
                put("fileName", entry.fileName)
            })
        }
        prefs.edit().putString("backup_index", arr.toString()).apply()
    }

    private fun snapshotToJson(snapshot: LayoutSnapshot): JSONObject {
        return JSONObject().apply {
            put("timestamp", snapshot.timestamp)
            put("name", snapshot.name)
            put("profileId", snapshot.profileId)
            put("gridColumns", snapshot.gridColumns)
            put("gridRows", snapshot.gridRows)
            put("hotseatColumns", snapshot.hotseatColumns)

            put("workspaceItems", JSONArray().apply {
                snapshot.workspaceItems.forEach { entry ->
                    put(JSONObject().apply {
                        put("itemId", entry.itemId)
                        put("itemType", entry.itemType)
                        put("packageName", entry.packageName)
                        put("title", entry.title)
                        put("cellX", entry.cellX)
                        put("cellY", entry.cellY)
                        put("spanX", entry.spanX)
                        put("spanY", entry.spanY)
                        put("screen", entry.screen)
                    })
                }
            })

            put("hotseatItems", JSONArray().apply {
                snapshot.hotseatItems.forEach { entry ->
                    put(JSONObject().apply {
                        put("itemId", entry.itemId)
                        put("packageName", entry.packageName)
                        put("title", entry.title)
                        put("cellX", entry.cellX)
                    })
                }
            })

            put("folders", JSONArray().apply {
                snapshot.folders.forEach { folder ->
                    put(JSONObject().apply {
                        put("folderId", folder.folderId)
                        put("title", folder.title)
                        put("childItems", JSONArray().apply {
                            folder.childItems.forEach { child ->
                                put(JSONObject().apply {
                                    put("itemId", child.itemId)
                                    put("itemType", child.itemType)
                                    put("packageName", child.packageName)
                                    put("title", child.title)
                                    put("cellX", child.cellX)
                                    put("cellY", child.cellY)
                                    put("spanX", child.spanX)
                                    put("spanY", child.spanY)
                                    put("screen", child.screen)
                                })
                            }
                        })
                    })
                }
            })
        }
    }

    private fun snapshotFromJson(json: JSONObject): LayoutSnapshot {
        val workspaceItems = mutableListOf<WorkspaceEntry>()
        val wsArr = json.getJSONArray("workspaceItems")
        for (i in 0 until wsArr.length()) {
            val obj = wsArr.getJSONObject(i)
            workspaceItems.add(
                WorkspaceEntry(
                    itemId = obj.getLong("itemId"),
                    itemType = obj.getInt("itemType"),
                    packageName = obj.getString("packageName"),
                    title = obj.getString("title"),
                    cellX = obj.getInt("cellX"),
                    cellY = obj.getInt("cellY"),
                    spanX = obj.getInt("spanX"),
                    spanY = obj.getInt("spanY"),
                    screen = obj.getInt("screen")
                )
            )
        }

        val hotseatItems = mutableListOf<HotseatEntry>()
        val hsArr = json.getJSONArray("hotseatItems")
        for (i in 0 until hsArr.length()) {
            val obj = hsArr.getJSONObject(i)
            hotseatItems.add(
                HotseatEntry(
                    itemId = obj.getLong("itemId"),
                    packageName = obj.getString("packageName"),
                    title = obj.getString("title"),
                    cellX = obj.getInt("cellX")
                )
            )
        }

        val folders = mutableListOf<FolderEntry>()
        val fArr = json.getJSONArray("folders")
        for (i in 0 until fArr.length()) {
            val obj = fArr.getJSONObject(i)
            val children = mutableListOf<WorkspaceEntry>()
            val cArr = obj.getJSONArray("childItems")
            for (j in 0 until cArr.length()) {
                val cObj = cArr.getJSONObject(j)
                children.add(
                    WorkspaceEntry(
                        itemId = cObj.getLong("itemId"),
                        itemType = cObj.getInt("itemType"),
                        packageName = cObj.getString("packageName"),
                        title = cObj.getString("title"),
                        cellX = cObj.getInt("cellX"),
                        cellY = cObj.getInt("cellY"),
                        spanX = cObj.getInt("spanX"),
                        spanY = cObj.getInt("spanY"),
                        screen = cObj.getInt("screen")
                    )
                )
            }
            folders.add(
                FolderEntry(
                    folderId = obj.getLong("folderId"),
                    title = obj.getString("title"),
                    childItems = children
                )
            )
        }

        return LayoutSnapshot(
            timestamp = json.getLong("timestamp"),
            name = json.getString("name"),
            profileId = json.getString("profileId"),
            workspaceItems = workspaceItems,
            hotseatItems = hotseatItems,
            folders = folders,
            gridColumns = json.getInt("gridColumns"),
            gridRows = json.getInt("gridRows"),
            hotseatColumns = json.getInt("hotseatColumns")
        )
    }
}
