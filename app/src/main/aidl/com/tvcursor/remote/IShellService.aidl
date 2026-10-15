package com.tvcursor.remote;

import com.tvcursor.remote.IKeyCallback;

// Layanan yang berjalan di proses Shizuku (root di TV ini).
interface IShellService {
    // Wajib untuk Shizuku UserService (kode transaksi tetap).
    void destroy() = 16777114;

    // Jalankan perintah shell, kembalikan keluarannya.
    String exec(String cmd) = 1;

    // Mulai/stop pemantauan tombol remote (getevent -l) dan kirim ke callback.
    void startKeyMonitor(IKeyCallback callback) = 2;
    void stopKeyMonitor() = 3;
}
