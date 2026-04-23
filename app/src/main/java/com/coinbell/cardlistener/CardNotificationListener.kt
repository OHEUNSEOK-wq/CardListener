package com.coinbell.cardlistener

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class CardNotificationListener : NotificationListenerService() {

    companion object { private const val TAG = "CardListener" }

    // 감시 대상 패키지
    // - 카드사 네이티브: 기존
    // - 간편결제: viva.republica.toss(토스), com.samsung.android.spay(삼성월렛)
    // - 패키지명 확신 없으면 여러 후보 포함. 알림 수신 시 Log로 실제 패키지명 기록해서 정리 가능.
    private val CARD_PACKAGES = setOf(
        // 카드사 네이티브 앱
        "com.hanaskcard.rocomo", "com.hanacard.app",
        "com.lottemembers.android", "com.samsungcard.app",
        "com.kbcard.app", "com.shinhancard.smartshinhan",
        // 간편결제 앱
        "viva.republica.toss",              // 토스 (토스뱅크 카드 알림 포함)
        "com.toss.tossbank",                // 토스뱅크 (분리 앱이 있을 경우 대비)
        "com.samsung.android.spay",         // Samsung Wallet (구 Samsung Pay)
        "com.samsung.android.spaylite"      // 샘플링용 대비
    )

    private val SERVER_URL = "https://coinbell.cafe24.com/acct/api/auto-record"

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""
        val bigText = extras.getString("android.bigText") ?: ""
        val body = if (bigText.isNotBlank()) bigText else text

        // ALL 알림을 디버그 스토어에 기록 (필터링 전)
        if (pkg !in CARD_PACKAGES) {
            LogStore.add(LogStore.Kind.SKIP, pkg, title, body, "(not in CARD_PACKAGES)")
            return
        }
        Log.d(TAG, "recv pkg=$pkg title='$title' body='$body'")
        val parsed = parseNotification(pkg, title, body)
        if (parsed == null) {
            Log.w(TAG, "parse failed pkg=$pkg title='$title' body='$body'")
            LogStore.add(LogStore.Kind.PARSE_FAIL, pkg, title, body, "(정규식 매칭 실패 — 실제값 보고 정규식 조정 필요)")
            return
        }
        Log.d(TAG, "parsed pkg=$pkg amount=${parsed.amount} merchant='${parsed.merchant}' card_last4='${parsed.cardLast4}' card_name='${parsed.cardName}'")
        LogStore.add(LogStore.Kind.MATCH, pkg, title, body,
            "→ parsed: amount=${parsed.amount} merchant='${parsed.merchant}' card_last4='${parsed.cardLast4}' card_name='${parsed.cardName}'")
        sendToServer(parsed, pkg, title, body)
    }

    data class CardTx(
        val amount: Int, val merchant: String, val paymentType: String,
        val installmentMonths: Int, val date: String, val time: String,
        val cardLast4: String, val cardName: String
    )

    private fun parseNotification(pkg: String, title: String, body: String): CardTx? {
        val full = "$title $body"

        // 금액 추출 (공통)
        val amountMatch = Regex("([0-9,]+)\\s*원").find(full) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toIntOrNull() ?: return null

        // 날짜/시간 (알림에 명시되면 우선, 없으면 now)
        val dMatch = Regex("(\\d{4})[./-](\\d{2})[./-](\\d{2})\\s*(\\d{2}):(\\d{2})").find(full)
        val now = Date()
        val date = dMatch?.let { "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(now)
        val time = dMatch?.let { "${it.groupValues[4]}:${it.groupValues[5]}" }
            ?: SimpleDateFormat("HH:mm", Locale.KOREA).format(now)

        // 할부/일시불
        val paymentType = if (full.contains("할부")) "할부" else "일시불"
        val installMonths = Regex("(\\d+)\\s*개월").find(full)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        // 간편결제 앱 (토스, 삼성월렛)
        if (pkg.startsWith("viva.republica.toss") || pkg == "com.toss.tossbank") {
            // 토스/토스뱅크 포맷:
            //   title: "토스뱅크 하나카드 Wide"
            //   body:  "2,400원 결제 | 비지에프리테일(주)씨유양재알뜰점"
            val cardName = title.trim()
            val merchant = body.substringAfter("|").trim().ifEmpty {
                // "| " 없는 변형: "2,400원 결제 비지에프리테일..." 형태 대비
                val afterAmount = body.substringAfter(amountMatch.value).trim()
                afterAmount.removePrefix("결제").trim().removePrefix("승인").trim()
            }
            if (merchant.isEmpty()) return null
            return CardTx(amount, merchant, paymentType, installMonths, date, time, "", cardName)
        }
        if (pkg.startsWith("com.samsung.android.spay")) {
            // Samsung Wallet 포맷 (대표 실물 샘플 확보 후 정확히 조정. 현재 보수적 추정)
            //   title: "삼성 iD ONE 카드" 또는 "Samsung Wallet"
            //   body:  "5,000원 / 스타벅스 강남점" 또는 유사
            val cardName = title.trim()
            val merchant = Regex("(?:사용|결제|승인)[^\\n]*?[|/:]\\s*([^\\n]+)").find(full)?.groupValues?.get(1)?.trim()
                ?: body.substringAfter(amountMatch.value).split("|", "/", "\n").lastOrNull()?.trim()
                ?: ""
            if (merchant.isEmpty()) return null
            return CardTx(amount, merchant, paymentType, installMonths, date, time, "", cardName)
        }

        // 카드사 네이티브 앱 (기존 로직 + card_name=title)
        val merchantCandidate = Regex("(?:가맹점|이용처)[:\\s]*([^\\n/]+)").find(full)?.groupValues?.get(1)?.trim()
            ?: full.substringBefore(amountMatch.value).split("\n", "/", " ").lastOrNull()?.trim()
            ?: ""
        val cardLast4 = Regex("(?:본인|카드)\\s*(\\d{4})").find(full)?.groupValues?.get(1) ?: ""
        val cardName = title.trim()  // 카드사 앱의 알림 제목 (예: "하나카드", "삼성카드")
        if (merchantCandidate.isEmpty()) return null
        return CardTx(amount, merchantCandidate, paymentType, installMonths, date, time, cardLast4, cardName)
    }

    private fun sendToServer(tx: CardTx, ctxPkg: String = "", ctxTitle: String = "", ctxBody: String = "") {
        Thread {
            try {
                val conn = URL(SERVER_URL).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val json = JSONObject().apply {
                    put("amount", tx.amount)
                    put("merchant", tx.merchant)
                    put("payment_type", tx.paymentType)
                    put("installment_months", tx.installmentMonths)
                    put("date", tx.date)
                    put("time", tx.time)
                    if (tx.cardLast4.isNotEmpty()) put("card_last4", tx.cardLast4)
                    if (tx.cardName.isNotEmpty()) put("card_name", tx.cardName)
                }
                val payload = json.toString()
                conn.outputStream.write(payload.toByteArray())
                val code = conn.responseCode
                val resp = try { conn.inputStream.bufferedReader().use { it.readText() } }
                           catch (_: Exception) { conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "" }
                Log.d(TAG, "POST $SERVER_URL code=$code resp='$resp' payload=$payload")
                LogStore.add(LogStore.Kind.POST_OK, ctxPkg, ctxTitle, ctxBody,
                    "→ POST code=$code resp=${resp.take(200)}")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "POST fail: ${e.message}")
                LogStore.add(LogStore.Kind.POST_FAIL, ctxPkg, ctxTitle, ctxBody,
                    "→ POST exception: ${e.javaClass.simpleName}: ${e.message}")
            }
        }.start()
    }
}
