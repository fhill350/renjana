# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-08-26

### 🚀 Major Feature & Stability Release: Multi-App Instances & Android 14/15 Hardening

#### ✨ Added

**Multi-App per Instance Architecture**
- **Instance Apps Database Entity**: Added `instance_apps` Room table to support multiple installed apps inside a single virtual instance container (`InstanceAppEntity`).
- **Multi-App Activity Task Stack**: Partitioned `ActivityStubManager.instanceStacks` with composite keys (`stackKey(instanceId, packageName)`), allowing multiple apps within the same instance to maintain independent activity task stacks and respect standard Android launch modes (`standard`, `singleTop`, `singleTask`, `singleInstance`).
- **Per-App Data & Storage Isolation**: Completely sandboxed app filesystem paths in `StubActivity` and `VirtualContext` to `/instances/<instanceId>/packages/<packageName>/` for `files/`, `databases/`, `shared_prefs/`, `cache/`, `code_cache/`, and `dex_opt/`, eliminating database, preference, and ODEX collisions.
- **Independent App Runtime Tracking**: Extended `AppRuntimeRegistry` and `InstanceLifecycleService` to track and control app lifecycles via `"$instanceId/$packageName"` keys, including per-app launch, stop, and process reconciliation.

**Split APK & Multi-Dex Engine**
- **Full Split APK (App Bundle) Support**: Implemented `resolveAllApkPaths()` in `ApkLoader` and `VirtualClassLoader` to automatically locate and load all split APK siblings (`split_config.arm64_v8a.apk`, `split_config.xxhdpi.apk`, `split_config.in.apk`), supporting modern Google Play App Bundles seamlessly.
- **Framework-Res Integration**: Integrated host framework resources with `PackageManager.getResourcesForApplication()` to ensure full asset availability across both guest and system themes.

**GMS & Firebase Dynamic Sandboxing**
- **Dynamic UID Spoofing for GMS Client**: Propagated active guest package names (`effectivePackage`) to `PineHookManager` and `CoreHooks.createGetApplicationInfoHook()`, spoofing `ApplicationInfo.uid` to match the container process UID and resolving `SecurityException: Unknown calling package name` in Firebase Analytics, Google Ads, and GMS Dynamite.
- **GMS Unavailability Fallback**: Enhanced GMS availability hooks (`isGooglePlayServicesAvailable`) and `ContextImpl.bindService` blocking hooks to allow Firebase and GMS-dependent apps to fall back cleanly to direct HTTPS protocols when GMS virtualization is disabled.

**User Interface & Experience**
- **Multi-App Instance Cards**: Redesigned Home screen cards with a 2x2 mini app icon grid for multi-app containers and combined title/subtitle displays.
- **Instance Detail "Apps" Tab**: Added dedicated Apps management tab in `InstanceDetailScreen` featuring individual app launch buttons (▶ Play), stop/delete controls, and an "Add App" clone sheet.
- **Virtual Container Floating Overlay**: Added interactive floating action overlay providing quick access to container tools and navigation while guest applications are running.
- **PROBLEM.md Technical Log**: Created structured post-mortem documentation detailing 9 major virtualization hurdles, root causes, and applied engineering solutions.

#### 🐛 Fixed

- **`VectorDrawableCompat` Crash**: Bypassed strict AppCompat vector drawable verification across both host and guest classloaders using `AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)`, reflection on `ResourceManagerInternal.mHasCheckedVectorDrawableSetup`, and Pine method hooks.
- **Android 14/15 `ActivityInfo.parentActivityName` NPE**: Cloned and injected valid `ActivityInfo` into guest `Activity.mActivityInfo` to prevent null-pointer crashes during `Activity.onCreate()`.
- **`ComponentActivity.getViewModelStore` / `Activity.attach` Order**: Reordered parameter type evaluation in `Activity.attach` reflection so container `Application` instances are passed accurately instead of generic `Context`.
- **`AppCompatDelegateImpl` Manifest Lookups**: Redirected guest activity component names to valid registered `StubActivity` components and added Pine fallback hooks on `PackageManager.getActivityInfo()`.
- **Layout XML Resource Inflation**: Overrode `StubActivity.getTheme()` and `StubActivity.setTheme()` to bind directly to `virtualClassLoader.getResources().newTheme()`, preventing `Resources$NotFoundException` when inflating XML drawables.
- **`SuperNotCalledException` on Guest Finish**: Ensured `super.onCreate(savedInstanceState)` is called unconditionally at the start of `StubActivity.onCreate()`, fixing lifecycle contract violations during `ACTION_FINISH_GUEST`.
- **Directory Creation Race Condition**: Fixed `VirtualContext.ensureDir()` concurrency checks to avoid false warning logs when multiple background worker threads initialize directories simultaneously.

