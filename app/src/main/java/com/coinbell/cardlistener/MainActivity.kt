package com.coinbell.cardlistener

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var statusDot: TextView
    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        layout.addView(TextView(this).apply {
            text = "\uD83D\uDCB3 카드 알림 자동기록"
            textSize = 24f
            setPadding(0, 0, 0, 24)
        })

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 24)
        }

        statusDot = TextView(this).apply {
            textSize = 20f
            setPadding(0, 0, 16, 0)
        }

        statusText = TextView(this).apply {
            textSize = 18f
        }

        statusRow.addView(statusDot)
        statusRow.addView(statusText)
        layout.addView(statusRow)

        layout.addView(TextView(this).apply {
            text = "알림 접근 권한을 허용하면\n카드 결제 알림이 자동으로\n회계팀장에 기록됩니다."
            textSize = 15f
            setPadding(0, 0, 0, 32)
            setTextColor(Color.parseColor("#666666"))
        })

        layout.addView(Button(this).apply {
            text = "알림 접근 권한 설정"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        })

        setContentView(layout)
        startStatusCheck()
    }

    private fun isListenerEnabled(): Boolean {
        val cn = ComponentName(this, CardNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return flat.contains(cn.flattenToString())
    }

    private fun startStatusCheck() {
        handler.post(object : Runnable {
            override fun run() {
                val enabled = isListenerEnabled()
                statusDot.text = if (enabled) "\uD83D\uDFE2" else "\uD83D\uDD34"
                statusText.text = if (enabled) "실행 중" else "중단"
                statusText.setTextColor(if (enabled) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
                handler.postDelayed(this, 1000)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
