package com.coinbell.cardlistener

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class CardNotificationListener : NotificationListenerService() {

    private val CARD_PACKAGES = setOf(
        "com.hanaskcard.rocomo", "com.hanacard.app",
        "com.lottemembers.android", "com.samsungcard.app",
        "com.kbcard.app", "com.shinhancard.smartshinhan"
    )

    private val SERVER_URL = "https://coinbell.cafe24.com/acct/api/auto-record"

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in CARD_PACKAGES) return
        val text = sbn.notification.extras.getString("android.text") ?: return
        val title = sbn.notification.extras.getString("android.title") ?: ""
        val parsed = parseNotification("$title $text") ?: return
        sendToServer(parsed)
    }

    data class CardTx(val amount: Int, val merchant: String, val paymentType: String,
                      val installmentMonths: Int, val date: String, val time: String, val cardLast4: String)

    private fun parseNotification(text: String): CardTx? {
        val amountMatch = Regex("([0-9,]+)\\s*원").find(text) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toIntOrNull() ?: return null

        var merchant = ""
        val mMatch = Regex("(?:가맹점|이용처)[:\\s]*([^\\n/]+)").find(text)
        merchant = mMatch?.groupValues?.get(1)?.trim()
            ?: text.substringBefore(amountMatch.value).split("\n", "/", " ").lastOrNull()?.trim() ?: ""

        val paymentType = if (text.contains("할부")) "할부" else "일시불"
        val installMonths = Regex("(\\d+)\\s*개월").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val cardLast4 = Regex("(?:본인|카드)\\s*(\\d{4})").find(text)?.groupValues?.get(1) ?: ""

        val dMatch = Regex("(\\d{4})[./](\\d{2})[./](\\d{2})\\s*(\\d{2}):(\\d{2})").find(text)
        val now = Date()
        val date = dMatch?.let { "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(now)
        val time = dMatch?.let { "${it.groupValues[4]}:${it.groupValues[5]}" }
            ?: SimpleDateFormat("HH:mm", Locale.KOREA).format(now)

        if (merchant.isEmpty()) return null
        return CardTx(amount, merchant, paymentType, installMonths, date, time, cardLast4)
    }

    private fun sendToServer(tx: CardTx) {
        Thread {
            try {
                val conn = URL(SERVER_URL).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val json = JSONObject().apply {
                    put("amount", tx.amount); put("merchant", tx.merchant)
                    put("payment_type", tx.paymentType); put("installment_months", tx.installmentMonths)
                    put("date", tx.date); put("time", tx.time); put("card_last4", tx.cardLast4)
                }
                conn.outputStream.write(json.toString().toByteArray())
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }.start()
    }
}
