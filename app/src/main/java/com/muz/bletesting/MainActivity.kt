// ==========================================
// BAHAGIAN 1: IMPORTS, STRUCT & LIFECYCLE
// (Salin bahagian ini dahulu)
// ==========================================

package com.muz.bletesting

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArrayList

data class BleTarget(
    val name: String,
    val address: String,
    val manufacturerId: Int,
    val manufacturerData: ByteArray,
    val rssi: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BleTarget
        return address == other.address
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button
    private lateinit var btnBroadcast: Button
    private lateinit var btnStopBroadcast: Button
    private lateinit var lvDevices: ListView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null

    // Guna CopyOnWriteArrayList supaya thread-safe semasa imbasan laju
    private val deviceList = CopyOnWriteArrayList<BleTarget>()
    private lateinit var listAdapter: ArrayAdapter<BleTarget>
    private var selectedTarget: BleTarget? = null
    private var isScanning = false
    private var isAdvertising = false

    private lateinit var sharedPreferences: SharedPreferences

    // Throttling UI elak skrin jem bila lambakan isyarat masuk serentak
    private val updateHandler = Handler(Looper.getMainLooper())
    private var pendingUiUpdate = false
    private val uiUpdateRunnable = Runnable {
        listAdapter.notifyDataSetChanged()
        pendingUiUpdate = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("BLE_STORAGE", Context.MODE_PRIVATE)

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

        muatDataTersimpan()

        // Ambil data terus melalui adapter supaya bebas daripada ralat index out of bounds
        lvDevices.setOnItemClickListener { _, _, position, _ ->
            val item = listAdapter.getItem(position) ?: return@setOnItemClickListener
            selectedTarget = item
            hentiImbas()

            simpanDataTarget(item)

            val hexData = item.manufacturerData.joinToString("") { "%02X".format(it) }
            tvStatus.text = """
                PROFIL DISIMPAN KE STORAN KEKAL:
                
                Peranti: ${item.name}
                MAC Sumber: ${item.address}
                ID: ${item.manufacturerId}
                Data Hex: $hexData
                
                (Sedia dipancar bila-bila masa! Tekan butang 'Pancar')
            """.trimIndent()
            Toast.makeText(this, "${item.name} telah disimpan!", Toast.LENGTH_SHORT).show()
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

    // --- PENGURUSAN STORAN KEKAL & FORMATTING ---

    private fun simpanDataTarget(target: BleTarget) {
        val hex = target.manufacturerData.joinToString("") { "%02X".format(it) }
        sharedPreferences.edit()
            .putString("SAVED_NAME", target.name)
            .putString("SAVED_MAC", target.address)
            .putInt("SAVED_MAN_ID", target.manufacturerId)
            .putString("SAVED_HEX", hex)
            .apply()
    }

    private fun muatDataTersimpan() {
        val name = sharedPreferences.getString("SAVED_NAME", null)
        val mac = sharedPreferences.getString("SAVED_MAC", null)
        val manId = sharedPreferences.getInt("SAVED_MAN_ID", -1)
        val hex = sharedPreferences.getString("SAVED_HEX", null)

        if (name != null && hex != null) {
            val payload = hexToByteArray(hex)
            selectedTarget = BleTarget(name, mac ?: "Alamat Disimpan", manId, payload, 0)
            tvStatus.text = """
                DATA TERSIMPAN DIMUATKAN SEMULA:
                
                Peranti: $name
                MAC Sumber: $mac
                ID Vendor: $manId
                Data Hex: $hex
                
                (Sedia dipancar tanpa perlu imbas! Tekan butang 'Pancar')
            """.trimIndent()
        }
    }

    private fun hexToByteArray(hex: String): ByteArray {
        val cleanHex = if (hex.length % 2 != 0) "0$hex" else hex
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
// ==========================================
// BAHAGIAN 2: LOGIK BLE, IMBAS & PANCAR
// (Sambung tampal tepat di bawah Bahagian 1)
// ==========================================

    // --- LOGIK PENGECAMAN JENAMA & EKSTRAK ASCII ---

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
            if (type == 0x08 || type == 0x09) {
                val nameBytes = bytes.copyOfRange(index + 2, index + 1 + length)
                return String(nameBytes, Charsets.UTF_8).trim()
            }
            index += length + 1
        }
        return null
    }

    private fun tentukanNamaPeranti(record: ScanRecord, rawDeviceName: String?, manId: Int, payload: ByteArray): String {
        val officialName = record.deviceName ?: rawDeviceName
        if (!officialName.isNullOrBlank() && !officialName.contains("Tanpa Nama", true)) {
            return officialName
        }

        val rawName = cariNamaDalamRawBytes(record.bytes)
        if (!rawName.isNullOrBlank()) {
            return rawName
        }

        val ascii = ekstrakTeksAscii(payload)
        val namaSyarikat = kenalpastiSyarikat(manId)

        if (!ascii.isNullOrBlank()) {
            return "$namaSyarikat ($ascii)"
        }

        return "Peranti $namaSyarikat"
    }

    // --- PENGURUSAN PERMISSIONS ---

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Kebenaran Bluetooth & Lokasi diperlukan!", Toast.LENGTH_LONG).show()
        }
    }

    private fun adaKebenaranBle(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
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

    private fun isLocationServiceEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    // --- CALLBACK & OPERASI IMBASAN BLE ---

    private val scanCallback = object : ScanCallback() {
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
                // Elak SecurityException bila akses device.name pada Android 12+
                val rawDeviceName = try {
                    device.name
                } catch (e: SecurityException) {
                    null
                }

                val finalName = tentukanNamaPeranti(record, rawDeviceName, mId, payload)
                val target = BleTarget(finalName, devAddress, mId, payload, result.rssi)

                val existingIndex = deviceList.indexOfFirst { it.address == devAddress }
                if (existingIndex != -1) {
                    deviceList[existingIndex] = target
                } else {
                    deviceList.add(target)
                }

                if (!pendingUiUpdate) {
                    pendingUiUpdate = true
                    updateHandler.postDelayed(uiUpdateRunnable, 500)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            runOnUiThread {
                tvStatus.text = "Imbasan gagal dijalankan. Ralat kod: $errorCode"
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun mulaImbas() {
        if (!adaKebenaranBle()) {
            semakKebenaran()
            tvStatus.text = "Kebenaran belum lengkap. Sila benarkan akses."
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            tvStatus.text = "Sila hidupkan Bluetooth telefon dahulu."
            return
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            if (!isLocationServiceEnabled()) {
                tvStatus.text = "Sila aktifkan GPS/Lokasi di bar notifikasi untuk mengimbas BLE!"
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                return
            }
        }

        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) {
            tvStatus.text = "Cip BLE Scanner tidak dijumpai."
            return
        }

        deviceList.clear()
        listAdapter.notifyDataSetChanged()

        isScanning = true
        btnScan.text = "Henti Imbas"
        tvStatus.text = "Sedang mengimbas di udara...\nKetik mana-mana peranti untuk simpan & pancar semula."

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner?.startScan(emptyList(), settings, scanCallback)
        } catch (e: Exception) {
            tvStatus.text = "Ralat imbas: ${e.localizedMessage}"
            isScanning = false
            btnScan.text = "1. Mula Imbas Signal"
        }
    }

    @SuppressLint("MissingPermission")
    private fun hentiImbas() {
        if (isScanning) {
            try {
                bleScanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                // Abaikan jika Bluetooth adapter terpadam tiba-tiba
            }
            isScanning = false
            btnScan.text = "1. Mula Imbas Signal"
        }
        updateHandler.removeCallbacks(uiUpdateRunnable)
        pendingUiUpdate = false
    }

    // --- CALLBACK & OPERASI PEMANCAR (ADVERTISER) ---

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            runOnUiThread {
                val hex = selectedTarget?.manufacturerData?.joinToString("") { "%02X".format(it) } ?: "TIADA"
                val txLabel = when (settingsInEffect?.txPowerLevel) {
                    AdvertiseSettings.ADVERTISE_TX_POWER_HIGH -> "HIGH (+1 hingga +4 dBm)"
                    AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM -> "MEDIUM (-7 dBm)"
                    AdvertiseSettings.ADVERTISE_TX_POWER_LOW -> "LOW (-15 dBm)"
                    AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW -> "ULTRA LOW (-21 dBm)"
                    else -> "MAKSIMUM (+1 hingga +4 dBm)"
                }

                val vendorLabel = if (selectedTarget?.manufacturerId != -1) {
                    "${selectedTarget?.manufacturerId}"
                } else {
                    "0xFFFF (Fallback Data Mentah)"
                }

                tvStatus.text = """
                    ● SIGNAL SEDANG DIPANCARKAN SECARA AKTIF
                    
                    Peranti Sumber: ${selectedTarget?.name}
                    MAC Sumber: ${selectedTarget?.address} (Rujuk data; pemancar guna RPA Android)
                    ID Vendor: $vendorLabel
                    Kuasa TX: $txLabel [Paras Penuh Perkakasan]
                    Mod Latensi: LOW_LATENCY (Pancaran Berterusan)
                    Anggaran Liputan: 10 - 20 meter (kawasan terbuka)
                    
                    Payload (${selectedTarget?.manufacturerData?.size ?: 0} Bytes):
                    $hex
                """.trimIndent()
            }
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            isAdvertising = false
            runOnUiThread {
                val ralatDesc = when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data melebihi had 31 bait BLE!"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Terlalu banyak pemancar aktif dalam sistem!"
                    ADVERTISE_FAILED_ALREADY_STARTED -> "Pemancar sudah sedia aktif."
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "Ralat pemacu Bluetooth dalaman telefon."
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Cip radio telefon tidak menyokong fungsi BLE Advertising."
                    else -> "Ralat kod tidak diketahui: $errorCode"
                }
                tvStatus.text = "Gagal memancar klon!\nPunca: $ralatDesc"
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun mulaPancar() {
        if (!adaKebenaranBle()) {
            semakKebenaran()
            tvStatus.text = "Kebenaran Bluetooth belum lengkap untuk memancar."
            return
        }

        val target = selectedTarget
        if (target == null) {
            tvStatus.text = "Tiada signal dipilih atau tersimpan! Sila imbas dahulu."
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            tvStatus.text = "Sila hidupkan Bluetooth telefon dahulu."
            return
        }

        bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bleAdvertiser == null || !bluetoothAdapter!!.isMultipleAdvertisementSupported) {
            tvStatus.text = "Telefon ini tidak menyokong fungsi pemancar BLE (cip terhad)."
            return
        }

        // Hentikan sesi aktif terdahulu bagi mengelak ralat ALREADY_STARTED
        if (isAdvertising) {
            hentiPancar()
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

        val effectiveManId = if (target.manufacturerId != -1) target.manufacturerId else 0xFFFF

        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(effectiveManId, payload)

        tvStatus.text = "Menghantar arahan pemancar ke cip radio telefon..."
        try {
            bleAdvertiser?.startAdvertising(settings, dataBuilder.build(), advertiseCallback)
            isAdvertising = true
        } catch (e: Exception) {
            tvStatus.text = "Ralat memancar: ${e.localizedMessage}"
            isAdvertising = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun hentiPancar() {
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            // Tangkap sebarang ralat jika Bluetooth ditutup dari luar
        }
        isAdvertising = false
        tvStatus.text = "Pancaran klon telah dihentikan sepenuhnya."
    }

    override fun onDestroy() {
        super.onDestroy()
        hentiImbas()
        hentiPancar()
    }
}
