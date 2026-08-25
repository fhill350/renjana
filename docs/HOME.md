# Home Screen

Layar utama Renjana yang menampilkan dashboard overview dan daftar seluruh instance virtual container yang aktif maupun idle.

---

## 🎨 Tampilan UI

| Elemen | Deskripsi |
| :--- | :--- |
| **Stat Header** | Ringkasan metrik: Jumlah container aktif vs total instance |
| **List / Grid Toggle** | Mengubah mode tampilan antara list vertikal ringkas atau grid 2 kolom |
| **Instance Card** | Menampilkan ikon app (atau mini grid 2x2 multi-app), nama container, status badge, dan tanggal pembuatan |
| **FAB (+)** | Floating Action Button untuk membuka wizard pembuatan instance baru |

---

## 🎮 Aksi Cepat

| Aksi | Interaksi |
| :--- | :--- |
| **Buat Instance Baru** | Tap FAB **+** ➔ Pilih aplikasi pada `AppsScreen` |
| **Jalankan Container** | Tap tombol **▶ Play** pada card container |
| **Hentikan Container** | Tap tombol **⏹ Stop** pada card container yang sedang aktif |
| **Buka Manajemen Instance** | Tap body card container untuk masuk ke `InstanceDetailScreen` |
| **Edit / Hapus Instance** | Long-press card container ➔ Pilih menu konteks **Edit** atau **Delete** |

---

## 🚦 Indikator Status Container

| Status | Indikator | Arti Status |
| :--- | :---: | :--- |
| **Running** | 🟢 Hijau | Container dan proses guest sedang aktif berjalan di subprocess (`:p0`–`:p9`) |
| **Paused** | 🟡 Kuning | Container dalam keadaan paused / background |
| **Idle** | ⚫ Abu-abu | Container dalam keadaan mati / belum dijalankan |

---

## 🧭 Alur Navigasi

- **Tap Card** ➔ Buka [`InstanceDetailScreen`](INSTANCE_DETAIL.md)
- **Tap FAB (+)** ➔ Buka [`AppsScreen`](APPS.md)

