# Petunjuk Manajemen Keystore Rilis ArAr Note

Untuk keamanan rilis produksi:
1. **JANGAN PERNAH** mengomits berkas `.jks`, `.keystore`, atau kata sandi ke dalam repositori Git publik.
2. File berekstensi `*.jks` dan `keystore.properties` sudah dimasukkan ke `.gitignore`.

### Konfigurasi Lokal
Buat file `keystore.properties` di root direktori proyek (sejajar dengan `build.gradle.kts` root):
```properties
storeFile=app/keystore/ararnote-release.jks
storePassword=password_keystore_anda
keyAlias=alias_kunci_anda
keyPassword=password_kunci_anda
```

### Konfigurasi CI/CD (GitHub Actions)
Gunakan GitHub Repository Secrets (`Settings > Secrets and variables > Actions`):
- `KEYSTORE_BASE64`: Isi file .jks yang di-encode Base64
- `KEYSTORE_PASSWORD`: Kata sandi keystore
- `KEY_ALIAS`: Alias kunci
- `KEY_PASSWORD`: Kata sandi kunci
