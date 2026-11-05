package com.muz.bletesting

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.muz.bletesting.adapter.BleDeviceAdapter
import com.muz.bletesting.ble.BleAdvertiserManager
import com.muz.bletesting.ble.BleScannerManager
import com.muz.bletesting.model.BleTarget
import com.muz.bletesting.storage.BleStorageManager
import com.muz.bletesting.utils.BleParserUtils
import java.util.concurrent.CopyOnWriteArrayList

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button
    private lateinit var btnBroadcast: Button
    private lateinit var btnStopBroadcast: Button
    private lateinit var lvDevices: ListView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var storageManager: BleStorageManager
    private lateinit var scannerManager: BleScannerManager
    private lateinit var advertiserManager: BleAdvertiserManager

    private val deviceList = CopyOnWriteArrayList<BleTarget>()
    private lateinit var listAdapter: BleDeviceAdapter
    private var selectedTarget: BleTarget? = null

    private val updateHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var pendingUiUpdate = false
    private val uiUpdateRunnable = Runnable {
        listAdapter.notifyDataSetChanged()
        pendingUiUpdate = false
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Kebenaran Bluetooth diperlukan untuk operasi penuh!", Toast.LENGTH_LONG).show()
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
        storageManager = BleStorageManager(this)

        initManagers()

        listAdapter = BleDeviceAdapter(this, deviceList)
        lvDevices.adapter = listAdapter

        muatDataTersimpan()
        semakKebenaran()

        lvDevices.setOnItemClickListener { _, _, position, _ ->
            val item = listAdapter.getItem(position) ?: return@setOnItemClickListener
            selectedTarget = item
            hentikanImbasan()

            storageManager.simpanTarget(item)

            val hexData = BleParserUtils.byteArrayToHex(item.manufacturerData)
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

        btnScan.setOnClickListener {
            if (scannerManager.isScanning) {
                hentikanImbasan()
            } else {
                mulakanImbasan()
            }
        }

        btnBroadcast.setOnClickListener {
            mulakanPancaran()
        }

        btnStopBroadcast.setOnClickListener {
            hentikanPancaran()
        }
    }

    private fun initManagers() {
        scannerManager = BleScannerManager(
            bluetoothAdapter = bluetoothAdapter,
            onDeviceFound = { target ->
                val existingIndex = deviceList.indexOfFirst { it.address == target.address }
                if (existingIndex != -1) {
                    deviceList[existingIndex] = target
                } else {
                    deviceList.add(target)
                }

                if (!pendingUiUpdate) {
                    pendingUiUpdate = true
                    updateHandler.postDelayed(uiUpdateRunnable, 400)
                }
            },
            onError = { msg ->
                runOnUiThread {
                    tvStatus.text = msg
                    btnScan.text = "1. Mula Imbas Signal"
                }
            }
        )

        advertiserManager = BleAdvertiserManager(
            bluetoothAdapter = bluetoothAdapter,
            onSuccess = { settingsInEffect ->
                runOnUiThread {
                    val target = selectedTarget
                    val hex = target?.let { BleParserUtils.byteArrayToHex(it.manufacturerData) } ?: "TIADA"
                    val txLabel = when (settingsInEffect?.txPowerLevel) {
                        AdvertiseSettings.ADVERTISE_TX_POWER_HIGH -> "HIGH (+1 hingga +4 dBm)"
                        AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM -> "MEDIUM (-7 dBm)"
                        AdvertiseSettings.ADVERTISE_TX_POWER_LOW -> "LOW (-15 dBm)"
                        AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW -> "ULTRA LOW (-21 dBm)"
                        else -> "MAKSIMUM (+1 hingga +4 dBm)"
                    }

                    val vendorLabel = if (target?.manufacturerId != -1) {
                        "${target?.manufacturerId}"
                    } else {
                        "0xFFFF (Fallback Data Mentah)"
                    }

                    tvStatus.text = """
                        ● SIGNAL SEDANG DIPANCARKAN SECARA AKTIF
                        
                        Peranti Sumber: ${target?.name}
                        MAC Sumber: ${target?.address} (Rujuk data; pemancar guna RPA Android)
                        ID Vendor: $vendorLabel
                        Kuasa TX: $txLabel [Paras Penuh Perkakasan]
                        Mod Latensi: LOW_LATENCY (Pancaran Berterusan)
                        Anggaran Liputan: 10 - 20 meter (kawasan terbuka)
                        
                        Payload (${target?.manufacturerData?.size ?: 0} Bytes):
                        $hex
                    """.trimIndent()
                }
            },
            onFailure = { errorMsg ->
                runOnUiThread {
                    tvStatus.text = errorMsg
                }
            }
        )
    }

    private fun muatDataTersimpan() {
        val target = storageManager.muatTarget()
        if (target != null) {
            selectedTarget = target
            val hex = storageManager.dapatkanSavedHex() ?: BleParserUtils.byteArrayToHex(target.manufacturerData)
            tvStatus.text = """
                DATA TERSIMPAN DIMUATKAN SEMULA:
                
                Peranti: ${target.name}
                MAC Sumber: ${target.address}
                ID Vendor: ${target.manufacturerId}
                Data Hex: $hex
                
                (Sedia dipancar tanpa perlu imbas! Tekan butang 'Pancar')
            """.trimIndent()
        }
    }

    private fun mulakanImbasan() {
        if (!adaKebenaranBle()) {
            semakKebenaran()
            tvStatus.text = "Kebenaran Bluetooth belum lengkap. Sila benarkan akses dahulu."
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            tvStatus.text = "Sila hidupkan Bluetooth telefon dahulu."
            return
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R && !isLocationServiceEnabled()) {
            tvStatus.text = "Sila aktifkan GPS/Lokasi di bar notifikasi untuk mengimbas BLE!"
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        deviceList.clear()
        listAdapter.notifyDataSetChanged()

        if (scannerManager.mulaImbas()) {
            btnScan.text = "Henti Imbas"
            tvStatus.text = "Sedang mengimbas di udara...\nKetik mana-mana peranti untuk simpan & pancar semula."
        }
    }

    private fun hentikanImbasan() {
        scannerManager.hentiImbas()
        btnScan.text = "1. Mula Imbas Signal"
        updateHandler.removeCallbacks(uiUpdateRunnable)
        pendingUiUpdate = false
    }

    private fun mulakanPancaran() {
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

        tvStatus.text = "Menghantar arahan pemancar ke cip radio telefon..."
        advertiserManager.mulaPancar(target)
    }

    private fun hentikanPancaran() {
        advertiserManager.hentiPancar()
        tvStatus.text = "Pancaran klon telah dihentikan sepenuhnya."
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

    override fun onDestroy() {
        super.onDestroy()
        hentikanImbasan()
        hentikanPancaran()
    }
}
