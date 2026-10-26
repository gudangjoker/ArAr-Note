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

6. **Interactive Home Screen Sticky Widget (`NoteWidgetProvider` & `ChecklistWidgetService`):**
   - **Interaktif Langsung dari Home Screen:** Centang dan batalkan centang item to-do checklist langsung dari layar depan HP tanpa perlu membuka aplikasi!
   - Didukung `RemoteViewsService` + `RemoteViewsFactory` dengan pembaruan otomatis instan ke database SQLite.
   - Menampilkan badge progres checklist (contoh: `✓ 3/5`) di baris judul widget.
   - Untuk catatan teks, menampilkan sticky note dengan tema warna dan cuplikan teks rapi.
   - Tombol cepat di header widget untuk membuka editor catatan langsung.

7. **Ekspor Catatan ke PDF, Markdown (.md), dan Plain Text (.txt) (`ExportManager`):**
   - **PDF Document (.pdf):** Dokumen A4 berstandar cetak dengan tata letak rapi, bilah warna aksen tema catatan, watermark header & footer dengan nomor halaman otomatis, pembungkusan teks multi-halaman (*automatic pagination*), serta kotak centang checklist grafis.
   - **Markdown (.md):** File Markdown standar dengan `# Judul`, blok metadata tanggal, pemisah `---`, dan format to-do list GitHub Flavored Markdown (`- [x]` / `- [ ]`).
   - **Plain Text (.txt):** File teks murni berformat rapi dengan ASCII divider dan penanda `[x]` / `[ ]`.
   - **Aksi Cepat:** Simpan langsung ke folder `Downloads/ArArNote` perangkat (didukung MediaStore Android 10–14+) atau Bagikan (*Share Sheet*) via `FileProvider` ke WhatsApp, Gmail, Telegram, Google Drive, dll.
   - **Akses Fleksibel:** Tersedia di toolbar editor catatan dan menu tekan lama (*long-press*) kartu catatan di beranda (dengan proteksi biometrik/password otomatis jika catatan terenkripsi).

8. **Manajemen Organisasi Lengkap:**
   - **Tampilan Grid & List:** Beralih antara tampilan kartu grid 2 kolom atau daftar ringkas.
   - **Sorting:** Urutkan berdasarkan Tanggal Modifikasi, Tanggal Dibuat, Judul (A-Z), Warna, atau Waktu Pengingat.
   - **Pin to Top:** Sematkan catatan penting di posisi teratas.
   - **Search:** Pencarian instan teks dan checklist.
   - **Archive:** Arsipkan catatan tanpa menghapusnya.
   - **Trash & Empty Trash:** Keranjang sampah dengan opsi pulihkan (*restore*) atau hapus permanen.

9. **Backup & Restore ke Google Drive, Cloud & Penyimpanan Lokal (`.backup`):**
   - **Dukungan Google Drive (Storage Access Framework - SAF):** Simpan berkas cadangan database `.backup` langsung ke akun Google Drive pribadi, OneDrive, SD Card, atau folder perangkat tanpa perlu konfigurasi API key yang rumit.
   - **Pemulihan Langsung dari Google Drive:** Jelajahi dan pilih berkas cadangan langsung dari Google Drive untuk memulihkan seluruh catatan secara instan.
   - **Cadangan Internal Aplikasi:** Tetap menyediakan opsi simpan dan pulihkan cadangan cepat di penyimpanan internal aplikasi.
   - **Sinkronisasi Widget Otomatis:** Setelah proses pemulihan, widget layar utama langsung diperbarui secara realtime.

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
