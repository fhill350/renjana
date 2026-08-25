# Master Plan — Multi-App Runtime Controls & UI Overhaul

Status per 2026-08-23. Konteks: container engine sudah jalan end-to-end
(1.1.1.1 melewati splash → onboarding). Sekarang fokus: kontrol per-APP
(bukan per-instance), UX stop/run, visual detail, dan Quick Switch Bubble.

## PR-1 — AppRuntimeRegistry (fondasi) ⭐ blocking semua
State per **(instance, app)**, bukan per instance:
- `RunningApp(instanceId, packageName, appName, stubIndex, startedAt)`
- `core/AppRuntimeRegistry.kt`: StateFlow<Set<RunningApp>> + API:
  - `register(...)` dari InstanceLauncher (main) & LifecycleService (dari stub)
  - `refresh()` — rekonsiliasi dengan proses `:pN` hidup (seperti ActivityStubManager)
  - `openApp(ctx, app)` — Intent ke `StubActivity_N` + REORDER_TO_FRONT|SINGLE_TOP|NEW_TASK
  - `closeApp(ctx, app)` — `ActivityManager.getAppTasks()` cari task stub → `finishAndRemoveTask()` → stub bunuh diri (mekanisme ada)
- StubActivity.reportStateToService dibawa packageName + stubIndex.
- InstanceLifecycleService: daftar/hapus per-app; derived instance-running.

## PR-2 — Instance Detail: kontrol per-app + fix visual
- Baris app: ikon, nama, status chip (Running + durasi / Stopped).
- Aksi: **Open** (jalan) / **Run** (berhenti) / **Close** (confirm modal) /
  **Remove** (confirm modal, hanya saat berhenti).
- Fix bug visual "kotak dalam kotak": Surface bersarang di dalam SectionCard
  diratakan jadi satu lapis.

## PR-3 — ConfirmDialog reusable + Home card upgrade
- `ui/components/ConfirmDialog.kt`: judul, pesan, aksi berbahaya merah.
- Kartu Home: badge running live ("Running · 2 apps"), tombol **Open**
  (jika jalan), **Stop** (confirm → stop SEMUA app instance), **Run**
  (single-app langsung; multi-app → ke detail).

## PR-4 — Quick Switch Bubble: terlihat & berfungsi
- Panel bubble menampilkan **app yang jalan** (per-app): tap → openApp,
  tombol stop per baris.
- Permission SYSTEM_ALERT_WINDOW: banner di Home + toggle di Settings →
  deep-link `ACTION_MANAGE_OVERLAY_LINK`. (Untuk test: `adb shell appops
  set com.fesu.renjana SYSTEM_ALERT_WINDOW allow`.)

## PR-5 — Reactive & smoothness
- Registry auto-refresh 5 dtk saat UI subscribe → badge mati-sendiri
  akurat tanpa manual.
- Home "Running" diturunkan dari daftar app jalan.

## PR-6 — Build & smoke test device
- assembleDebug → install → verifikasi: per-app Open/Close, modal confirm,
  detail flat, bubble muncul + pindah app.

Urutan eksekusi: 1 → 2 → 3 → 4 → 5 → 6.
