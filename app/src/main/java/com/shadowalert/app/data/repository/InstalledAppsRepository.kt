package com.shadowalert.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.shadowalert.app.domain.model.AppCategory
import com.shadowalert.app.domain.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    suspend fun getLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }

        val selfPackage = context.packageName
        val seenPackages = HashSet<String>()
        val result = mutableListOf<InstalledAppInfo>()

        for (info in resolveInfos) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == selfPackage || !seenPackages.add(pkg)) continue

            val label = info.loadLabel(packageManager)?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: pkg

            val appInfo = info.activityInfo?.applicationInfo
            val category = inferCategory(pkg, label, appInfo)
            result += InstalledAppInfo(
                packageName = pkg,
                appName = label,
                suggestedCategory = category
            )
        }

        result.sortedBy { it.appName.lowercase() }
    }

    fun getAppIcon(packageName: String): Drawable? {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun isAppInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getLaunchIntent(packageName: String): Intent? {
        return packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun inferCategory(
        packageName: String,
        appName: String,
        appInfo: ApplicationInfo?
    ): AppCategory {
        if (appInfo != null) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
                ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
                ApplicationInfo.CATEGORY_VIDEO,
                ApplicationInfo.CATEGORY_AUDIO,
                ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.ENTERTAINMENT
                ApplicationInfo.CATEGORY_NEWS -> return AppCategory.NEWS
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.FOCUS
            }
        }

        val lowerPkg = packageName.lowercase()
        val lowerName = appName.lowercase()
        return when {
            listOf("instagram", "facebook", "whatsapp", "snapchat", "twitter", "x.android", "reddit", "tiktok", "discord", "telegram", "linkedin", "threads")
                .any { it in lowerPkg || it in lowerName } -> AppCategory.SOCIAL

            listOf("youtube", "netflix", "spotify", "twitch", "prime", "disney", "hotstar", "hulu", "music", "video", "reels")
                .any { it in lowerPkg || it in lowerName } -> AppCategory.ENTERTAINMENT

            listOf("news", "times", "bbc", "cnn", "flipboard", "inshorts", "medium", "substack")
                .any { it in lowerPkg || it in lowerName } -> AppCategory.NEWS

            listOf("game", "chess", "clash", "pubg", "roblox", "minecraft", "candy", "ludo")
                .any { it in lowerPkg || it in lowerName } -> AppCategory.GAMES

            else -> AppCategory.FOCUS
        }
    }
}
