package com.jerry.launcherx.modern.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.content.pm.PackageManager

class UniversalSearchEngine(private val context: Context) {

    data class SearchResult(
        val id: String,
        val title: String,
        val subtitle: String,
        val icon: Drawable?,
        val type: ResultType,
        val rank: Float = 0f,
        val intent: Intent? = null,
        val clipboardText: String? = null
    )

    enum class ResultType {
        APP,
        CONTACT,
        FILE,
        CLIPBOARD,
        WEB,
        SHORTCUT,
        SETTINGS,
        WIDGET
    }

    private val appSearchProvider = AppSearchProvider(context)
    private val contactSearchProvider = ContactSearchProvider(context)
    private val fileSearchProvider = FileSearchProvider(context)
    private val clipboardSearchProvider = ClipboardSearchProvider(context)
    private val settingsSearchProvider = SettingsSearchProvider(context)

    fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<SearchResult>()

        results.addAll(appSearchProvider.search(query))
        results.addAll(contactSearchProvider.search(query))
        results.addAll(fileSearchProvider.search(query))
        results.addAll(clipboardSearchProvider.search(query))
        results.addAll(settingsSearchProvider.search(query))

        return results.sortedByDescending { it.rank }
    }

    fun searchApps(query: String): List<SearchResult> {
        return appSearchProvider.search(query)
    }

    fun searchContacts(query: String): List<SearchResult> {
        return contactSearchProvider.search(query)
    }

    fun searchFiles(query: String): List<SearchResult> {
        return fileSearchProvider.search(query)
    }

    fun searchClipboard(query: String): List<SearchResult> {
        return clipboardSearchProvider.search(query)
    }

    fun launchResult(result: SearchResult, context: Context) {
        result.intent?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    interface SearchProvider {
        fun search(query: String): List<SearchResult>
    }

    class AppSearchProvider(private val context: Context) : SearchProvider {
        private val pm: PackageManager = context.packageManager

        override fun search(query: String): List<SearchResult> {
            val results = mutableListOf<SearchResult>()
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)

            for (ri in resolveInfos) {
                val label = ri.loadLabel(pm).toString()
                if (label.contains(query, ignoreCase = true)) {
                    val appIntent = pm.getLaunchIntentForPackage(
                        ri.activityInfo.packageName
                    )
                    results.add(
                        SearchResult(
                            id = "app_${ri.activityInfo.packageName}",
                            title = label,
                            subtitle = ri.activityInfo.packageName,
                            icon = ri.loadIcon(pm),
                            type = ResultType.APP,
                            rank = calculateAppRank(label, query),
                            intent = appIntent
                        )
                    )
                }
            }

            return results.sortedByDescending { it.rank }.take(20)
        }

        private fun calculateAppRank(label: String, query: String): Float {
            val lowerLabel = label.lowercase()
            val lowerQuery = query.lowercase()
            return when {
                lowerLabel == lowerQuery -> 100f
                lowerLabel.startsWith(lowerQuery) -> 90f
                lowerLabel.contains(lowerQuery) -> 70f
                lowerLabel.split(" ").any { it.startsWith(lowerQuery) } -> 50f
                levenshteinDistance(lowerLabel, lowerQuery) <= 2 -> 30f
                else -> 10f
            }
        }

        private fun levenshteinDistance(s1: String, s2: String): Int {
            val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
            for (i in 0..s1.length) dp[i][0] = i
            for (j in 0..s2.length) dp[0][j] = j
            for (i in 1..s1.length) {
                for (j in 1..s2.length) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    dp[i][j] = minOf(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + cost
                    )
                }
            }
            return dp[s1.length][s2.length]
        }
    }

    class ContactSearchProvider(private val context: Context) : SearchProvider {

        override fun search(query: String): List<SearchResult> {
            val results = mutableListOf<SearchResult>()
            val uri = ContactsContract.Contacts.CONTENT_FILTER_URI.buildUpon()
                .appendPath(query)
                .build()

            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.DISPLAY_NAME,
                        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
                    ),
                    null, null, null
                )

                cursor?.let {
                    val idIdx = it.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIdx = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val photoIdx = it.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)

                    while (it.moveToNext()) {
                        val id = it.getLong(idIdx)
                        val name = it.getString(nameIdx) ?: continue
                        val photoUri = it.getString(photoIdx)

                        val contactIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.withAppendedPath(
                                ContactsContract.Contacts.CONTENT_URI,
                                id.toString()
                            )
                        }

                        results.add(
                            SearchResult(
                                id = "contact_$id",
                                title = name,
                                subtitle = "Contact",
                                icon = null,
                                type = ResultType.CONTACT,
                                rank = 80f,
                                intent = contactIntent
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                return emptyList()
            } finally {
                cursor?.close()
            }

            return results.take(10)
        }
    }

    class FileSearchProvider(private val context: Context) : SearchProvider {

        override fun search(query: String): List<SearchResult> {
            val results = mutableListOf<SearchResult>()
            val uri = MediaStore.Files.getContentUri("external")

            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    uri,
                    arrayOf(
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        MediaStore.Files.FileColumns.DATA,
                        MediaStore.Files.FileColumns.MIME_TYPE
                    ),
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$query%"),
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                )

                cursor?.let {
                    val idIdx = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
                    val nameIdx = it.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val dataIdx = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    val mimeIdx = it.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)

                    while (it.moveToNext() && results.size < 10) {
                        val id = it.getLong(idIdx)
                        val name = it.getString(nameIdx) ?: continue
                        val path = it.getString(dataIdx) ?: continue
                        val mime = it.getString(mimeIdx) ?: ""

                        val fileIntent = Intent(Intent.ACTION_VIEW).apply {
                            val file = java.io.File(path)
                            setDataAndType(
                                Uri.fromFile(file),
                                mime.ifEmpty { "*/*" }
                            )
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        results.add(
                            SearchResult(
                                id = "file_$id",
                                title = name,
                                subtitle = path,
                                icon = null,
                                type = ResultType.FILE,
                                rank = 40f,
                                intent = fileIntent
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                return emptyList()
            } finally {
                cursor?.close()
            }

            return results
        }
    }

    class ClipboardSearchProvider(private val context: Context) : SearchProvider {

        override fun search(query: String): List<SearchResult> {
            val results = mutableListOf<SearchResult>()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as? ClipboardManager ?: return results

            val clipData = clipboard.primaryClip ?: return results

            for (i in 0 until clipData.itemCount) {
                val item = clipData.getItemAt(i)
                val text = item.coerceToText(context)?.toString() ?: continue

                if (text.contains(query, ignoreCase = true)) {
                    results.add(
                        SearchResult(
                            id = "clipboard_$i",
                            title = text.take(50),
                            subtitle = "Clipboard",
                            icon = null,
                            type = ResultType.CLIPBOARD,
                            rank = 20f,
                            clipboardText = text
                        )
                    )
                }
            }

            return results.take(5)
        }
    }

    class SettingsSearchProvider(private val context: Context) : SearchProvider {

        private data class SettingEntry(
            val title: String,
            val keyword: String,
            val intent: Intent
        )

        private val settingsEntries = listOf(
            SettingEntry("Wi-Fi", "wifi network wireless",
                Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)),
            SettingEntry("Bluetooth", "bluetooth bt",
                Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)),
            SettingEntry("Display", "display screen brightness",
                Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)),
            SettingEntry("Sound", "sound audio volume ringtone",
                Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)),
            SettingEntry("Battery", "battery power charge",
                Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS)),
            SettingEntry("Storage", "storage memory space",
                Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
            SettingEntry("Apps", "applications app list manage",
                Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS)),
            SettingEntry("Security", "security lock screen password fingerprint",
                Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)),
            SettingEntry("Location", "location gps geo",
                Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)),
            SettingEntry("Date & Time", "date time timezone clock",
                Intent(android.provider.Settings.ACTION_DATE_SETTINGS)),
            SettingEntry("Accessibility", "accessibility a11y",
                Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)),
            SettingEntry("Developer Options", "developer debug usb adb",
                Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)),
            SettingEntry("About Phone", "about device info phone model",
                Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS)),
            SettingEntry("Hotspot", "hotspot tethering wifi share",
                Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)),
            SettingEntry("NFC", "nfc contactless payment",
                Intent(android.provider.Settings.ACTION_NFC_SETTINGS)),
            SettingEntry("Dark Mode", "dark theme night",
                Intent(android.provider.Settings.ACTION_DARK_THEME_SETTINGS)),
            SettingEntry("Wallpaper", "wallpaper background",
                Intent("android.intent.action.SET_WALLPAPER"))
        )

        override fun search(query: String): List<SearchResult> {
            val lowerQuery = query.lowercase()
            return settingsEntries
                .filter { entry ->
                    entry.keyword.lowercase().contains(lowerQuery) ||
                    entry.title.lowercase().contains(lowerQuery)
                }
                .map { entry ->
                    SearchResult(
                        id = "settings_${entry.title}",
                        title = entry.title,
                        subtitle = "Settings",
                        icon = null,
                        type = ResultType.SETTINGS,
                        rank = 60f,
                        intent = entry.intent
                    )
                }
                .take(5)
        }
    }
}
