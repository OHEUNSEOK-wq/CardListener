package com.coinbell.cardlistener

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager

class MainActivity : Activity() {
    private lateinit var statusDot: TextView
    private lateinit var statusText: TextView
    private lateinit var logContainer: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateStatus()
            renderLogs()
            handler.postDelayed(this, 2000)   // 2초마다 자동 갱신
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }
        root.addView(layout)

        // 타이틀
        layout.addView(TextView(this).apply {
            text = "💳 카드 알림 자동기록"
            textSize = 22f
            setPadding(0, 0, 0, 16)
        })

        // 상태 행
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }
        statusDot = TextView(this).apply { textSize = 18f; setPadding(0, 0, 12, 0) }
        statusText = TextView(this).apply { textSize = 16f }
        statusRow.addView(statusDot)
        statusRow.addView(statusText)
        layout.addView(statusRow)

        // 설명
        layout.addView(TextView(this).apply {
            text = "서버: https://coinbell.cafe24.com/acct/api/auto-record"
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 16)
        })

        // 버튼 행 (알림 접근 설정)
        layout.addView(Button(this).apply {
            text = "알림 접근 권한 설정"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        })

        // 구분선
        layout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#DDDDDD"))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            p.topMargin = 24; p.bottomMargin = 16
            layoutParams = p
        })

        // 로그 헤더
        val logHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }
        logHeader.addView(TextView(this).apply {
            text = "최근 수신 알림"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        logHeader.addView(Button(this).apply {
            text = "새로고침"
            setOnClickListener { renderLogs() }
        })
        logHeader.addView(Button(this).apply {
            text = "지우기"
            setOnClickListener {
                LogStore.clear()
                renderLogs()
                Toast.makeText(this@MainActivity, "로그 초기화됨", Toast.LENGTH_SHORT).show()
            }
        })
        layout.addView(logHeader)

        // 로그 컨테이너
        logContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(logContainer)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun isListenerEnabled(): Boolean {
        val cn = ComponentName(this, CardNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return flat.contains(cn.flattenToString())
    }

    private fun updateStatus() {
        val enabled = isListenerEnabled()
        statusDot.text = if (enabled) "🟢" else "🔴"
        statusText.text = if (enabled) "실행 중 · 알림 접근 권한 허용됨" else "중단 · 알림 접근 권한 필요"
        statusText.setTextColor(if (enabled) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
    }

    private fun renderLogs() {
        logContainer.removeAllViews()
        val logs = LogStore.snapshot()
        if (logs.isEmpty()) {
            logContainer.addView(TextView(this).apply {
                text = "(아직 수신된 알림 없음)"
                textSize = 13f
                setTextColor(Color.parseColor("#AAAAAA"))
                setPadding(0, 16, 0, 0)
            })
            return
        }
        for (e in logs) {
            val card = TextView(this).apply {
                text = LogStore.render(e)
                textSize = 12f
                setTextColor(Color.parseColor("#333333"))
                setPadding(20, 16, 20, 16)
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                p.bottomMargin = 10
                layoutParams = p
                val bgColor = when (e.kind) {
                    LogStore.Kind.MATCH, LogStore.Kind.POST_OK -> "#E8F5E9"
                    LogStore.Kind.SKIP -> "#F5F5F5"
                    LogStore.Kind.PARSE_FAIL -> "#FFF3E0"
                    LogStore.Kind.POST_FAIL -> "#FFEBEE"
                }
                setBackgroundColor(Color.parseColor(bgColor))
                setOnLongClickListener {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("cardlog", text.toString()))
                    Toast.makeText(this@MainActivity, "복사됨", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            logContainer.addView(card)
        }
    }
}
