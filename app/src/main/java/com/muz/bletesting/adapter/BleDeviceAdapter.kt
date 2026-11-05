package com.muz.bletesting.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
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
            val hex = BleParserUtils.byteArrayToHex(item.manufacturerData)
            val manIdDisplay = if (item.manufacturerId != -1) item.manufacturerId.toString() else "Tiada (Raw)"
            text2.text = "MAC: ${item.address}\nID: $manIdDisplay | Data: $hex"
        }
        return view
    }
}
