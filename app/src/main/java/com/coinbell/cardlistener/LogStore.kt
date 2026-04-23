package com.coinbell.cardlistener

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 알림 수신 진단용 인메모리 로그 스토어 (싱글톤).
 * - 앱 재시작 시 초기화됨 (디버깅 용도라 영구 저장 불필요)
 * - 최대 50건 유지, 초과 시 오래된 것부터 드롭
 * - 스레드 안전 (CopyOnWriteArrayList + synchronized append)
 */
object LogStore {
    private const val MAX = 50
    private val entries = CopyOnWriteArrayList<LogEntry>()

    enum class Kind { MATCH, SKIP, PARSE_FAIL, POST_OK, POST_FAIL }

    data class LogEntry(
        val ts: String,
        val kind: Kind,
        val pkg: String,
        val title: String,
        val body: String,
        val extra: String = ""
    )

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

    @Synchronized
    fun add(kind: Kind, pkg: String, title: String, body: String, extra: String = "") {
        val e = LogEntry(timeFmt.format(Date()), kind, pkg, title, body, extra)
        entries.add(0, e)
        while (entries.size > MAX) entries.removeAt(entries.size - 1)
    }

    fun snapshot(): List<LogEntry> = entries.toList()

    @Synchronized
    fun clear() { entries.clear() }

    fun render(e: LogEntry): String {
        val icon = when (e.kind) {
            Kind.MATCH -> "✅ MATCH"
            Kind.SKIP -> "⏭ SKIP"
            Kind.PARSE_FAIL -> "❌ PARSE FAIL"
            Kind.POST_OK -> "📤 POST OK"
            Kind.POST_FAIL -> "🔥 POST FAIL"
        }
        val sb = StringBuilder()
        sb.append("[").append(e.ts).append("] ").append(icon).append("\n")
        sb.append("pkg=").append(e.pkg).append("\n")
        if (e.title.isNotEmpty()) sb.append("title=").append(e.title).append("\n")
        if (e.body.isNotEmpty()) sb.append("body=").append(truncate(e.body, 180)).append("\n")
        if (e.extra.isNotEmpty()) sb.append(e.extra).append("\n")
        return sb.toString()
    }

    private fun truncate(s: String, n: Int): String =
        if (s.length <= n) s else s.substring(0, n) + "…"
}
