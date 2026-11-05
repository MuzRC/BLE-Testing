package com.muz.bletesting.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.TextView
import com.muz.bletesting.model.BleTarget
import com.muz.bletesting.utils.BleParserUtils

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
            text1.text = "${item.name} (${item.rssi} dBm)"

            if (item.manufacturerId != -1) {
                val hex = BleParserUtils.byteArrayToHex(item.manufacturerData)
                text2.text = "MAC: ${item.address}\nID Vendor: ${item.manufacturerId} | Data: $hex"
            } else if (item.serviceUuid != null) {
                val shortUuid = BleParserUtils.formatShortUuid(item.serviceUuid)
                val hex = BleParserUtils.byteArrayToHex(item.serviceData)
                text2.text = "MAC: ${item.address}\nService UUID: $shortUuid | Data: $hex"
            } else {
                val hex = BleParserUtils.byteArrayToHex(item.manufacturerData)
                text2.text = "MAC: ${item.address}\nRaw Data: $hex"
            }
        }

        // Animasi baris senarai meluncur masuk perlahan-lahan
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
