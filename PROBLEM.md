# Log Masalah & Kendala Virtual Engine Renjana (PROBLEM.md)

Dokumen ini mencatat setiap kendala teknis, akar masalah (*root cause*), dan solusi yang diterapkan dalam pengembangan dan perbaikan isolasi container / virtualisasi subprocess (`:p0` - `:p9`).

---

## 1. Crash `VectorDrawableCompat` di `ResourceManagerInternal`
- **Gejala / Error:**
  ```text
  java.lang.IllegalStateException: This app has been built with an incorrect configuration. Please configure your build for VectorDrawableCompat.
  at androidx.appcompat.widget.ResourceManagerInternal.checkVectorDrawableSetup(ResourceManagerInternal.java:...)
  ```
- **Akar Masalah:**
  Classloader APK guest memiliki instance `androidx.appcompat` independen yang memeriksa drawable `abc_vector_test`. Karena resource ID antara host dan guest berbeda, verifikasi drawable gagal.
- **Solusi:**
  1. Memanggil `AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)` pada host dan guest ClassLoader.
  2. Melakukan manipulasi refleksi field `mHasCheckedVectorDrawableSetup = true` pada kelas `ResourceManagerInternal` di kedua classloader.
  3. Menerapkan Pine method hook pada `ResourceManagerInternal.checkVectorDrawableSetup(Context)` untuk langsung me-return `true`.

---

## 2. Crash `ActivityInfo.parentActivityName` NPE di Android 14/15
- **Gejala / Error:**
  ```text
  java.lang.NullPointerException: Attempt to read from field 'java.lang.String android.content.pm.ActivityInfo.parentActivityName' on a null object reference
  at android.app.Activity.onCreate(Activity.java:...)
  ```
- **Akar Masalah:**
  Pada AOSP Android 14 & 15 (SDK 34 & 35), lifecycle `Activity.onCreate()` mengakses `mActivityInfo.parentActivityName`. Saat Activity guest dibuat via refleksi tanpa framework `ActivityThread.performLaunchActivity`, field `mActivityInfo` bernilai `null`.
- **Solusi:**
  Mengkloning `ActivityInfo` milik `StubActivity` dan menginjeksinya ke field `mActivityInfo` pada `Activity` guest.

---

## 3. Crash `ComponentActivity.getViewModelStore` / `Activity.attach`
- **Gejala / Error:**
  ```text
  java.lang.IllegalStateException: Your activity is not yet attached to the Application instance. You can't request ViewModel before onCreate call.
  at androidx.activity.ComponentActivity.getViewModelStore(ComponentActivity.java:...)
  at androidx.fragment.app.FragmentActivity$HostCallbacks.getViewModelStore(...)
  ```
- **Akar Masalah:**
  AndroidX `ComponentActivity` dan `FragmentManager` mewajibkan `Activity.getApplication()` mengembalikan objek `Application` yang valid. Injeksi refleksi parameter `Activity.attach` sempat salah urutan (`Context::class.java.isAssignableFrom` mengevaluasi `Application` sebagai `Context` biasa sehingga passing `StubActivity` alih-alih `application`).
- **Solusi:**
  1. Memperbaiki urutan pencocokan parameter `Activity.attach` agar tipe `Application` diinjeksi dengan instance `application` host.
  2. Memasang Pine hook pada `Activity.getApplication()` agar selalu mengembalikan instance `Application` container yang valid.
  3. Menginjeksi seluruh field hierarki (`mBase`, `mApplication`, `mActivityInfo`, `mToken`, `mWindow`, `mWindowManager`, `mUiThread`).

---

## 4. Crash `Resources$NotFoundException` pada Split APK (App Bundle)
- **Gejala / Error:**
  ```text
  android.content.res.Resources$NotFoundException: Drawable (missing name) with resource ID #0x7f0801e7
  at android.content.res.ResourcesImpl.loadDrawableForCookie(...)
  at android.widget.ImageView.<init>(ImageView.java:...)
  ```
- **Akar Masalah:**
  Aplikasi modern (seperti Cloudflare 1.1.1.1) didistribusikan sebagai **Split APK** (`base.apk`, `split_config.arm64_v8a.apk`, `split_config.xxhdpi.apk`, `split_config.in.apk`). `VirtualClassLoader` sebelumnya hanya menambahkan `base.apk` ke `AssetManager`, sehingga resource grafis/drawable resolusi tinggi (xxhdpi) yang berada di split APK terpisah tidak dapat ditemukan.
