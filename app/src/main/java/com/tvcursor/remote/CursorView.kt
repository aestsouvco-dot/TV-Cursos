package com.tvcursor.remote

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import kotlin.math.max

/**
 * Kursor panah + penanganan tombol remote.
 *
 *  Panah        : geser kursor (ketuk = geser kecil, tahan = meluncur makin cepat)
 *  OK / Enter   : klik; tahan OK = tekan lama
 *  BACK         : kembali (diteruskan ke aplikasi); tahan BACK 0,7 dtk = tutup kursor
 *  Dorong ke tepi layar : scroll ke arah itu
 *  CH+/CH- , Page Up/Down : scroll
 */
class CursorView(context: Context, private val svc: CursorService) : View(context) {

    private val density = resources.displayMetrics.density
    private val scale = density * 1.6f
    private val handler = Handler(Looper.getMainLooper())

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1.2f // satuan path, ikut diskalakan
        strokeJoin = Paint.Join.ROUND
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x33, 0xB5, 0xE5)
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    private val arrow = Path().apply {
        moveTo(0f, 0f)
        lineTo(0f, 17f)
        lineTo(4.2f, 13.2f)
        lineTo(7.2f, 20f)
        lineTo(9.8f, 18.9f)
        lineTo(6.9f, 12.2f)
        lineTo(12.2f, 12.2f)
        close()
    }

    private var cx = 0f
    private var cy = 0f
    private var positioned = false
    private var pulseAt = -1000L

    private val held = LinkedHashSet<Int>()
    private var holdStart = 0L
    private var lastTick = 0L
    private var lastScroll = 0L

    private var selHeld = false
    private var selLong = false
    private var backHeld = false
    private var backLong = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setWillNotDraw(false)
    }

    // ---------------------------------------------------------------- gambar

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!positioned && w > 0 && h > 0) {
            cx = w / 2f
            cy = h / 2f
            positioned = true
        } else {
            cx = cx.coerceIn(0f, max(0, w - 1).toFloat())
            cy = cy.coerceIn(0f, max(0, h - 1).toFloat())
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, outline)
        canvas.restore()

        val age = SystemClock.uptimeMillis() - pulseAt
        if (age in 0..350) {
            val f = age / 350f
            ring.alpha = (255 * (1f - f)).toInt()
            canvas.drawCircle(cx, cy, (10f + 28f * f) * density, ring)
            postInvalidateOnAnimation()
        }
    }

    private fun pulse() {
        pulseAt = SystemClock.uptimeMillis()
        invalidate()
    }

    // ---------------------------------------------------------------- gerak

    private val ticker = object : Runnable {
        override fun run() {
            if (held.isEmpty()) return
            val now = SystemClock.uptimeMillis()
            val dt = max(0L, now - lastTick) / 1000f
            lastTick = now
            val t = (now - holdStart) / 1000f
            val v = width * (0.14f + 0.40f * (t / 1.6f).coerceAtMost(1f)) * svc.speedFactor()
            step(v * dt)
            if (now - holdStart > 12000) { // pengaman jika tombol "lepas" tidak pernah datang
                held.clear()
                return
            }
            handler.postDelayed(this, 16)
        }
    }

    private fun dirDown(code: Int) {
        if (code in held) return
        val first = held.isEmpty()
        held.add(code)
        if (first) {
            holdStart = SystemClock.uptimeMillis()
            lastTick = holdStart + 230
            step(max(10f, width * 0.007f)) // ketukan singkat = geser kecil yang presisi
            handler.postDelayed(ticker, 230)
        }
    }

    private fun dirUp(code: Int) {
        held.remove(code)
        if (held.isEmpty()) handler.removeCallbacks(ticker)
    }

    private fun step(dist: Float) {
        var dx = 0f
        var dy = 0f
        for (k in held) {
            when (k) {
                KeyEvent.KEYCODE_DPAD_UP -> dy -= dist
                KeyEvent.KEYCODE_DPAD_DOWN -> dy += dist
                KeyEvent.KEYCODE_DPAD_LEFT -> dx -= dist
                KeyEvent.KEYCODE_DPAD_RIGHT -> dx += dist
            }
        }
        move(dx, dy)
    }

    private fun move(dx: Float, dy: Float) {
        val tx = cx + dx
        val ty = cy + dy
        val nx = tx.coerceIn(0f, max(0, width - 1).toFloat())
        val ny = ty.coerceIn(0f, max(0, height - 1).toFloat())
        val hitX = nx != tx
        val hitY = ny != ty
        cx = nx
        cy = ny
        invalidate()
        if (hitY && dy != 0f) edgeScroll(true, dy > 0)
        if (hitX && dx != 0f) edgeScroll(false, dx > 0)
    }

    private fun edgeScroll(vertical: Boolean, positive: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (now - lastScroll < 550) return
        lastScroll = now
        svc.scroll(cx.toInt(), cy.toInt(), width, height, vertical, positive)
    }

    // ---------------------------------------------------------------- klik

    private val longSelRun = Runnable {
        selLong = true
        pulse()
        svc.longPress(cx.toInt(), cy.toInt())
    }

    private fun selPress() {
        if (selHeld) return
        selHeld = true
        selLong = false
        handler.postDelayed(longSelRun, 600)
    }

    private fun selRelease() {
        if (!selHeld) return
        selHeld = false
        handler.removeCallbacks(longSelRun)
        if (!selLong) {
            pulse()
            svc.tap(cx.toInt(), cy.toInt())
        }
    }

    // ---------------------------------------------------------------- back

    private val backRun = Runnable {
        backLong = true
        svc.setActive(false)
    }

    private fun backPress() {
        if (backHeld) return
        backHeld = true
        backLong = false
        handler.postDelayed(backRun, 700)
    }

    private fun backRelease() {
        if (!backHeld) return
        backHeld = false
        handler.removeCallbacks(backRun)
        if (!backLong) svc.passKey(KeyEvent.KEYCODE_BACK)
    }

    // ---------------------------------------------------------------- tombol remote

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        val first = down && e.repeatCount == 0
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (down) dirDown(e.keyCode) else dirUp(e.keyCode)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (down) selPress() else selRelease()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (down) backPress() else backRelease()
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP -> {
                if (first) svc.scroll(cx.toInt(), cy.toInt(), width, height, true, false)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (first) svc.scroll(cx.toInt(), cy.toInt(), width, height, true, true)
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            held.clear()
            handler.removeCallbacks(ticker)
            handler.removeCallbacks(longSelRun)
            selHeld = false
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
