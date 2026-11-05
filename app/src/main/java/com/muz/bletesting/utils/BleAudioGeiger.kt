package com.muz.bletesting.utils

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

class BleAudioGeiger {

    private var toneGenerator: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    
    var isActive: Boolean = false
        private set

    private var currentIntervalMs: Long = 1200L

    private val beepRunnable = object : Runnable {
        override fun run() {
            if (!isActive) return

            try {
                // Mainkan nada bip pendek standard (40ms)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
            } catch (e: Exception) {
                // Elak ralat pemacu audio
            }

            handler.postDelayed(this, currentIntervalMs)
        }
    }

    fun mula() {
        if (isActive) return
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
            isActive = true
            handler.post(beepRunnable)
        } catch (e: Exception) {
            isActive = false
        }
    }

    fun henti() {
        isActive = false
        handler.removeCallbacks(beepRunnable)
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // Elak ralat pelepasan memori
        }
        toneGenerator = null
    }

    /**
     * Laraskan kelajuan denyutan bip berdasarkan kekuatan signal RSSI (-100 hingga -30 dBm)
     */
    fun kemasKiniRssi(rssi: Int) {
        val clamped = rssi.coerceIn(-100, -35)
        
        currentIntervalMs = when {
            clamped >= -45 -> 90L   // Sangat dekat (< 0.5 meter): Sangat laju
            clamped >= -55 -> 180L  // Dekat (1 meter)
            clamped >= -65 -> 320L  // Sederhana (2 - 3 meter)
            clamped >= -75 -> 600L  // Agak jauh
            clamped >= -85 -> 1000L // Jauh
            else -> 1500L           // Signal lemah di pinggir sempadan
        }
    }
}
