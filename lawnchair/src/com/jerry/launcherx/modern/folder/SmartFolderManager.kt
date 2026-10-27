package com.jerry.launcherx.modern.folder

import android.content.Context
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo

class SmartFolderManager(private val context: Context) {

    private val categoryDetector = AppCategoryDetector(context)

    fun createSmartFolder(items: List<ItemInfo>, name: String): FolderInfo {
        val folderInfo = FolderInfo().apply {
            this.title = name
        }
        items.forEach { itemInfo ->
            folderInfo.add(itemInfo)
        }
        return folderInfo
    }

    fun categorizeFolderItems(folderInfo: FolderInfo): Map<LargeFolderView.AppCategory, List<ItemInfo>> {
        val result = mutableMapOf<LargeFolderView.AppCategory, MutableList<ItemInfo>>()
        folderInfo.getContents().forEach { itemInfo ->
            val category = categoryDetector.detect(itemInfo)
            result.getOrPut(category) { mutableListOf() }.add(itemInfo)
        }
        return result
    }

    fun detectCategory(itemInfo: ItemInfo): LargeFolderView.AppCategory {
        return categoryDetector.detect(itemInfo)
    }

    fun autoSortByCategory(folderInfo: FolderInfo): List<ItemInfo> {
        return folderInfo.getContents().sortedBy {
            categoryDetector.detect(it).ordinal
        }
    }

    fun autoSortByFrequency(
        folderInfo: FolderInfo,
        launchCounts: Map<String, Int>
    ): List<ItemInfo> {
        return folderInfo.getContents().sortedByDescending { item ->
            val pkg = item.intent?.component?.packageName ?: ""
            launchCounts[pkg] ?: 0
        }
    }

    private class AppCategoryDetector(private val context: Context) {

        private val socialPackages = setOf(
            "com.tencent.mm", "com.tencent.mobileqq", "com.tencent.tim",
            "com.sina.weibo", "com.ss.android.ugc.aweme",
            "com.facebook.katana", "com.twitter.android",
            "com.instagram.android", "com.whatsapp",
            "org.telegram.messenger", "com.snapchat.android"
        )

        private val productivityPackages = setOf(
            "com.microsoft.office.outlook",
            "com.microsoft.office.word",
            "com.microsoft.office.excel",
            "com.microsoft.office.powerpoint",
            "com.google.android.apps.docs.editors.docs",
            "com.google.android.apps.docs.editors.sheets",
            "com.google.android.apps.docs.editors.slides",
            "com.notion.zone", "com.slack.android",
            "com.asana.app", "com.toggl.timer",
            "com.todoist", "com.microsoft.to-do"
        )

        private val entertainmentPackages = setOf(
            "com.ss.android.ugc.aweme", "com.kuaishou.nebula",
            "com.youku.phone", "com.iqiyi.i18n",
            "tv.danmaku.bili", "com.netflix.mediaclient",
            "com.spotify.music", "com.apple.android.music",
            "com.kugou.android", "com.kuwo.player",
            "com.tencent.tmgp.sgame", "com.tencent.ig"
        )

        private val toolsPackages = setOf(
            "com.android.chrome", "com.android.calculator2",
            "com.android.calendar", "com.android.clock",
            "com.android.settings", "com.android.camera",
            "com.android.gallery3d", "com.google.android.apps.maps",
            "com.autonavi.minimap", "com.baidu.BaiduMap",
            "com.didi.passenger", "com.amap.android"
        )

        private val financePackages = setOf(
            "com.eg.android.AlipayGphone", "com.tencent.mm",
            "com.unionpay", "cmb.pb",
            "com.icbc", "com.chinamworld.bocmbci",
            "com.google.android.apps.walletnfcrel"
        )

        fun detect(itemInfo: ItemInfo): LargeFolderView.AppCategory {
            val pkg = itemInfo.intent?.component?.packageName ?: ""
            return when {
                socialPackages.contains(pkg) -> LargeFolderView.AppCategory.SOCIAL
                productivityPackages.contains(pkg) -> LargeFolderView.AppCategory.PRODUCTIVITY
                entertainmentPackages.contains(pkg) -> LargeFolderView.AppCategory.ENTERTAINMENT
                toolsPackages.contains(pkg) -> LargeFolderView.AppCategory.TOOLS
                financePackages.contains(pkg) -> LargeFolderView.AppCategory.FINANCE
                else -> guessCategoryFromFlags(itemInfo)
            }
        }

        private fun guessCategoryFromFlags(itemInfo: ItemInfo): LargeFolderView.AppCategory {
            return when (itemInfo.itemType) {
                LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET -> LargeFolderView.AppCategory.TOOLS
                LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT -> LargeFolderView.AppCategory.TOOLS
                LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT -> LargeFolderView.AppCategory.TOOLS
                else -> LargeFolderView.AppCategory.OTHER
            }
        }
    }
}
