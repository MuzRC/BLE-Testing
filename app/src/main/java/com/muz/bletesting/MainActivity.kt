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
import android.graphics.Color
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
import com.google.android.material.tabs.TabLayout
import com.muz.bletesting.adapter.BleDeviceAdapter
import com.muz.bletesting.ble.BleAdvertiserManager
import com.muz.bletesting.ble.BleScannerManager
import com.muz.bletesting.dialog.BookmarkDialog
import com.muz.bletesting.dialog.BurstConfigDialog
import com.muz.bletesting.dialog.GattExplorerDialog
import com.muz.bletesting.dialog.ManualInjectDialog
import com.muz.bletesting.dialog.PacketDiffDialog
import com.muz.bletesting.dialog.RssiTrackerDialog
import com.muz.bletesting.model.BleTarget
import com.muz.bletesting.storage.BleProfileStorage
import com.muz.bletesting.utils.BleAnomalyDetector
import com.muz.bletesting.utils.BleParserUtils
import com.muz.bletesting.utils.CsvExportHelper
import com.muz.bletesting.utils.ProximityAlertManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class MainActivity : AppCompatActivity() {

    // Pengurus 4 Tab
    private lateinit var tabLayout: TabLayout
    private lateinit var layoutTabScanner: View
    private lateinit var layoutTabAdvertiser: View
    private lateinit var layoutTabTools: View
    private lateinit var layoutTabSecurity: View

    // Komponen Skrin
    private lateinit var tvStatus: TextView
    private lateinit var tvRssiLabel: TextView
    private lateinit var sbRssi: SeekBar
    private lateinit var btnScan: Button
    private lateinit var btnBroadcast: Button
    private lateinit var btnStopBroadcast: Button
    private lateinit var btnConfigBurst: Button
    private lateinit var btnOpenRadar: Button
    private lateinit var btnDiff: Button
    private lateinit var btnManualInject: Button
    private lateinit var btnBookmarks: Button
    private lateinit var btnExportCsv: Button
    private lateinit var btnGattExplorer: Button
    private lateinit var lvDevices: ListView

    // Komponen Tab 4: Sekuriti
    private lateinit var layoutThreatStatus: View
    private lateinit var tvThreatBadge: TextView
    private lateinit var tvThreatDetail: TextView
    private lateinit var tvPpsCounter: TextView
    private lateinit var tvAnomalyLog: TextView
    private lateinit var svAnomalyLog: ScrollView
    private lateinit var btnClearSecurityLog: Button

    private val securityLogBuilder = StringBuilder()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var profileStorage: BleProfileStorage
    private lateinit var proximityManager: ProximityAlertManager
    private lateinit var scannerManager: BleScannerManager
    private lateinit var advertiserManager: BleAdvertiserManager

    private val deviceList = CopyOnWriteArrayList<BleTarget>()
    private lateinit var listAdapter: BleDeviceAdapter
    private var selectedTarget: BleTarget? = null

    private var previousHex: String = ""
    private var rssiTrackerDialog: RssiTrackerDialog? = null
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

        // Sambung Bekas Tab
        tabLayout = findViewById(R.id.tabLayout)
        layoutTabScanner = findViewById(R.id.layoutTabScanner)
        layoutTabAdvertiser = findViewById(R.id.layoutTabAdvertiser)
        layoutTabTools = findViewById(R.id.layoutTabTools)
        layoutTabSecurity = findViewById(R.id.layoutTabSecurity)

        // Sambung Komponen Paparan
        tvStatus = findViewById(R.id.tvStatus)
        tvRssiLabel = findViewById(R.id.tvRssiLabel)
        sbRssi = findViewById(R.id.sbRssi)
        btnScan = findViewById(R.id.btnScan)
        btnBroadcast = findViewById(R.id.btnBroadcast)
        btnStopBroadcast = findViewById(R.id.btnStopBroadcast)
        btnConfigBurst = findViewById(R.id.btnConfigBurst)
        btnOpenRadar = findViewById(R.id.btnOpenRadar)
        btnDiff = findViewById(R.id.btnDiff)
        btnManualInject = findViewById(R.id.btnManualInject)
        btnBookmarks = findViewById(R.id.btnBookmarks)
        btnExportCsv = findViewById(R.id.btnExportCsv)
        btnGattExplorer = findViewById(R.id.btnGattExplorer)
        lvDevices = findViewById(R.id.lvDevices)

        // Komponen Tab Sekuriti
        layoutThreatStatus = findViewById(R.id.layoutThreatStatus)
        tvThreatBadge = findViewById(R.id.tvThreatBadge)
        tvThreatDetail = findViewById(R.id.tvThreatDetail)
        tvPpsCounter = findViewById(R.id.tvPpsCounter)
        tvAnomalyLog = findViewById(R.id.tvAnomalyLog)
        svAnomalyLog = findViewById(R.id.svAnomalyLog)
        btnClearSecurityLog = findViewById(R.id.btnClearSecurityLog)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        profileStorage = BleProfileStorage(this)
        proximityManager = ProximityAlertManager(this)

        initManagers()

        listAdapter = BleDeviceAdapter(this, deviceList)
        lvDevices.adapter = listAdapter

        setupTabNavigation()
        setupControls()
        semakKebenaran()
    }

    private fun setupTabNavigation() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                layoutTabScanner.visibility = if (tab?.position == 0) View.VISIBLE else View.GONE
                layoutTabAdvertiser.visibility = if (tab?.position == 1) View.VISIBLE else View.GONE
                layoutTabTools.visibility = if (tab?.position == 2) View.VISIBLE else View.GONE
                layoutTabSecurity.visibility = if (tab?.position == 3) View.VISIBLE else View.GONE
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupControls() {
        // Tab 1: Penggelongsor (Slider) RSSI
        sbRssi.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val rssi = -100 + progress
                scannerManager.rssiThreshold = rssi
                tvRssiLabel.text = "Tapis: $rssi dBm"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Tab 1: Ketik Senarai untuk Kunci Sasaran
        lvDevices.setOnItemClickListener { _, _, position, _ ->
            val item = listAdapter.getItem(position) ?: return@setOnItemClickListener
            kunciTarget(item)
            profileStorage.simpanKeBookmark(item)
            Toast.makeText(this, "${item.name} dikunci & disimpan!", Toast.LENGTH_SHORT).show()
            tabLayout.getTabAt(1)?.select()
        }

        // Tab 1: Tekan Lama untuk Salin Hex
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

        // Tab 2: Kawalan Pancaran
        btnBroadcast.setOnClickListener {
            animasiButang(it)
            mulakanPancaran()
        }

        btnStopBroadcast.setOnClickListener {
            animasiButang(it)
            hentikanPancaran()
        }

        btnConfigBurst.setOnClickListener {
            animasiButang(it)
            BurstConfigDialog.tunjuk(
                context = this,
                isBurstActive = advertiserManager.isBurstMode,
                currentOnSec = advertiserManager.burstOnSeconds,
                currentOffSec = advertiserManager.burstOffSeconds
            ) { isEnabled, onSec, offSec ->
                advertiserManager.isBurstMode = isEnabled
                advertiserManager.burstOnSeconds = onSec
                advertiserManager.burstOffSeconds = offSec
            }
        }

        // Tab 3: Alatan Audit
        btnGattExplorer.setOnClickListener {
            animasiButang(it)
            val target = selectedTarget
            if (target != null) {
                hentikanImbasan()
                GattExplorerDialog.tunjuk(this, bluetoothAdapter, target)
            } else {
                Toast.makeText(this, "Sila imbas dan pilih sasaran di Tab Imbas dahulu!", Toast.LENGTH_SHORT).show()
            }
        }

        btnOpenRadar.setOnClickListener {
            animasiButang(it)
            val target = selectedTarget
            if (target != null) {
                rssiTrackerDialog = RssiTrackerDialog(this, target).apply { show() }
            } else {
                Toast.makeText(this, "Sila pilih sasaran di Tab Imbas dahulu!", Toast.LENGTH_SHORT).show()
            }
        }

        btnDiff.setOnClickListener {
            animasiButang(it)
            val target = selectedTarget
            val hexSemasa = if (target != null) {
                if (target.isServiceData) BleParserUtils.byteArrayToHex(target.serviceData) else BleParserUtils.byteArrayToHex(target.manufacturerData)
            } else ""

            PacketDiffDialog.tunjuk(
                context = this,
                defaultHexAsal = previousHex,
                defaultHexBaru = hexSemasa
            )
        }

        btnManualInject.setOnClickListener {
            animasiButang(it)
            ManualInjectDialog.tunjuk(this) { targetManual ->
                kunciTarget(targetManual)
                profileStorage.simpanKeBookmark(targetManual)
                Toast.makeText(this, "Target Manual Disiapkan!", Toast.LENGTH_SHORT).show()
                tabLayout.getTabAt(1)?.select()
            }
        }

        btnBookmarks.setOnClickListener {
            animasiButang(it)
            BookmarkDialog.tunjuk(this, profileStorage) { targetDipilih ->
                kunciTarget(targetDipilih)
                Toast.makeText(this, "Profil ${targetDipilih.name} dipilih!", Toast.LENGTH_SHORT).show()
                tabLayout.getTabAt(1)?.select()
            }
        }

        btnExportCsv.setOnClickListener {
            animasiButang(it)
            val berjaya = CsvExportHelper.eksportDanKongsi(this, deviceList)
            if (!berjaya) {
                Toast.makeText(this, "Tiada data untuk dieksport!", Toast.LENGTH_SHORT).show()
            }
        }

        // Tab 4: Kosongkan Log Insiden Sekuriti
        btnClearSecurityLog.setOnClickListener {
            animasiButang(it)
            securityLogBuilder.clear()
            tvAnomalyLog.text = "[Log insiden dikosongkan.]"
            BleAnomalyDetector.reset()
            Toast.makeText(this, "Log dikosongkan!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun kunciTarget(item: BleTarget) {
        val hexData = if (item.isServiceData) BleParserUtils.byteArrayToHex(item.serviceData) else BleParserUtils.byteArrayToHex(item.manufacturerData)

        if (selectedTarget != null) {
            val oldHex = if (selectedTarget!!.isServiceData) BleParserUtils.byteArrayToHex(selectedTarget!!.serviceData) else BleParserUtils.byteArrayToHex(selectedTarget!!.manufacturerData)
            if (oldHex != hexData && oldHex.isNotBlank()) {
                previousHex = oldHex
            }
        }

        selectedTarget = item

        val infoJenis = if (item.manufacturerId != -1) {
            "Jenis: Manufacturer Specific (ID: ${item.manufacturerId})"
        } else if (item.serviceUuid != null) {
            "Jenis: Service Data (${BleParserUtils.formatShortUuid(item.serviceUuid)})"
        } else {
            "Jenis: Raw Data Fallback"
        }

        tvStatus.text = """
            SASARAN TERKUNCI:
            Peranti: ${item.name}
            MAC: ${item.address}
            $infoJenis
            
            Payload Hex:
            $hexData
            
            (Sedia dipancar! Tekan butang 'Pancar Klon')
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

                // 1. Semakan Jarak Proximity
                if (selectedTarget?.address == target.address) {
                    proximityManager.semakJarak(target.rssi)

                    if (rssiTrackerDialog?.isShowing == true) {
                        runOnUiThread {
                            rssiTrackerDialog?.kemasKiniRssi(target.rssi)
                        }
                    }
                }

                // 2. Analisis Anomali & Serangan Radio untuk Tab 4
                val report = BleAnomalyDetector.prosesPaket(target)
                runOnUiThread {
                    tvPpsCounter.text = "${report.packetsPerSecond} PPS"

                    if (report.isUnderAttack) {
                        layoutThreatStatus.setBackgroundColor(Color.parseColor("#3B141E"))
                        tvThreatBadge.text = "⚠️ AMARAN: SPAM DIKESAN!"
                        tvThreatBadge.setTextColor(Color.parseColor("#FF5252"))
                        tvThreatDetail.text = report.suspiciousReason ?: "Anomali dikesan."

                        val stamp = timeFormat.format(Date())
                        val jenis = report.attackType ?: "Banjir Trafik"
                        securityLogBuilder.append("[$stamp] $jenis | Sumber: ${target.address} (${target.rssi} dBm)\n")
                        tvAnomalyLog.text = securityLogBuilder.toString()
                        svAnomalyLog.post { svAnomalyLog.fullScroll(ScrollView.FOCUS_DOWN) }
                    } else {
                        layoutThreatStatus.setBackgroundColor(Color.parseColor("#161822"))
                        tvThreatBadge.text = "STATUS: UDARA BERSIH"
                        tvThreatBadge.setTextColor(Color.parseColor("#00E676"))
                        tvThreatDetail.text = "Kadar pancaran stabil ($report.packetsPerSecond paket/s)."
                    }
                }

                if (!pendingUiUpdate) {
                    pendingUiUpdate = true
                    updateHandler.postDelayed(uiUpdateRunnable, 400)
                }
            },
            onError = { msg ->
                runOnUiThread {
                    btnScan.text = "Mula Imbas Signal"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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

                    val infoMod = if (advertiserManager.isBurstMode) {
                        "Mod: BURST BERKALA (${advertiserManager.burstOnSeconds}s ON / ${advertiserManager.burstOffSeconds}s OFF)"
                    } else {
                        "Mod: PANCARAN BERTERUSAN (Continuous)"
                    }

                    tvStatus.text = """
                        ● SIGNAL SEDANG DIPANCARKAN
                        Peranti: ${target?.name}
                        MAC: ${target?.address}
                        $infoMod
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
            },
            onPulseStatusChange = { isPulseActive ->
                runOnUiThread {
                    if (isPulseActive) mulaDenyutanRadar() else hentiDenyutanRadar()
                }
            }
        )
    }

    private fun mulakanImbasan() {
        if (!adaKebenaranBle()) {
            semakKebenaran()
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Sila hidupkan Bluetooth telefon.", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R && !isLocationServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        deviceList.clear()
        listAdapter.notifyDataSetChanged()

        if (scannerManager.mulaImbas()) {
            btnScan.text = "Henti Imbas"
            cleanupHandler.postDelayed(cleanupRunnable, 3000)
        }
    }

    private fun hentikanImbasan() {
        scannerManager.hentiImbas()
        cleanupHandler.removeCallbacks(cleanupRunnable)
        proximityManager.hentiSemua()
        btnScan.text = "Mula Imbas Signal"
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
            Toast.makeText(this, "Tiada sasaran dipilih! Sila imbas atau pilih templat dahulu.", Toast.LENGTH_SHORT).show()
            return
        }

        advertiserManager.mulaPancar(target)
    }

    private fun hentikanPancaran() {
        advertiserManager.hentiPancar()
        hentiDenyutanRadar()
        tvStatus.text = "Pancaran dihentikan.\nSedia untuk memancar semula."
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
        rssiTrackerDialog?.dismiss()
        cleanupHandler.removeCallbacks(cleanupRunnable)
        proximityManager.hentiSemua()
        hentiDenyutanRadar()
        hentikanImbasan()
        hentikanPancaran()
    }
}
