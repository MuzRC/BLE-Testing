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

// Model data untuk simpan maklumat setiap peranti BLE
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

            val devName = record.deviceName ?: device.name ?: "Peranti Tanpa Nama"
            val devAddress = device.address ?: return
            val manData = record.manufacturerSpecificData

            var mId = 0x004C // Default fallback ID
            var payload: ByteArray? = null

            if (manData != null && manData.size() > 0) {
                mId = manData.keyAt(0)
                payload = manData.valueAt(0)
            } else {
                val rawBytes = record.bytes
                if (rawBytes != null && rawBytes.isNotEmpty()) {
                    payload = rawBytes.take(20).toByteArray()
                }
            }

            if (payload != null) {
                val target = BleTarget(devName, devAddress, mId, payload, result.rssi)

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

        // Adapter untuk ListView (paparan nama & data hex)
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

        // Tindakan bila pengguna ketik mana-mana peranti dalam senarai
        lvDevices.setOnItemClickListener { _, _, position, _ ->
            val item = deviceList[position]
            selectedTarget = item
            hentiImbas()

            val hexData = item.manufacturerData.joinToString("") { "%02X".format(it) }
            tvStatus.text = "DIPILIH:\nNama: ${item.name}\nMAC: ${item.address}\nID: ${item.manufacturerId}\nHex: $hexData\n\n(Sedia dipancar! Tekan butang '2. Pancar Klon')"
            Toast.makeText(this, "Peranti ${item.name} dipilih!", Toast.LENGTH_SHORT).show()
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
        tvStatus.text = "Sedang mengimbas... Ketik mana-mana peranti di bawah untuk menyalin."

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
            tvStatus.text = "Tiada signal dipilih! Sila ketik satu peranti dalam senarai dahulu."
            return
        }

        bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bleAdvertiser == null || !bluetoothAdapter!!.isMultipleAdvertisementSupported) {
            tvStatus.text = "Telefon ini tidak menyokong mod pemancar BLE (cip radio terhad)."
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        // Hadkan data iklan kepada maksima 24 bait agar tidak melebihi had 31 bait BLE
        val payload = if (target.manufacturerData.size > 24) {
            target.manufacturerData.copyOfRange(0, 24)
        } else {
            target.manufacturerData
        }

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(target.manufacturerId, payload)
            .build()

        tvStatus.text = "Memancarkan klon: ${target.name} (${target.address})..."
        bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
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