- **Solusi:**
  1. Menambahkan fungsi `resolveAllApkPaths()` untuk otomatis mendeteksi semua file split APK sibling (`split_config.*.apk`).
  2. Mendaftarkan seluruh split APK ke `DexClassLoader` (multi-dex path) dan `AssetManager.addAssetPath()`.
  3. Mengintegrasikan `PackageManager.getResourcesForApplication()` untuk memuat resource lengkap termasuk resource sistem Android (`framework-res.apk`).

---

## 5. Crash `PackageManager$NameNotFoundException` pada `AppCompatDelegateImpl`
- **Gejala / Error:**
  ```text
  android.content.pm.PackageManager$NameNotFoundException: ComponentInfo{com.fesu.renjana/com.cloudflare.app.presentation.main.SplashActivity}
  at android.app.ApplicationPackageManager.getActivityInfo(ApplicationPackageManager.java:604)
  at androidx.appcompat.app.AppCompatDelegateImpl.onCreate(...)
  at com.cloudflare.app.presentation.main.SplashActivity.onCreate(...)
  ```
- **Akar Masalah:**
  `AppCompatActivity.onCreate()` memanggil `packageManager.getActivityInfo(getComponentName())` untuk membaca tema & atribut Manifest. Karena `mComponent` di-set ke `ComponentName("com.fesu.renjana", "com.cloudflare.app.presentation.main.SplashActivity")`, PackageManager sistem mencari Activity guest di dalam Manifest host `com.fesu.renjana`, bukan Manifest guest.
- **Solusi:**
  1. Mengatur `mComponent` pada `guestActivity` agar menggunakan `this.componentName` (komponen `StubActivity` yang sah dan terdaftar di Manifest host).
  2. Memasang hook fallback pada `PackageManager.getActivityInfo` via Pine untuk mengembalikan `stubActInfo` jika ada query ke komponen guest.

---

## 6. Crash `Resources$NotFoundException` saat Inflate Layout XML (`ImageView`)
- **Gejala / Error:**
  ```text
  android.view.InflateException: Binary XML file line #12 in com.cloudflare.onedotonedotonedotone:layout/activity_splash: Error inflating class android.widget.ImageView
  Caused by: android.content.res.Resources$NotFoundException: Drawable (missing name) with resource ID #0x7f0801e7
  ```
- **Akar Masalah:**
  Saat `StubActivity` diinisialisasi oleh framework Android OS, `mTheme` bawaan `Activity` terikat (*bound*) ke `AssetManager` milik APK host (`com.fesu.renjana`). Ketika `guestActivity` meng-inflate layout XML dan `ImageView` memanggil `context.obtainStyledAttributes()` atau `context.getTheme().getDrawable(0x7f0801e7)`, `Theme` menanyakan resource ID tersebut ke `AssetManager` milik host, di mana ID grafis guest tidak ada.
- **Solusi:**
  1. Meng-override `getTheme()` dan `setTheme()` pada `StubActivity` untuk membuat `Resources.Theme` baru yang langsung di-construct dari `virtualClassLoader.getResources().newTheme()` (AssetManager milik APK guest).
  2. Menginjeksi `guestRes` ke field `mResources` dan `this.theme` ke `mTheme` pada `ContextThemeWrapper` milik `guestActivity`.
  3. Memastikan `getLayoutInflater()` meng-clone inflater dengan context `StubActivity` yang tema dan resourcenya sudah terikat ke APK guest.

---

## 7. Crash `SuperNotCalledException` pada `ACTION_FINISH_GUEST`
- **Gejala / Error:**
  ```text
  android.util.SuperNotCalledException: Activity {com.fesu.renjana/com.fesu.renjana.core.StubActivity_1} did not call through to super.onCreate()
  at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4192)
  ```
- **Akar Masalah:**
  Pada handler intent `ACTION_FINISH_GUEST` (saat stub activity diminta menghentikan guest process dan menutup jendela), method `StubActivity.onCreate()` langsung mengeksekusi `finish()` dan `return` sebelum memanggil `super.onCreate(savedInstanceState)`. Hal ini melanggar kontrak siklus hidup Android di mana `super.onCreate()` wajib dipanggil sebelum method selesai.
- **Solusi:**
  Memindahkan pemanggilan `super.onCreate(savedInstanceState)` ke baris paling pertama di dalam `StubActivity.onCreate()`.

