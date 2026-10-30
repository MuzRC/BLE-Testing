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
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

data class BleTarget(
    val name: String,
    val address: String,
    val manufacturerId: Int,
    val manufacturerData: ByteArray,
    val rssi: Int
)

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button
    private lateinit var btnBroadcast: Button
    private lateinit var btnStopBroadcast: Button
    private lateinit var lvDevices: ListView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null

    private val deviceList = mutableListOf<BleTarget>()
    private lateinit var listAdapter: ArrayAdapter<BleTarget>
    private var selectedTarget: BleTarget? = null
    private var isScanning = false

    // Callback pemancar klon
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            runOnUiThread {
                tvStatus.text = "Signal klon [${selectedTarget?.name}] sedang dipancarkan secara aktif!"
            }
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            runOnUiThread {
                tvStatus.text = "Gagal memancar. Ralat kod: $errorCode"
            }
        }
    }

    // Callback imbasan BLE
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val record = result?.scanRecord ?: return
            val device = result.device ?: return

            val devAddress = device.address ?: return
            val manData = record.manufacturerSpecificData

            var mId = -1
            var payload: ByteArray? = null

            if (manData != null && manData.size() > 0) {
                mId = manData.keyAt(0)
                payload = manData.valueAt(0)
            } else {
                val rawBytes = record.bytes
                if (rawBytes != null && rawBytes.isNotEmpty()) {
                    payload = rawBytes.take(24).toByteArray()
                }
            }

            if (payload != null) {
                // Tentukan nama pintar berdasarkan data mentah
                val finalName = tentukanNamaPeranti(record, device.name, mId, payload)
                val target = BleTarget(finalName, devAddress, mId, payload, result.rssi)

                runOnUiThread {
                    val existingIndex = deviceList.indexOfFirst { it.address == devAddress }
                    if (existingIndex != -1) {
                        deviceList[existingIndex] = target
                    } else {
                        deviceList.add(target)
                    }
                    listAdapter.notifyDataSetChanged()
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            runOnUiThread {
                tvStatus.text = "Imbasan gagal. Ralat kod: $errorCode"
            }
        }
    }

    // --- LOGIK PENGECAMAN NAMA & VENDOR ---

    private fun kenalpastiSyarikat(id: Int): String {
        return when (id) {
            0x0006 -> "Microsoft"
            0x004C -> "Apple"
            0x0075 -> "Samsung"
            0x000F -> "Broadcom"
            0x0059 -> "Nordic Semiconductor"
            0x00E0 -> "Google"
            0x0157 -> "Anker"
            0x038F -> "Xiaomi"
            0x07D0 -> "Tuya Smart"
            -1 -> "Generic BLE"
            else -> "Vendor #$id"
        }
    }

    private fun ekstrakTeksAscii(bytes: ByteArray): String? {
        var currentChunk = StringBuilder()
        for (b in bytes) {
            val c = b.toInt().toChar()
            if (c in '0'..'9' || c in 'A'..'Z' || c in 'a'..'z' || c == '-' || c == '_') {
                currentChunk.append(c)
            } else {
                if (currentChunk.length >= 4) {
                    return currentChunk.toString()
                }
                currentChunk = StringBuilder()
            }
        }
        return if (currentChunk.length >= 4) currentChunk.toString() else null
    }

    private fun cariNamaDalamRawBytes(bytes: ByteArray?): String? {
        if (bytes == null) return null
        var index = 0
        while (index < bytes.size) {
            val length = bytes[index].toInt() and 0xFF
            if (length == 0 || index + length >= bytes.size) break
            val type = bytes[index + 1].toInt() and 0xFF
            if (type == 0x08 || type == 0x09) { // 0x08/0x09 adalah standard Local Name BLE
                val nameBytes = bytes.copyOfRange(index + 2, index + 1 + length)
                return String(nameBytes, Charsets.UTF_8).trim()
            }
            index += length + 1
        }
        return null
    }

    private fun tentukanNamaPeranti(record: ScanRecord, rawDeviceName: String?, manId: Int, payload: ByteArray): String {
        // 1. Ambil nama rasmi jika ada
        val officialName = record.deviceName ?: rawDeviceName
        if (!officialName.isNullOrBlank() && !officialName.contains("Tanpa Nama", true)) {
            return officialName
        }

        // 2. Cari dalam paket iklan AD mentah
        val rawName = cariNamaDalamRawBytes(record.bytes)
        if (!rawName.isNullOrBlank()) {
            return rawName
        }

        // 3. Cari teks ASCII dalam payload data (cth: nama laptop 'DESKTOP-XXX')
        val ascii = ekstrakTeksAscii(payload)
        val namaSyarikat = kenalpastiSyarikat(manId)

        if (!ascii.isNullOrBlank()) {
            return "$namaSyarikat ($ascii)"
        }

        // 4. Jika tiada teks langsung, sekurang-kurangnya guna jenama/vendor
        return "Peranti $namaSyarikat"
    }

    // --- PENGURUSAN AKTIVITI & PERMISSIONS ---

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Kebenaran Bluetooth & Lokasi diperlukan!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnScan = findViewById(R.id.btnScan)
        btnBroadcast = findViewById(R.id.btnBroadcast)
        btnStopBroadcast = findViewById(R.id.btnStopBroadcast)
        lvDevices = findViewById(R.id.lvDevices)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        listAdapter = object : ArrayAdapter<BleTarget>(
            this,
            android.R.layout.simple_list_item_2,
            android.R.id.text1,
            deviceList
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val text1 = view.findViewById<TextView>(android.R.id.text1)
                val text2 = view.findViewById<TextView>(android.R.id.text2)

                val item = getItem(position)
                if (item != null) {
                    text1.text = "${item.name} (${item.rssi} dBm)"
                    val hex = item.manufacturerData.joinToString("") { "%02X".format(it) }
                    text2.text = "MAC: ${item.address}\nID: ${item.manufacturerId} | Data: $hex"
                }
                return view
            }
        }
        lvDevices.adapter = listAdapter

        lvDevices.setOnItemClickListener { _, _, position, _ ->
            val item = deviceList[position]
            selectedTarget = item
            hentiImbas()

            val hexData = item.manufacturerData.joinToString("") { "%02X".format(it) }
            tvStatus.text = "DIPILIH:\nNama: ${item.name}\nMAC: ${item.address}\nID: ${item.manufacturerId}\nHex: $hexData\n\n(Tekan '2. Pancar Klon' untuk siarkan)"
            Toast.makeText(this, "${item.name} dipilih!", Toast.LENGTH_SHORT).show()
        }

        semakKebenaran()

        btnScan.setOnClickListener {
            if (isScanning) {
                hentiImbas()
            } else {
                mulaImbas()
            }
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
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun mulaImbas() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            tvStatus.text = "Sila hidupkan Bluetooth telefon dahulu."
            return
        }

        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) {
            tvStatus.text = "Cip BLE Scanner tidak dijumpai."
            return
        }

        deviceList.clear()
        listAdapter.notifyDataSetChanged()
        selectedTarget = null

        isScanning = true
        btnScan.text = "Henti Imbas"
        tvStatus.text = "Sedang mengimbas... Ketik peranti untuk salin."

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(emptyList(), settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun hentiImbas() {
        if (isScanning) {
            bleScanner?.stopScan(scanCallback)
            isScanning = false
            btnScan.text = "1. Mula Imbas Signal"
        }
    }

    @SuppressLint("MissingPermission")
    private fun mulaPancar() {
        val target = selectedTarget
        if (target == null) {
            tvStatus.text = "Pilih satu peranti dalam senarai dahulu!"
            return
        }

        bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bleAdvertiser == null || !bluetoothAdapter!!.isMultipleAdvertisementSupported) {
            tvStatus.text = "Telefon ini tidak menyokong penyiaran BLE."
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val payload = if (target.manufacturerData.size > 24) {
            target.manufacturerData.copyOfRange(0, 24)
        } else {
            target.manufacturerData
        }

        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)

        if (target.manufacturerId != -1) {
            dataBuilder.addManufacturerData(target.manufacturerId, payload)
        }

        tvStatus.text = "Memancarkan klon: ${target.name}..."
        bleAdvertiser?.startAdvertising(settings, dataBuilder.build(), advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    private fun hentiPancar() {
        bleAdvertiser?.stopAdvertising(advertiseCallback)
        tvStatus.text = "Pancaran klon telah dihentikan."
    }

    override fun onDestroy() {
        super.onDestroy()
        hentiImbas()
        hentiPancar()
    }
}
