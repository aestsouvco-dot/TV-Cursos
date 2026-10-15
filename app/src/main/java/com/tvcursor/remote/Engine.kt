package com.tvcursor.remote

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/**
 * Otak aplikasi. Tidak memakai Accessibility. Semua hal yang butuh hak lebih dikerjakan
 * lewat Shizuku (UserService di proses root/shell):
 *
 *  - menyuntikkan klik / tekan lama / geser (input tap / input swipe / input keyevent)
 *  - memberi izin overlay ke aplikasi ini (appops set ... SYSTEM_ALERT_WINDOW allow)
 *  - memantau tombol remote secara pasif (getevent -l) untuk tombol aktivasi
 */
object Engine {
    private const val HOLD_MS = 700L
    private const val LEARN_TIMEOUT_MS = 20000L
    private const val REQ_CODE = 4242

    private val BLOCKED = setOf(
        "KEY_UP", "KEY_DOWN", "KEY_LEFT", "KEY_RIGHT",
        "KEY_ENTER", "KEY_OK", "KEY_SELECT", "KEY_KPENTER",
        "KEY_BACK", "KEY_ESC", "KEY_POWER"
    )

    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private lateinit var ctx: Context
    private var inited = false

    @Volatile
    private var shell: IShellService? = null

    @Volatile
    private var binding = false

    @Volatile
    var connected: Boolean = false
        private set

    @Volatile
    var status: String = "Belum dicoba. Tekan tombol 1."
        private set

    private var lastToast = 0L

    @Volatile
    private var monitorOn = false

    private var learnCb: ((String?) -> Unit)? = null
    private var learnTimeout: Runnable? = null
    private var holdRunnable: Runnable? = null

