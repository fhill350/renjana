package com.fesu.renjana.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import com.fesu.renjana.models.AppInfo
import com.fesu.renjana.utils.RenjanaLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * Everything the stub process needs to boot a guest properly, parsed from the
 * guest APK's manifest. Without this, the stub can only guess at the guest's
 * Application class, theme, and providers — which is why guest apps crashed
 * on launch (SDKs initialized in Application.onCreate were never run).
 */
data class GuestLaunchInfo(
    /** Manifest package name of the guest APK. */
    val packageName: String?,
    /** Fully-qualified <application android:name> class, or null when the guest uses the default Application. */
    val applicationClass: String?,
    /** The REAL ActivityInfo of the target activity (theme, launchMode, softInputMode, etc.). */
    val activityInfo: android.content.pm.ActivityInfo?,
    /** ContentProviders declared by the guest (FirebaseInitProvider, FileProvider, ...). */
    val providers: List<android.content.pm.ProviderInfo>,
    /**
     * `<application android:theme>` resource ID. Activities without their own
     * android:theme inherit this — skipping it leaves the guest themeless and
     * AppCompatActivity dies with "You need to use a Theme.AppCompat theme".
     */
    val applicationTheme: Int = 0
)

class ApkLoader(private val context: Context) {

    companion object {
        private const val TAG = "ApkLoader"

        internal fun qualifyActivityName(packageName: String?, activityName: String): String {
            return when {
                activityName.startsWith(".") -> "${packageName.orEmpty()}$activityName"
                '.' in activityName -> activityName
                packageName.isNullOrBlank() -> activityName
                else -> "$packageName.$activityName"
            }
        }
    }

