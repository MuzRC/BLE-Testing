package com.muz.bletesting.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class RssiWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Simpan sehingga 40 titik bacaan RSSI terkini
    private val maxPoints = 40
    private val rssiHistory = ArrayDeque<Int>(maxPoints)

    // Tetapan garisan grid
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#262938")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#757F9A")
        textSize = 24f
        typeface = Typeface.MONOSPACE
    }

    // Tetapan garisan graf neon
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Titik terkini (Glow Dot)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.FILL
    }

    private val path = Path()

    fun addRssi(rssi: Int) {
        if (rssiHistory.size >= maxPoints) {
            rssiHistory.removeFirst()
        }
        rssiHistory.addLast(rssi)
        postInvalidate()
    }

    fun reset() {
        rssiHistory.clear()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0 || h <= 0) return

        // 1. Lukis Garisan Rujukan Grid dBm (-40 dBm hingga -100 dBm)
        val levels = listOf(-40, -60, -80, -100)
        for (lvl in levels) {
            val y = rssiToY(lvl, h)
            canvas.drawLine(0f, y, w, y, gridPaint)
            canvas.drawText("$lvl dBm", 12f, y - 6f, textPaint)
        }

        if (rssiHistory.isEmpty()) return

        // 2. Bina Laluan Garisan Graf (Waveform Path)
        path.reset()
        val stepX = w / (maxPoints - 1)
        var lastX = 0f
        var lastY = h

        rssiHistory.forEachIndexed { index, rssi ->
            val x = index * stepX
            val y = rssiToY(rssi, h)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                // Sambung titik graf dengan garisan kemas
                path.lineTo(x, y)
            }

            lastX = x
            lastY = y
        }

        // 3. Lukis Garisan Graf
        canvas.drawPath(path, linePaint)

        // 4. Lukis Titik Lampu Neon di kedudukan paling terkini
        canvas.drawCircle(lastX, lastY, 8f, dotPaint)
    }

    // Tukar nilai RSSI (-100 ke -30 dBm) kepada koordinat piksel Y
    private fun rssiToY(rssi: Int, viewHeight: Float): Float {
        val minRssi = -100f
        val maxRssi = -30f
        val clamped = rssi.toFloat().coerceIn(minRssi, maxRssi)
        val ratio = (clamped - minRssi) / (maxRssi - minRssi)
        // Y bermula dari 0 (atas) ke viewHeight (bawah)
        return viewHeight - (ratio * (viewHeight - 40f)) - 20f
    }
}
