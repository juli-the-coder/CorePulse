package com.example.telemetry

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameDiscoveryRepository(private val context: Context) {

    suspend fun getLaunchableAppsAndGames(): List<InstalledGameInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val ownPkg = context.packageName
        val seen = HashSet<String>()
        val results = mutableListOf<InstalledGameInfo>()

        for (ri in resolveInfos) {
            val activityInfo = ri.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (pkg == ownPkg || !seen.add(pkg)) continue

            val appInfo = activityInfo.applicationInfo ?: continue
            val isTaggedGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == ApplicationInfo.CATEGORY_GAME
            } else {
                @Suppress("DEPRECATION")
                (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            }

            val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg)
            val icon = runCatching { ri.loadIcon(pm) }.getOrNull()
            val versionName = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L)).versionName ?: "1.0"
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0).versionName ?: "1.0"
                }
            }.getOrDefault("1.0")

            results.add(
                InstalledGameInfo(
                    packageName = pkg,
                    appName = label,
                    versionName = versionName,
                    isTaggedGame = isTaggedGame,
                    icon = icon
                )
            )
        }

        results.sortedWith(
            compareByDescending<InstalledGameInfo> { it.isTaggedGame }
                .thenBy { it.appName.lowercase() }
        )
    }

    fun buildLaunchIntent(packageName: String): Intent? {
        return context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
