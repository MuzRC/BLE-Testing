package com.muz.bletesting

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button
    private lateinit var btnBroadcast: Button
    private lateinit var btnStopBroadcast: Button

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null

    // Simpanan data signal yang disalin
    private var capturedManufacturerId: Int = -1
    private var capturedManufacturerData: ByteArray? = null

    // Callback pemancar
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            tvStatus.text = "Signal klon sedang dipancarkan secara aktif!"
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            tvStatus.text = "Gagal memancar. Ralat kod: $errorCode"
        }
    }

    // Callback pengimbas
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val record = result?.scanRecord ?: return
            val manData = record.manufacturerSpecificData

            if (manData.size() > 0) {
                capturedManufacturerId = manData.keyAt(0)
                capturedManufacturerData = manData.valueAt(0)

                bleScanner?.stopScan(this)

                val hexData = capturedManufacturerData?.joinToString("") { "%02X".format(it) } ?: "TIADA"
                tvStatus.text = "Signal Disalin!\nID: $capturedManufacturerId\nPayload (Hex): $hexData"
                Toast.makeText(this@MainActivity, "Data BLE berjaya disalin!", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            tvStatus.text = "Imbasan gagal. Ralat kod: $errorCode"
        }
    }

    // Pengurus kebenaran (Permission Launcher)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Kebenaran Bluetooth diperlukan untuk app ini berfungsi.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnScan = findViewById(R.id.btnScan)
        btnBroadcast = findViewById(R.id.btnBroadcast)
        btnStopBroadcast = findViewById(R.id.btnStopBroadcast)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        semakKebenaran()

        btnScan.setOnClickListener {
            mulaImbas()
        }

        btnBroadcast.setOnClickListener {
            mulaPancar()
        }

        btnStopBroadcast.setOnClickListener {
            hentiPancar()
        }
    }

    private fun semakKebenaran() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun mulaImbas() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            tvStatus.text = "Sila aktifkan Bluetooth telefon dulu."
            return
        }

        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) {
            tvStatus.text = "Cip BLE Scanner tidak dijumpai."
            return
        }

        tvStatus.text = "Sedang mengimbas signal BLE..."
        bleScanner?.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun mulaPancar() {
        if (capturedManufacturerData == null) {
            tvStatus.text = "Tiada data tersimpan! Imbas dulu peranti sasaran."
            return
        }

        bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bleAdvertiser == null || !bluetoothAdapter!!.isMultipleAdvertisementSupported) {
            tvStatus.text = "Telefon ini tidak menyokong BLE Advertising (cip radio terhad)."
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(capturedManufacturerId, capturedManufacturerData!!)
            .build()

        tvStatus.text = "Memulakan pemancaran klon..."
        bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    private fun hentiPancar() {
        bleAdvertiser?.stopAdvertising(advertiseCallback)
        tvStatus.text = "Pancaran klon telah dihentikan."
    }
}
