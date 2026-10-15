# TV Cursor (Android TV)

Kursor mouse di layar Android TV yang digerakkan dengan remote. Tidak memakai izin Aksesibilitas. Izin yang diminta adalah izin **Shizuku**.

## Cara kerja

| Kebutuhan | Cara |
|---|---|
| Menggambar kursor | Jendela overlay (`TYPE_APPLICATION_OVERLAY`), izinnya diberikan otomatis lewat Shizuku |
| Menangkap tombol remote saat kursor tampil | Overlay itu sendiri memegang fokus, jadi panah/OK/Back masuk ke kursor |
| Klik, tekan lama, scroll | Shizuku UserService menjalankan `input tap`, `input swipe`, `input keyevent` |
| Tombol untuk memunculkan kursor dari aplikasi lain | Pantau pasif `getevent -l` lewat Shizuku; tombol ditahan 0,7 detik |

## Build lewat GitHub dari Termux

```bash
pkg install git gh unzip
unzip tv-cursor.zip && cd tv-cursor
git init -b main
git add .
git commit -m "TV Cursor"
gh auth login
gh repo create tv-cursor --private --source=. --push
gh run watch            # tunggu build selesai
gh run download -n TVCursor-debug-apk
```

APK ada di folder `TVCursor-debug-apk/app-debug.apk`. Bisa juga diunduh dari tab Actions di GitHub.

## Pasang dan aktivasi di TV

1. Pasang `app-debug.apk` di TV (flashdisk, atau `adb install` lewat Termux dari HP).
2. Pastikan Shizuku berjalan (mode root: buka aplikasi Shizuku lalu Start).
3. Buka **TV Cursor**, tekan tombol **1**, di dialog Shizuku pilih **Izinkan**.
4. Tekan tombol **2**, lalu tekan tombol remote yang jarang dipakai (misalnya tombol warna).
5. Tekan tombol **3** untuk mencoba kursor.

Karena TV sudah root, Shizuku bisa dijalankan otomatis saat TV menyala lewat opsi "Start on boot" di aplikasi Shizuku (mode root). Dengan begitu TV Cursor tersambung sendiri setelah restart.

## Kontrol

- Panah: geser kursor (tahan = makin cepat)
- OK: klik, tahan OK: tekan lama
- Back: kembali, tahan Back 0,7 detik: tutup kursor
- Dorong kursor ke tepi layar, atau CH+/CH-/Page Up/Down: scroll
- Tahan tombol aktivasi 0,7 detik: tampil/sembunyikan kursor dari aplikasi mana pun
