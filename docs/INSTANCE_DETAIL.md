# Instance Detail Screen

Layar detail dan kontrol manajemen untuk satu instance virtual container. Dilengkapi dengan 4 tab navigasi utama dan kontrol runtime real-time.

---

## 📑 Struktur Tab

### 1. Tab Overview
| Elemen | Deskripsi |
| :--- | :--- |
| **Status Indicator** | Status live: `Running` 🟢, `Paused` 🟡, `Idle` ⚫ |
| **Container Info** | Nama instance, base package name, dan subprocess slot (`:p0`–`:p9`) |
| **Creation Date** | Waktu & tanggal pembuatan container |
| **Master Controls** | Tombol **▶ Play** (jalankan default) dan **⏹ Stop** (hentikan container) |

---

### 2. Tab Apps (Multi-App Manager)
| Elemen | Deskripsi |
| :--- | :--- |
| **App List** | Daftar semua aplikasi guest yang terpasang di dalam instance |
| **Per-App Controls** | Tombol **▶ Play** untuk meluncurkan aplikasi individual, **⏹ Stop**, dan **🗑 Delete** |
| **Add App Button** | Membuka bottom sheet untuk meng-clone aplikasi tambahan ke dalam container ini |

---

### 3. Tab Config
| Pengaturan | Pilihan Nilai | Keterangan |
| :--- | :---: | :--- |
| **GMS Virtualization** | `Aktif` / `Nonaktif` | Simulasi Google Play Services & Firebase Auth |
| **Fingerprint Spoofing** | `Aktif` / `Nonaktif` | Mengacak identitas perangkat virtual |
| **Signature Spoofing** | `Aktif` / `Nonaktif` | Meniru signature APK asli dari package manager |
| **Anti-Detection** | `Aktif` / `Nonaktif` | Menyembunyikan jejak container & path virtual |

---

### 4. Tab Device
| Parameter | Keterangan |
| :--- | :--- |
| **Model** | Perangkat virtual yang ditiru (contoh: *Xiaomi POCO X3 Pro*) |
| **Android ID** | 64-bit hex identifier unik per container |
| **IMEI** | Nomor IMEI virtual unik |
| **Build Fingerprint** | String fingerprint hardware yang dispoof |

> [!TIP]
> Tap tombol **🎲 Refresh Fingerprint** untuk meng-generate identitas perangkat acak yang baru.

---

## ⚠️ Danger Zone

| Aksi | Efek |
| :--- | :--- |
| **Clear Data** | Menghapus semua database, shared prefs, dan cache seluruh aplikasi di container ini. |
| **Delete Instance** | Menghapus container secara permanen beserta seluruh file dan aplikasinya. |

> [!CAUTION]
> Aksi di Danger Zone bersifat destruktif dan **tidak dapat dibatalkan**. Dialog konfirmasi akan selalu ditampilkan sebelum eksekusi.

---

## 🧭 Navigasi

- **Back Button** (Toolbar) ➔ Kembali ke `HomeScreen`
- **Bug Report Icon** (Toolbar) ➔ Menuju ke `DiagnosticsScreen`