    // ---------------------------------------------------------------- Shizuku

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            shell = IShellService.Stub.asInterface(binder)
            binding = false
            setStatus(true, "Terhubung lewat Shizuku")
            exec.execute { afterConnect() }
            if (monitorOn) registerMonitor()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            shell = null
            binding = false
            setStatus(false, "Layanan Shizuku terputus")
        }
    }

    private val keyCb = object : IKeyCallback.Stub() {
        override fun onKey(name: String, state: String) {
            main.post { this@Engine.handleKey(name, state) }
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { connect(false) }

    private val binderDead = Shizuku.OnBinderDeadListener {
        shell = null
        binding = false
        setStatus(false, "Shizuku berhenti. Jalankan lagi di aplikasi Shizuku.")
    }

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) {
            main.post { bind() }
        } else {
            setStatus(false, "Izin Shizuku ditolak. Tekan tombol 1 lagi lalu pilih Izinkan.")
        }
    }

    fun init(c: Context) {
        ctx = c.applicationContext
        if (inited) return
        inited = true
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permListener)
    }

    /** Sambungkan ke Shizuku. [prompt] = boleh memunculkan dialog izin Shizuku. */
    fun connect(prompt: Boolean = true) {
        main.post {
            try {
                if (!Shizuku.pingBinder()) {
                    setStatus(false, "Shizuku belum berjalan. Buka aplikasi Shizuku lalu start.")
                    return@post
                }
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    if (!prompt) {
                        setStatus(false, "Izin Shizuku belum diberikan. Tekan tombol 1.")
                    } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                        setStatus(
                            false,
                            "Izin Shizuku ditolak permanen. Buka aplikasi Shizuku, izinkan TV Cursor, lalu tekan tombol 1."
                        )
                    } else {
                        setStatus(false, "Menunggu izin Shizuku... pilih Izinkan di dialog TV.")
                        Shizuku.requestPermission(REQ_CODE)
                    }
                    return@post
                }
                bind()
            } catch (e: Exception) {
                setStatus(false, "Shizuku error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun bind() {
        if (shell != null || binding) return
        binding = true
        try {
            // Naikkan version() jika kode ShellService berubah, supaya proses lama diganti.
            val args = Shizuku.UserServiceArgs(
                ComponentName(ctx.packageName, ShellService::class.java.name)
            ).processNameSuffix("shell").daemon(false).tag("tvcursor").version(1)
            Shizuku.bindUserService(args, conn)
            setStatus(false, "Menyambung layanan Shizuku...")
        } catch (e: Exception) {
            binding = false
            setStatus(false, "Gagal bind layanan Shizuku: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun afterConnect() {
        val pkg = ctx.packageName
        // Izin "tampil di atas aplikasi lain" otomatis, tanpa membuka Settings (TV sering tidak punya menunya)
        try { shell?.exec("appops set $pkg SYSTEM_ALERT_WINDOW allow") } catch (_: Exception) {}
        // Supaya aplikasi tidak dimatikan sistem saat hemat daya
        try { shell?.exec("dumpsys deviceidle whitelist +$pkg") } catch (_: Exception) {}
    }

    private fun setStatus(ok: Boolean, msg: String) {
        connected = ok
        status = msg
    }

    // ---------------------------------------------------------------- perintah

    /** Jalankan perintah shell lewat Shizuku (berurutan). [done] dipanggil di thread utama, sukses atau gagal. */
    fun run(cmd: String, done: (() -> Unit)? = null) {
        exec.execute {
            try {
                val s = shell
                if (s == null) {
                    toastLimited("Shizuku belum terhubung. Buka aplikasi TV Cursor dan tekan tombol 1.")
                    connect(false)
                } else {
                    s.exec(cmd)
                }
            } catch (e: Exception) {
                shell = null
                setStatus(false, "Perintah gagal: ${e.message ?: e.javaClass.simpleName}")
                toastLimited("Koneksi Shizuku terputus. Buka aplikasi TV Cursor dan tekan tombol 1.")
                connect(false)
            } finally {
                if (done != null) main.post { done() }
            }
        }
    }

    fun toast(msg: String) {
        main.post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }

    private fun toastLimited(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastToast > 6000) {
            lastToast = now
            toast(msg)
        }
    }

    // ---------------------------------------------------------------- pantau tombol remote

    fun startMonitor() {
        monitorOn = true
        if (shell != null) registerMonitor() else connect(false)
    }

    fun stopMonitor() {
        monitorOn = false
        try { shell?.stopKeyMonitor() } catch (_: Exception) {}
    }

    private fun registerMonitor() {
        try { shell?.startKeyMonitor(keyCb) } catch (_: Exception) {}
    }

    private fun handleKey(name: String, state: String) {
        val cb = learnCb
        if (cb != null) {
            if (state == "DOWN" && name !in BLOCKED) {
                finishLearn()
                Prefs.setHotkey(ctx, name)
                cb(name)
            }
            return
        }
        val hot = Prefs.hotkey(ctx)
        if (hot.isEmpty() || name != hot) return
        when (state) {
            "DOWN" -> if (holdRunnable == null) {
                val r = Runnable {
                    holdRunnable = null
                    CursorService.toggle(ctx)
                }
                holdRunnable = r
                main.postDelayed(r, HOLD_MS)
            }
            "UP" -> {
                holdRunnable?.let { main.removeCallbacks(it) }
                holdRunnable = null
            }
        }
    }

    // ---------------------------------------------------------------- belajar tombol aktivasi

    /** Tangkap tombol remote berikutnya sebagai tombol aktivasi. [cb] menerima nama tombol, atau null jika waktu habis. */
    fun learn(cb: (String?) -> Unit) {
        finishLearn()
        learnCb = cb
        val t = Runnable {
            val c = learnCb
            learnCb = null
            learnTimeout = null
            c?.invoke(null)
        }
        learnTimeout = t
        main.postDelayed(t, LEARN_TIMEOUT_MS)
    }

    private fun finishLearn() {
        learnTimeout?.let { main.removeCallbacks(it) }
        learnTimeout = null
        learnCb = null
    }
}
