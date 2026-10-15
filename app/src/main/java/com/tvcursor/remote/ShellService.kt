package com.tvcursor.remote

import kotlin.system.exitProcess

/**
 * Dijalankan oleh Shizuku di proses terpisah dengan hak root/shell (bukan di proses aplikasi).
 * Konstruktor tanpa argumen diwajibkan oleh Shizuku UserService.
 */
class ShellService : IShellService.Stub() {

    private val keyRe = Regex("""EV_KEY\s+(\S+)\s+(DOWN|UP|REPEAT)""")

    @Volatile
    private var proc: Process? = null

    @Volatile
    private var listener: IKeyCallback? = null

    override fun destroy() {
        stopKeyMonitor()
        exitProcess(0)
    }

    override fun exec(cmd: String): String {
        return try {
            val p = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    override fun startKeyMonitor(callback: IKeyCallback) {
        stopKeyMonitor()
        listener = callback
        val t = Thread {
            try {
                val p = ProcessBuilder("getevent", "-l").redirectErrorStream(true).start()
                proc = p
                p.inputStream.bufferedReader().forEachLine { line ->
                    val m = keyRe.find(line)
                    if (m != null) {
                        try {
                            this@ShellService.listener?.onKey(m.groupValues[1], m.groupValues[2])
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        t.isDaemon = true
        t.start()
    }

    override fun stopKeyMonitor() {
        listener = null
        try { proc?.destroy() } catch (_: Exception) {}
        proc = null
    }
}
