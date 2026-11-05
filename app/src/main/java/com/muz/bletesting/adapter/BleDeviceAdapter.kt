package com.muz.bletesting.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.TextView
import com.muz.bletesting.decoder.BeaconDecoder
import com.muz.bletesting.model.BleTarget
import com.muz.bletesting.utils.BleParserUtils
import com.muz.bletesting.utils.BleVendorResolver

class BleDeviceAdapter(
    context: Context,
    devices: List<BleTarget>
) : ArrayAdapter<BleTarget>(
    context,
    android.R.layout.simple_list_item_2,
    android.R.id.text1,
    devices
) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val text1 = view.findViewById<TextView>(android.R.id.text1)
        val text2 = view.findViewById<TextView>(android.R.id.text2)

        val item = getItem(position)
        if (item != null) {
            // Huraikan jenama pengeluar cip daripada OUI dan Manufacturer ID
            val vendorMac = BleVendorResolver.huraikanMacVendor(item.address)
            val vendorCompany = if (item.manufacturerId != -1) {
                BleVendorResolver.huraikanCompanyId(item.manufacturerId)
            } else null

            val labelPengeluar = when {
                vendorCompany != null && vendorCompany != "Tiada Vendor ID" -> "[$vendorCompany]"
                else -> "[$vendorMac]"
            }

            text1.text = "${item.name} (${item.rssi} dBm) $labelPengeluar"

            // 1. Semak jika peranti memancarkan format Beacon Standard
            val iBeacon = BeaconDecoder.kesanIBeacon(item.manufacturerId, item.manufacturerData)
            val eddystone = BeaconDecoder.kesanEddystone(item.serviceUuid, item.serviceData)

            if (iBeacon != null) {
                text2.text = "MAC: ${item.address} | $vendorMac\n★ $iBeacon"
            } else if (eddystone != null) {
                text2.text = "MAC: ${item.address} | $vendorMac\n★ $eddystone"
            } else if (item.manufacturerId != -1) {
                val hex = BleParserUtils.byteArrayToHex(item.manufacturerData)
                text2.text = "MAC: ${item.address} | $vendorMac\nPengeluar: ${vendorCompany ?: "0x" + Integer.toHexString(item.manufacturerId)} | Hex: $hex"
            } else if (item.serviceUuid != null) {
                val shortUuid = BleParserUtils.formatShortUuid(item.serviceUuid)
                val hex = BleParserUtils.byteArrayToHex(item.serviceData)
                text2.text = "MAC: ${item.address} | $vendorMac\nService UUID: $shortUuid | Hex: $hex"
            } else {
                val hex = BleParserUtils.byteArrayToHex(item.manufacturerData)
                text2.text = "MAC: ${item.address} | $vendorMac\nData Mentah: $hex"
            }
        }

        if (convertView == null) {
            view.alpha = 0f
            view.translationY = 30f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        return view
    }
}
