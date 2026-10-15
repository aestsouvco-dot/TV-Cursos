package com.tvcursor.remote;

// Dipanggil dari proses Shizuku setiap ada tombol remote (nama evdev, mis. KEY_MENU) dan state DOWN/UP/REPEAT.
oneway interface IKeyCallback {
    void onKey(String name, String state);
}