---

## 8. Multi-App Isolation & Allocation Bug di Instance yang Sama
- **Gejala / Error:**
  Saat menambahkan dan menjalankan aplikasi kedua (misalnya `Cashdrama`) di instance yang sama dengan aplikasi pertama (misalnya `Cloudflare 1.1.1.1`):
  1. Aplikasi pertama ikut terdeteksi *running* / status runtime bercampur.
  2. Crash atau memory leak / storage collision karena kedua aplikasi berbagi direktori database, `shared_prefs`, `files`, dan `dex_opt` yang identik.
  3. Package identity guest salah (`EXTRA_PACKAGE_NAME` malah membaca package aplikasi pertama dari model `Instance`).
- **Akar Masalah:**
  1. `ActivityStubManager.buildStubIntent()` mengabaikan parameter `targetPackageName` dan `targetAppName` dari `InstanceLaunchData`, melainkan selalu mengambil `resolvedInstance.packageName` (package aplikasi pertama pemilik instance). Akibatnya, saat `StubActivity` melapor ke `InstanceLifecycleService` dan `AppRuntimeRegistry`, aplikasi pertama yang tercatat *running*.
  2. `ActivityStubManager` mengelola `instanceStacks` hanya berbasis `instanceId`. Jika ada 2 aplikasi dalam 1 instance, stack activity antar aplikasi saling bertumpuk dan merusak launch mode (`singleTop`, `singleTask`).
  3. `StubActivity` dan `VirtualContext` mengarahkan penyimpanan sandbox kedua aplikasi ke folder root instance yang sama (`/instances/<instanceId>/`), menyebabkan konflik file preferences, database SQLite, dan file odex (`dex_opt`).
  4. `ActivityStarterHook` hanya meng-cache APK path dan package per `instanceId` bukan per-aplikasi atau per-stub.
- **Solusi:**
  1. Menambahkan parameter `targetPackageName` dan `targetAppName` pada `ActivityStubManager.buildStubIntent()`.
  2. Menyesuaikan `instanceStacks` di `ActivityStubManager` agar menggunakan composite key (`instanceId:packageName`) sehingga setiap aplikasi dalam instance memiliki task stack independen.
  3. Mengisolasi `effectiveDataPath` pada `StubActivity` menjadi `/instances/<instanceId>/packages/<packageName>` sehingga direktori `files/`, `databases/`, `shared_prefs/`, `cache/`, dan `dex_opt/` terisolasi 100% per aplikasi.

---

## 9. Crash `SecurityException: Unknown calling package name` pada GMS Client / Firebase
- **Gejala / Error:**
  ```text
  java.lang.SecurityException: Unknown calling package name 'com.Cash.Drama'.
  at android.os.Parcel.createExceptionOrNull(Parcel.java:3261)
  at com.google.android.gms.common.internal.BaseGmsClient.getRemoteService(...)
  at com.google.android.gms.dynamite_measurementdynamite...
  ```
- **Akar Masalah:**
  Di dalam `StubActivity.kt`, saat merekonstruksi objek `Instance` untuk subprocess (`:p0` - `:p9`), kode mengambil `ActivityStubManager.getCachedInstance(instanceId)` yang berisi `packageName` milik aplikasi utama/pertama instance. Saat memanggil `PineHookManager.installGuestHooks()`, nama package yang dipasang hook adalah package aplikasi pertama, bukan package guest app yang sedang berjalan (`effectivePackage`). Akibatnya `CoreHooks.createGetApplicationInfoHook()` tidak mendeteksi guest package name dan tidak men-spoof `appInfo.uid = Process.myUid()`, sehingga verifikasi kepemilikan UID oleh library client GMS/Firebase melempar `SecurityException`.
- **Solusi:**
  1. Di `StubActivity.kt`, merekonstruksi model `instance` dengan `effectivePackage` (`packageName.ifEmpty { launchInfo.packageName ?: ... }`) dan `effectiveAppName`.
  2. Memastikan `PineHookManager.installGuestHooks()` menerima `effectivePackage` yang benar agar seluruh hook (`getApplicationInfo`, `packageDataPaths`, `AntiDetection`, `DeviceFingerprint`) terdaftar untuk package aplikasi yang sedang berjalan.
  3. Memperbaiki race condition pada `VirtualContext.ensureDir()` saat multiple background threads membuat folder penyimpanan virtual bersamaan.



