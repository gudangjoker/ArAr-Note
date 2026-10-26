# ArAr Note 📝

**ArAr Note** adalah aplikasi catatan & checklist Android modern yang merekonstruksi dan menduplikasi seluruh fitur serta arsitektur inti dari **ColorNote 4.9.3** secara utuh dan fungsional, dibangun dengan **Kotlin**, **AndroidX Room**, **Coroutines**, dan **Material Components**.

---

## 🌟 Fitur Utama (Full Feature Set)

1. **Text Notepad Ruled Paper (`LinedEditText`):**
   - Editor catatan teks dengan tampilan kertas bergaris (*ruled lines*) khas ColorNote.
   - Menggambar garis horisontal dinamis berdasarkan tinggi font (*baseline*) hingga ke bagian bawah layar.

2. **Interactive Checklist Manager (`ChecklistItem`):**
   - Mendukung catatan daftar tugas (*to-do list*).
   - Kotak centang interaktif dengan efek coret (*strike-through*).
   - Tombol cepat *Move Checked to Bottom* (pindahkan yang sudah selesai ke bawah) dan *Delete Checked*.
   - Input cepat item baru di baris bawah.

3. **9 Tema Warna Klasik ColorNote:**
   - Kuning (Default Notepad), Biru, Merah, Oranye, Hijau, Ungu, Cyan, Abu-abu, dan Gelap (*Dark/Black*).
   - Pengubah warna instan di toolbar editor dan filter warna (*chips filter*) di halaman utama.

4. **Sistem Enkripsi Catatan, Master Password & Biometric Unlock:**
   - Menggunakan enkripsi simetris `AES/CBC/PKCS5Padding` dengan derivasi kunci `PBKDF2WithHmacSHA256` dan *salt* unik.
   - **Biometric Unlock (Fingerprint / Face ID):** Didukung `AndroidX Biometric` + enkripsi kunci perangkat keras `AndroidKeyStore` (AES-GCM-256) untuk membuka catatan terkunci secara instan tanpa mengetik password manual.
   - Tetap mendukung otentikasi cadangan Master Password manual.
   - Menu pengelolaan kata sandi di Pengaturan: Setel, Ubah, Hapus Master Password, dan Toggle Biometric.

5. **Pengingat & Alarm (`ReminderScheduler`):**
   - Penjadwalan alarm persis waktu (*exact alarms*) melalui `AlarmManager`.
   - Notifikasi pengingat dengan tombol aksi langsung: **Snooze (15 menit)** dan **Done**.
   - `BootReceiver` otomatis memulihkan seluruh jadwal alarm setelah perangkat di-restart.

6. **Home Screen Sticky Widget (`NoteWidgetProvider`):**
   - Widget sticky note di home screen dengan warna latar sesuai tema catatan.
   - Pratinjau judul dan isi teks/checklist secara langsung.
   - Konfigurasi widget (`NoteWidgetConfigureActivity`) saat menambahkan ke layar utama.

7. **Manajemen Organisasi Lengkap:**
   - **Tampilan Grid & List:** Beralih antara tampilan kartu grid 2 kolom atau daftar ringkas.
   - **Sorting:** Urutkan berdasarkan Tanggal Modifikasi, Tanggal Dibuat, Judul (A-Z), Warna, atau Waktu Pengingat.
   - **Pin to Top:** Sematkan catatan penting di posisi teratas.
   - **Search:** Pencarian instan teks dan checklist.
   - **Archive:** Arsipkan catatan tanpa menghapusnya.
   - **Trash & Empty Trash:** Keranjang sampah dengan opsi pulihkan (*restore*) atau hapus permanen.

8. **Backup & Restore Lokal (`.backup`):**
   - Ekspor seluruh basis data catatan ke file cadangan berkas `.backup` di memori internal aplikasi.
   - Pulihkan (*restore*) catatan dari file `.backup`.
   - Fitur bagikan (*Share*) catatan via teks biasa.

---

## 🗄️ Struktur Basis Data (`colornote.db`)

Menggunakan skema tabel `notes` SQLite 30 kolom yang identik dengan arsitektur ColorNote:

| Kolom | Tipe | Deskripsi |
| :--- | :--- | :--- |
| `_id` | `INTEGER PRIMARY KEY` | ID catatan autoincrement |
| `active_state` | `INTEGER` | `0`: Aktif, `16`: Arsip, `32`: Sampah |
| `type` | `INTEGER` | `0`: Catatan Teks, `1`: Checklist |
| `title` | `TEXT` | Judul catatan |
| `note` | `TEXT` | Isi teks mentah atau serialisasi JSON checklist (atau ciphertext terenkripsi) |
| `color_index` | `INTEGER` | Index warna 0–8 |
| `encrypted` | `INTEGER` | `0`: Normal, `1`: Terenkripsi AES |
| `importance` | `INTEGER` | `1`: Disematkan (*pinned*) |
| `reminder_date` | `INTEGER` | Timestamp milidetik pengingat |
| `created_date` / `modified_date` | `INTEGER` | Timestamp milidetik |

---

## 🚀 Cara Build / Compile

### Opsi 1: Otomatis via GitHub Actions (Rekomendasi)
Proyek ini sudah dilengkapi file workflow CI/CD `.github/workflows/build.yml`.
Cukup push proyek ini ke repositori GitHub:
```bash
git init
git add .
git commit -m "Initial commit: ArAr Note complete source"
git branch -M main
git remote add origin https://github.com/<username>/<repo>.git
git push -u origin main
```
GitHub Actions akan otomatis menjalankan build Ubuntu, Java 17, dan menghasilkan artifact **`arar-note-release-apk`** dan **`arar-note-debug-apk`** yang dapat langsung diunduh.

### Opsi 2: Build di Mesin Lokal
Jalankan perintah Gradle melalui terminal:
```powershell
# Windows
.\gradlew assembleDebug

# Linux / macOS
chmod +x gradlew
./gradlew assembleDebug
```
File APK yang dihasilkan akan berada di: `app/build/outputs/apk/debug/app-debug.apk`.
