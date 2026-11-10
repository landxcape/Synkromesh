package com.landxcape.synkromesh.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLDecoder
import java.net.URLEncoder

data class RideQrData(
    val channel: Int,
    val callsign: String,
    val ssid: String? = null,
    val password: String? = null
) {
    fun toUriString(): String {
        val params = mutableListOf<String>()
        params.add("ch=$channel")
        params.add("callsign=${URLEncoder.encode(callsign, "UTF-8")}")
        if (!ssid.isNullOrBlank()) {
            params.add("ssid=${URLEncoder.encode(ssid, "UTF-8")}")
        }
        if (!password.isNullOrBlank()) {
            params.add("pwd=${URLEncoder.encode(password, "UTF-8")}")
        }
        return "synkromesh://join?" + params.joinToString("&")
    }

    companion object {
        fun fromUriString(uriString: String): RideQrData? {
            return try {
                if (!uriString.startsWith("synkromesh://join")) return null
                val query = uriString.substringAfter("?", "")
                if (query.isBlank()) return null
                val map = query.split("&").associate { param ->
                    val key = param.substringBefore("=")
                    val value = URLDecoder.decode(param.substringAfter("="), "UTF-8")
                    key to value
                }
                val ch = map["ch"]?.toIntOrNull() ?: 1
                val callsign = map["callsign"] ?: "Rider"
                val ssid = map["ssid"]
                val pwd = map["pwd"]
                RideQrData(channel = ch, callsign = callsign, ssid = ssid, password = pwd)
            } catch (_: Exception) {
                null
            }
        }
    }
}

object RideQrManager {
    fun generateQrBitmap(data: RideQrData, sizePx: Int = 512): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(data.toUriString(), BarcodeFormat.QR_CODE, sizePx, sizePx)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
