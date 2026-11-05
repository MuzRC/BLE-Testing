package com.muz.bletesting.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class ProximityAlertManager(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var lastBuzzTime: Long = 0L

    fun semakJarak(rssi: Int) {
        val now = System.currentTimeMillis()
        if (rssi >= -60 && now - lastBuzzTime > 600) {
            // Signal sangat dekat (< 1 meter): Dua denyutan pantas
            buzz(longArrayOf(0, 70, 70, 70))
            lastBuzzTime = now
        } else if (rssi >= -70 && now - lastBuzzTime > 1300) {
            // Signal sederhana (1 - 2 meter): Satu denyutan pendek
            buzz(longArrayOf(0, 100))
            lastBuzzTime = now
        }
    }

    private fun buzz(pattern: LongArray) {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    fun hentiSemua() {
        vibrator.cancel()
    }
}
