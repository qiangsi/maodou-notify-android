package com.maodou.notify.engine

import android.util.Log
import com.maodou.notify.data.NotificationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** Webhook 转发：这是产品唯一的数据外发通道，须由用户在规则中显式配置 URL。 */
object WebhookSender {

    fun send(url: String, title: String, text: String, entity: NotificationEntity): Boolean {
        if (url.isBlank()) return false
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")

            val json = JSONObject().apply {
                put("app", entity.packageName)
                put("appName", entity.appName)
                put("title", title)
                put("text", text)
                put("timestamp", entity.timestamp)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(json.toString()) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.w("MaodouWebhook", "转发失败: ${e.message}")
            false
        }
    }
}
