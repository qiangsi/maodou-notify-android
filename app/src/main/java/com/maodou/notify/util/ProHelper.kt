package com.maodou.notify.util

import java.security.MessageDigest
import java.util.Base64

/**
 * v1.5.0 Pro 买断：离线激活码的签发与校验。
 *
 * 码格式：MD1-<payloadB32>-<sigB32>
 *   payload = [类型字节 W/P][8字节签发UTC毫秒][2字节随机]
 *   sig     = SHA256(SECRET + payload) 前 6 字节
 * 类型 W=周卡(签发后7天)，P=永久。
 *
 * 说明：本地买断的校验密钥必然随 APK 分发，逆向者理论上可绕过，
 * 这是所有离线买断方案的共同局限；对正常用户足够，且无需联网与账号。
 */
object ProHelper {

    private const val PREFIX = "MD1"
    // 与签发脚本 .publish/gen_pro_code.py 中的 SECRET 保持一致
    private const val SECRET = "maodou-notify-pro-2026-v15"

    private const val TYPE_WEEK = 'W'.code.toByte()
    private const val TYPE_FOREVER = 'P'.code.toByte()

    data class ProInfo(
        val valid: Boolean,
        val forever: Boolean,
        val expireAt: Long,
        val typeLabel: String
    )

    /** 校验激活码，返回解析结果。任何异常都视为无效。 */
    fun verify(code: String): ProInfo {
        try {
            val trimmed = code.trim().uppercase()
            val parts = trimmed.split("-")
            if (parts.size != 3 || parts[0] != PREFIX) return invalid()
            val payload = b32Decode(parts[1])
            val sig = b32Decode(parts[2])
            if (payload.size != 11) return invalid()
            if (!MessageDigest.isEqual(sig, signature(payload))) return invalid()

            val type = payload[0]
            val issuedAt = bytesToLong(payload, 1, 8)
            // 防止签发时间明显超前（留 2 天容差，兼容时区/设备时钟偏差）
            val now = System.currentTimeMillis()
            if (issuedAt > now + 2 * 86_400_000L) return invalid()
            return when (type) {
                TYPE_FOREVER -> ProInfo(true, true, Long.MAX_VALUE, "永久版")
                TYPE_WEEK -> ProInfo(true, false, issuedAt + 7L * 86_400_000, "周卡")
                else -> invalid()
            }
        } catch (e: Exception) {
            return invalid()
        }
    }

    /** 已激活状态下判断是否仍有效（周卡可能已过期） */
    fun isActive(prefs: Prefs): Boolean {
        if (!prefs.proActivated) return false
        if (prefs.proForever) return true
        return System.currentTimeMillis() < prefs.proExpireAt
    }

    private fun invalid() = ProInfo(false, false, 0L, "")

    private fun signature(payload: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(SECRET.toByteArray(Charsets.UTF_8))
        md.update(payload)
        return md.digest().copyOf(6)
    }

    private fun b32Encode(src: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(src)

    private fun b32Decode(src: String): ByteArray =
        Base64.getUrlDecoder().decode(src)

    private fun bytesToLong(src: ByteArray, offset: Int, len: Int): Long {
        var v = 0L
        for (i in offset until offset + len) {
            v = (v shl 8) or (src[i].toLong() and 0xFF)
        }
        return v
    }

    /** 供设置页展示当前状态文字 */
    fun statusText(prefs: Prefs): String = when {
        !prefs.proActivated -> "免费版"
        prefs.proForever -> "Pro · 永久版"
        isActive(prefs) -> {
            val days = (prefs.proExpireAt - System.currentTimeMillis()) / 86_400_000 + 1
            "Pro · 周卡（剩 $days 天）"
        }
        else -> "Pro 已到期"
    }
}