    /**
     * Parse the guest launch info for a specific activity. Synchronous because
     * StubActivity.onCreate needs it before the guest can be instantiated;
     * archive parsing of a single APK is a few milliseconds.
     */
    fun getGuestLaunchInfo(apkPath: String, activityClassName: String): GuestLaunchInfo {
        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(
                apkPath,
                PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA or PackageManager.GET_PROVIDERS
            ) ?: return GuestLaunchInfo(null, null, null, emptyList(), 0)

            val appInfo = packageInfo.applicationInfo
            if (appInfo != null) {
                // Archive parsing fills these in, but pin them to the real path so
                // resources/native libs resolve regardless of parser quirks.
                appInfo.sourceDir = apkPath
                appInfo.publicSourceDir = apkPath
            }

            val appClass = appInfo?.className?.takeIf {
                it.isNotBlank() && it != android.app.Application::class.java.name
            }

            val simpleWanted = activityClassName.substringAfterLast('.')
            val activityInfo = packageInfo.activities?.firstOrNull { info ->
                info.name == activityClassName || info.name.substringAfterLast('.') == simpleWanted
            }?.apply {
                if (applicationInfo == null && appInfo != null) applicationInfo = appInfo
            }

            val providers = packageInfo.providers.orEmpty()
                .filter { it.name.isNotBlank() && it.enabled }
                .onEach { provider ->
                    if (provider.applicationInfo == null && appInfo != null) {
                        provider.applicationInfo = appInfo
                    }
                }

            RenjanaLog.i(
                TAG,
                "Guest launch info for ${packageInfo.packageName}: app=$appClass, " +
                    "activity=${activityInfo?.name}, providers=${providers.size}"
            )

            GuestLaunchInfo(
                packageInfo.packageName,
                appClass,
                activityInfo,
                providers,
                appInfo?.theme ?: 0
            )
        } catch (e: Throwable) {
            RenjanaLog.w(TAG, "Failed to parse guest launch info: ${e.message}")
            GuestLaunchInfo(null, null, null, emptyList(), 0)
        }
    }

    suspend fun parseApk(apkPath: String): AppInfo = withContext(Dispatchers.IO) {
        RenjanaLog.d(TAG, "Parsing APK: $apkPath")
        
        try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(
                apkPath,
                PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES
            ) ?: throw ApkLoadException("Failed to parse APK: getPackageArchiveInfo returned null")

            val appInfo = AppInfo(
                packageName = packageInfo.packageName ?: throw ApkLoadException("No package name"),
                appName = packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: packageInfo.packageName,
                versionName = packageInfo.versionName ?: "1.0",
                versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                },
                apkPath = apkPath,
                iconPath = null,
                installedDate = System.currentTimeMillis(),
                updatedDate = System.currentTimeMillis(),
                isSystemApp = false
            )
            
            RenjanaLog.i(TAG, "Parsed APK: ${appInfo.packageName} v${appInfo.versionName}")
            appInfo
        } catch (e: Exception) {
            RenjanaLog.e(TAG, "Failed to parse APK: $apkPath")
            throw ApkLoadException("Failed to parse APK: ${e.message}", e)
        }
    }

    suspend fun extractResources(apkPath: String, instanceDataPath: String): File = 
        withContext(Dispatchers.IO) {
            RenjanaLog.d(TAG, "Extracting resources from: $apkPath")
            
            val resDir = File(instanceDataPath, "res").apply { mkdirs() }
            
            try {
                ZipFile(apkPath).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.name == "resources.arsc" || entry.name.startsWith("res/")) {
                            val outFile = File(instanceDataPath, entry.name)
                            outFile.parentFile?.mkdirs()
                            
                            if (!entry.isDirectory) {
                                zip.getInputStream(entry).use { input ->
                                    FileOutputStream(outFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        }
                    }
                }
                
                RenjanaLog.i(TAG, "Extracted resources to: $resDir")
                resDir
            } catch (e: Exception) {
                RenjanaLog.e(TAG, "Failed to extract resources")
                throw ApkLoadException("Failed to extract resources: ${e.message}", e)
            }
        }

    suspend fun extractManifest(apkPath: String): String = withContext(Dispatchers.IO) {
        RenjanaLog.d(TAG, "Extracting manifest from: $apkPath")
        
        try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(
                apkPath,
                PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES or PackageManager.GET_PERMISSIONS
            ) ?: throw ApkLoadException("Failed to parse manifest")

            val manifest = buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                appendLine("<manifest package=\"${packageInfo.packageName}\">")
                appendLine("  <application android:label=\"${packageInfo.applicationInfo?.loadLabel(pm)}\" />")
                packageInfo.activities?.forEach { activity ->
                    appendLine("  <activity android:name=\"${activity.name}\" android:exported=\"${activity.exported}\" />")
                }
                appendLine("</manifest>")
            }
            
            RenjanaLog.i(TAG, "Manifest extracted successfully")
            manifest
        } catch (e: Exception) {
            RenjanaLog.e(TAG, "Failed to extract manifest")
            throw ApkLoadException("Failed to extract manifest: ${e.message}", e)
        }
    }

    suspend fun getLauncherActivity(apkPath: String, packageName: String? = null): String? = withContext(Dispatchers.IO) {
        RenjanaLog.d(TAG, "Finding launcher activity in: $apkPath (package: $packageName)")

        // 1. Try system PackageManager if app is installed (most reliable on Android 10-15)
        if (!packageName.isNullOrBlank()) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                val className = launchIntent?.component?.className
                if (!className.isNullOrBlank()) {
                    RenjanaLog.i(TAG, "Found launcher activity via PackageManager: $className")
                    return@withContext className
                }
            } catch (e: Exception) {
                RenjanaLog.w(TAG, "Failed to get launch intent for $packageName: ${e.message}")
            }
        }
        
        try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(
                apkPath,
                PackageManager.GET_ACTIVITIES
            )
            val resolvedPkg = packageName ?: packageInfo?.packageName
            if (!resolvedPkg.isNullOrBlank()) {
                try {
                    val launchIntent = pm.getLaunchIntentForPackage(resolvedPkg)
                    val className = launchIntent?.component?.className
                    if (!className.isNullOrBlank()) {
                        RenjanaLog.i(TAG, "Found launcher activity via resolved pkg: $className")
                        return@withContext className
                    }
                } catch (_: Exception) {}
            }
            val activities = packageInfo?.activities.orEmpty()

            findMainLauncherActivity(apkPath, packageName)?.let { launcherActivity ->
                RenjanaLog.i(TAG, "Found MAIN/LAUNCHER activity: $launcherActivity")
                return@withContext launcherActivity
            }

            activities.firstOrNull { it.exported }?.let { activity ->
                RenjanaLog.i(TAG, "Found exported activity fallback: ${activity.name}")
                return@withContext activity.name
            }
                
            activities.firstOrNull()?.name?.let { firstActivity ->
                RenjanaLog.w(TAG, "No exported activity found, using first: $firstActivity")
                return@withContext firstActivity
            }
            
            RenjanaLog.w(TAG, "No launcher activity found")
            null
        } catch (e: Exception) {
            RenjanaLog.e(TAG, "Failed to find launcher activity")
            null
        }
    }

    private fun findMainLauncherActivity(apkPath: String, packageName: String?): String? {
        return try {
            val parserClass = Class.forName("android.content.pm.PackageParser")
            val parser = parserClass.getDeclaredConstructor().newInstance()
            val parsePackage = parserClass.getDeclaredMethod("parsePackage", File::class.java, Integer.TYPE)
            val parsedPackage = parsePackage.invoke(parser, File(apkPath), 0) ?: return null
            val parsedPackageName = getFieldValue(parsedPackage, "packageName") as? String ?: packageName
            val activities = getFieldValue(parsedPackage, "activities") as? Iterable<*> ?: return null

            for (activity in activities) {
                if (activity == null) continue
                val filters = getFieldValue(activity, "intents") as? Iterable<*> ?: continue
                val matchesLauncher = filters.any { filter ->
                    (filter as? IntentFilter)?.hasAction(Intent.ACTION_MAIN) == true &&
                        filter.hasCategory(Intent.CATEGORY_LAUNCHER)
                }
                if (matchesLauncher) {
                    val className = getFieldValue(activity, "className") as? String ?: continue
                    return qualifyActivityName(parsedPackageName, className)
                }
            }

            null
        } catch (e: Exception) {
            RenjanaLog.w(TAG, "Failed to inspect launcher intent filters: ${e.message}")
            null
        }
    }

    private fun getFieldValue(target: Any, name: String): Any? {
        var currentClass: Class<*>? = target.javaClass
        while (currentClass != null) {
            try {
                val field = currentClass.getDeclaredField(name)
                field.isAccessible = true
                return field.get(target)
            } catch (_: NoSuchFieldException) {
                currentClass = currentClass.superclass
            }
        }
        return null
    }

    suspend fun extractNativeLibraries(
        apkPath: String,
        instanceDataPath: String,
        abi: String = "arm64-v8a"
    ): File = withContext(Dispatchers.IO) {
        RenjanaLog.d(TAG, "Extracting native libraries for ABI: $abi")
        
        val libDir = File(instanceDataPath, "lib").apply { mkdirs() }
        val libPath = "lib/$abi"
        
        try {
            ZipFile(apkPath).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith(libPath) && entry.name.endsWith(".so")) {
                        val soName = File(entry.name).name
                        val outFile = File(libDir, soName)
                        
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        RenjanaLog.d(TAG, "Extracted: $soName")
                    }
                }
            }
            
            RenjanaLog.i(TAG, "Native libraries extracted to: $libDir")
            libDir
        } catch (e: Exception) {
            RenjanaLog.w(TAG, "Failed to extract native libraries: ${e.message}")
            libDir
        }
    }
}

class ApkLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
