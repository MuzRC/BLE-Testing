package com.muz.bletesting

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseSettings
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.muz.bletesting.adapter.BleDeviceAdapter
import com.muz.bletesting.ble.BleAdvertiserManager
import com.muz.bletesting.ble.BleScannerManager
import com.muz.bletesting.dialog.BookmarkDialog
import com.muz.bletesting.dialog.ManualInjectDialog
import com.muz.bletesting.dialog.PacketDiffDialog
import com.muz.bletesting.model.BleTarget
import com.muz.bletesting.storage.BleProfileStorage
import com.muz.bletesting.utils.BleParserUtils
import com.muz.bletesting.utils.CsvExportHelper
import com.muz.bletesting.utils.ProximityAlertManager
import java.util.concurrent.CopyOnWriteArrayList

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvRssiLabel: TextView
    private lateinit var sbRssi: SeekBar
    private lateinit var btnScan: Button
    private lateinit var btnBroadcast: Button
    private lateinit var btnStopBroadcast: Button
    private lateinit var btnBookmarks: Button
    private lateinit var btnManualInject: Button
    private lateinit var btnDiff: Button
    private lateinit var btnExportCsv: Button
    private lateinit var lvDevices: ListView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var profileStorage: BleProfileStorage
    private lateinit var proximityManager: ProximityAlertManager
    private lateinit var scannerManager: BleScannerManager
    private lateinit var advertiserManager: BleAdvertiserManager

    private val deviceList = CopyOnWriteArrayList<BleTarget>()
    private lateinit var listAdapter: BleDeviceAdapter
    private var selectedTarget: BleTarget? = null

    // Simpanan data paket sebelumnya untuk perbandingan Diff automatik
    private var previousHex: String = ""

    private var pulseAnimator: ObjectAnimator? = null

    private val updateHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var pendingUiUpdate = false

    private val uiUpdateRunnable = Runnable {
        val now = System.currentTimeMillis()
        deviceList.removeIf { now - it.lastSeen > 10000 }
        val sortedList = deviceList.sortedByDescending { it.rssi }
        deviceList.clear()
        deviceList.addAll(sortedList)
        listAdapter.notifyDataSetChanged()
        pendingUiUpdate = false
    }

    private val cleanupHandler = Handler(Looper.getMainLooper())
    private val cleanupRunnable = object : Runnable {
        override fun run() {
            if (scannerManager.isScanning) {
                val now = System.currentTimeMillis()
                val saizSebelum = deviceList.size
                deviceList.removeIf { now - it.lastSeen > 10000 }
                if (deviceList.size != saizSebelum) {
                    listAdapter.notifyDataSetChanged()
                }
                cleanupHandler.postDelayed(this, 3000)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Kebenaran Bluetooth diperlukan!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvRssiLabel = findViewById(R.id.tvRssiLabel)
        sbRssi = findViewById(R.id.sbRssi)
        btnScan = findViewById(R.id.btnScan)
        btnBroadcast = findViewById(R.id.btnBroadcast)
        btnStopBroadcast = findViewById(R.id.btnStopBroadcast)
        btnBookmarks = findViewById(R.id.btnBookmarks)
        btnManualInject = findViewById(R.id.btnManualInject)
        btnDiff = findViewById(R.id.btnDiff)
        btnExportCsv = findViewById(R.id.btnExportCsv)
        lvDevices = findViewById(R.id.lvDevices)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        profileStorage = BleProfileStorage(this)
        proximityManager = ProximityAlertManager(this)

        initManagers()

        listAdapter = BleDeviceAdapter(this, deviceList)
        lvDevices.adapter = listAdapter

        setupControls()
        semakKebenaran()
    }

    private fun setupControls() {
        sbRssi.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val rssi = -100 + progress
                scannerManager.rssiThreshold = rssi
                tvRssiLabel.text = "Tapis: $rssi dBm"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        lvDevices.setOnItemClickListener { _, _, position, _ ->
            val item = listAdapter.getItem(position) ?: return@setOnItemClickListener
            kunciTarget(item)
            profileStorage.simpanKeBookmark(item)
            Toast.makeText(this, "${item.name} disimpan ke Bookmark!", Toast.LENGTH_SHORT).show()
        }

        lvDevices.setOnItemLongClickListener { _, _, position, _ ->
            val item = listAdapter.getItem(position) ?: return@setOnItemLongClickListener true
            val hex = if (item.isServiceData) BleParserUtils.byteArrayToHex(item.serviceData) else BleParserUtils.byteArrayToHex(item.manufacturerData)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("BLE Hex", hex)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Hex disalin ke Clipboard!", Toast.LENGTH_SHORT).show()
            true
        }

        btnScan.setOnClickListener {
            animasiButang(it)
            if (scannerManager.isScanning) hentikanImbasan() else mulakanImbasan()
        }

        btnBroadcast.setOnClickListener {
            animasiButang(it)
            mulakanPancaran()
        }

        btnStopBroadcast.setOnClickListener {
            animasiButang(it)
            hentikanPancaran()
        }

        btnBookmarks.setOnClickListener {
            animasiButang(it)
            BookmarkDialog.tunjuk(this, profileStorage) { targetDipilih ->
                kunciTarget(targetDipilih)
                Toast.makeText(this, "Profil ${targetDipilih.name} dipilih!", Toast.LENGTH_SHORT).show()
            }
        }

        btnManualInject.setOnClickListener {
            animasiButang(it)
            ManualInjectDialog.tunjuk(this) { targetManual ->
                kunciTarget(targetManual)
                profileStorage.simpanKeBookmark(targetManual)
                Toast.makeText(this, "Target Manual Disediakan!", Toast.LENGTH_SHORT).show()
            }
        }

        // Buka Pembanding Paket Hex (Diff)
        btnDiff.setOnClickListener {
            animasiButang(it)
            val hexSemasa = selectedTarget?.let { target ->
                if (target.isServiceData) BleParserUtils.byteArrayToHex(target.serviceData)
                else BleParserUtils.byteArrayToHex(target.manufacturerData)
            } ?: ""

            PacketDiffDialog.tunjuk(
                context = this,
                defaultHexAsal = previousHex,
                defaultHexBaru = hexSemasa
            )
        }

        btnExportCsv.setOnClickListener {
            animasiButang(it)
            val berjaya = CsvExportHelper.eksportDanKongsi(this, deviceList)
            if (!berjaya) {
                Toast.makeText(this, "Tiada data untuk dieksport!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun kunciTarget(item: BleTarget) {
        val hexData = if (item.isServiceData) BleParserUtils.byteArrayToHex(item.serviceData) else BleParserUtils.byteArrayToHex(item.manufacturerData)

        // Jika sasaran sama diketik semula dengan bait berbeza, simpan bait lama ke previousHex
        if (selectedTarget != null) {
            val oldHex = if (selectedTarget!!.isServiceData) BleParserUtils.byteArrayToHex(selectedTarget!!.serviceData) else BleParserUtils.byteArrayToHex(selectedTarget!!.manufacturerData)
            if (oldHex != hexData && oldHex.isNotBlank()) {
                previousHex = oldHex
            }
        }

        selectedTarget = item
        hentikanImbasan()

        val infoJenis = if (item.manufacturerId != -1) {
            "Jenis: Manufacturer Data (ID: ${item.manufacturerId})"
        } else if (item.serviceUuid != null) {
            "Jenis: Service Data (${BleParserUtils.formatShortUuid(item.serviceUuid)})"
        } else {
            "Jenis: Raw Data"
        }

        tvStatus.text = """
            PROFIL AKTIF:
            Peranti: ${item.name}
            MAC Sumber: ${item.address}
            $infoJenis
            Data Hex: $hexData
            
            (Sedia untuk dipancar! Tekan 'Pancar')
        """.trimIndent()
    }

    private fun animasiButang(view: View) {
        view.animate()
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(90)
            .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(90).start() }
            .start()
    }

    private fun mulaDenyutanRadar() {
        if (pulseAnimator == null) {
            pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                tvStatus,
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.45f)
            ).apply {
                duration = 700
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
            }
        }
        if (pulseAnimator?.isStarted != true) pulseAnimator?.start()
    }

    private fun hentiDenyutanRadar() {
        pulseAnimator?.cancel()
        tvStatus.alpha = 1f
    }

    private fun initManagers() {
        scannerManager = BleScannerManager(
            bluetoothAdapter = bluetoothAdapter,
            onDeviceFound = { target ->
                val existingIndex = deviceList.indexOfFirst { it.address == target.address }
                if (existingIndex != -1) {
                    val existing = deviceList[existingIndex]
                    existing.lastSeen = System.currentTimeMillis()
                    deviceList[existingIndex] = target
                } else {
                    deviceList.add(target)
                }

                if (selectedTarget?.address == target.address) {
                    proximityManager.semakJarak(target.rssi)
                }

                if (!pendingUiUpdate) {
                    pendingUiUpdate = true
                    updateHandler.postDelayed(uiUpdateRunnable, 400)
                }
            },
            onError = { msg ->
                runOnUiThread {
                    hentiDenyutanRadar()
                    tvStatus.text = msg
                    btnScan.text = "1. Mula Imbas Signal"
                }
            }
        )

        advertiserManager = BleAdvertiserManager(
            bluetoothAdapter = bluetoothAdapter,
            onSuccess = { settingsInEffect ->
                runOnUiThread {
                    mulaDenyutanRadar()
                    val target = selectedTarget
                    val hex = target?.let { if (it.isServiceData) BleParserUtils.byteArrayToHex(it.serviceData) else BleParserUtils.byteArrayToHex(it.manufacturerData) } ?: "TIADA"
                    val payloadSize = if (target?.isServiceData == true) target.serviceData.size else target?.manufacturerData?.size ?: 0

                    val txLabel = when (settingsInEffect?.txPowerLevel) {
                        AdvertiseSettings.ADVERTISE_TX_POWER_HIGH -> "HIGH (+1 hingga +4 dBm)"
                        AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM -> "MEDIUM (-7 dBm)"
                        AdvertiseSettings.ADVERTISE_TX_POWER_LOW -> "LOW (-15 dBm)"
                        AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW -> "ULTRA LOW (-21 dBm)"
                        else -> "MAKSIMUM (+1 hingga +4 dBm)"
                    }

                    val infoJenis = if (target?.manufacturerId != -1) {
                        "Format: Manufacturer Specific (ID: ${target?.manufacturerId})"
                    } else if (target?.serviceUuid != null) {
                        "Format: Service Data (${BleParserUtils.formatShortUuid(target.serviceUuid)})"
                    } else {
                        "Format: Raw Fallback (0xFFFF)"
                    }

                    tvStatus.text = """
                        ● SIGNAL SEDANG DIPANCARKAN SECARA TEPAT
                        Peranti: ${target?.name}
                        MAC Sumber: ${target?.address}
                        $infoJenis
                        Kuasa TX: $txLabel
                        Payload ($payloadSize Bytes):
                        $hex
                    """.trimIndent()
                }
            },
            onFailure = { errorMsg ->
                runOnUiThread {
                    hentiDenyutanRadar()
                    tvStatus.text = errorMsg
                }
            }
        )
    }

    private fun mulakanImbasan() {
        if (!adaKebenaranBle()) {
            semakKebenaran()
            tvStatus.text = "Kebenaran Bluetooth diperlukan."
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            tvStatus.text = "Sila hidupkan Bluetooth telefon."
            return
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R && !isLocationServiceEnabled()) {
            tvStatus.text = "Aktifkan GPS/Lokasi untuk imbasan BLE."
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        deviceList.clear()
        listAdapter.notifyDataSetChanged()

        if (scannerManager.mulaImbas()) {
            mulaDenyutanRadar()
            btnScan.text = "Henti Imbas"
            tvStatus.text = "Mengimbas di udara...\nKetik senarai untuk simpan. Tekan lama untuk salin Hex."
            cleanupHandler.postDelayed(cleanupRunnable, 3000)
        }
    }

    private fun hentikanImbasan() {
        scannerManager.hentiImbas()
        cleanupHandler.removeCallbacks(cleanupRunnable)
        proximityManager.hentiSemua()
        hentiDenyutanRadar()
        btnScan.text = "1. Mula Imbas Signal"
        updateHandler.removeCallbacks(uiUpdateRunnable)
        pendingUiUpdate = false
    }

    private fun mulakanPancaran() {
        if (!adaKebenaranBle()) {
            semakKebenaran()
            return
        }

        val target = selectedTarget
        if (target == null) {
            tvStatus.text = "Tiada signal dipilih! Sila imbas atau pilih profil dahulu."
            return
        }

        advertiserManager.mulaPancar(target)
    }

    private fun hentikanPancaran() {
        advertiserManager.hentiPancar()
        hentiDenyutanRadar()
        tvStatus.text = "Pancaran dihentikan."
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
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.BLUETOOTH_SCAN)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (list.isNotEmpty()) requestPermissionLauncher.launch(list.toTypedArray())
    }

    private fun isLocationServiceEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lm.isLocationEnabled else lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupHandler.removeCallbacks(cleanupRunnable)
        proximityManager.hentiSemua()
        hentiDenyutanRadar()
        hentikanImbasan()
        hentikanPancaran()
    }
}