---

## [0.1.0] - 2026-06-18

### 🎉 Initial Release

#### ✨ Added

**Core Virtualization**
- VirtualClassLoader with isolated DEX loading per instance
- Activity stub system with 10 pre-registered activity slots
- Intent router for smart intent interception and routing
- Resource manager for per-instance resource isolation
- Wrapper activity for guest app lifecycle management

**Multi-Instance Support**
- Create multiple instances of the same app
- Isolated data directories per instance
- Independent cache and settings for each instance
- Instance cloning functionality

**Google Services Integration**
- Google Sign-In virtualization with account picker
- Firebase authentication bypass
- Play Billing proxy for in-app purchases
- GMS service proxy for Google APIs

**Anti-Detection**
- Play Integrity API bypass
- SafetyNet bypass with attestation spoofing
- Per-instance signature spoofing
- Device fingerprint randomization (IMEI, MAC, Android ID, etc.)
- Frida detection evasion
- Root detection evasion

**Frida Integration**
- FridaManager for gadget lifecycle management
- ScriptInjector for runtime JavaScript injection
- HookManagerActivity for visual hook management
- Support for inline and file-based scripts

**User Interface**
- Modern Jetpack Compose UI with Material 3
- Home screen with instance grid
- Apps screen for APK selection
- Accounts screen for Google account management
- Settings screen for configuration
- Create Instance wizard with multi-step flow

**Database**
- Room database for persistent storage
- Instance entity with configuration support
- Google account entity with token management
- DAO layer with Flow support

**Build System**
- Gradle 8.11 with Kotlin DSL support
- Signed release builds with keystore
- R8 minification for release APK (3.3MB)
- Debug builds with full symbols (50MB)

**Documentation**
- Comprehensive README with usage guide
- Contributing guidelines
- Apache 2.0 license
- Architecture documentation

#### 🔧 Technical Details

- **Package:** `com.fesu.renjana`
- **Min SDK:** 29 (Android 10)
- **Target SDK:** 34 (Android 14)
- **Kotlin:** 1.9.20
- **Compose BOM:** 2023.10.01
- **Room:** 2.6.0
- **Coroutines:** 1.7.3

#### 📦 Dependencies

- AndroidX Core KTX 1.12.0
- AndroidX AppCompat 1.6.1
- Lifecycle Runtime KTX 2.6.2
- Activity Compose 1.8.0
- Navigation Compose 2.7.4
- Room Runtime & KTX 2.6.0
- Gson 2.10.1
- Xposed API 82 (compileOnly)
- Play Services Auth 20.7.0 (compileOnly)

#### 🐛 Known Issues

- Some aggressive anti-tamper apps may still detect virtualization
- Performance overhead for resource-intensive applications
- Limited to Android 10 and above
- Frida gadget requires manual asset placement

---

## Version History

| Version | Release Date | Status |
|---------|--------------|--------|
| 0.2.0   | 2026-08-26   | ✅ Current |
| 0.1.0   | 2026-06-18   | 📦 Previous |

---

## Upcoming Features (Roadmap)

### Future Versions

Plans for versions beyond v0.2.0 will be announced later. Bug fixes and patches will be released as needed.

---

## Migration Guide

### From Previous Versions

This is the initial release, so no migration is needed.

### For Developers

If you're integrating Renjana into your workflow:

1. **Install APK** on your device
2. **Grant permissions** (storage, etc.)
3. **Add apps** you want to virtualize
4. **Create instances** as needed

See [README.md](README.md) for detailed usage instructions.

---

## Contributors

### Core Team
- Renjana Development Team

### Special Thanks
- VirtualXposed community for inspiration
- LSPatch developers for modern techniques
- Frida project for dynamic instrumentation
- Android open-source community

---

## Reporting Issues

Found a bug or have a feature request?

- **Bugs:** [Open an Issue](../../issues/new?template=bug_report.md)
- **Features:** [Request a Feature](../../issues/new?template=feature_request.md)
- **Questions:** [Start a Discussion](../../discussions)

---

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**[⬆ Back to Top](#changelog)**

</div>
