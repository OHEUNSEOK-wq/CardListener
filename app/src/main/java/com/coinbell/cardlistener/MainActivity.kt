package com.coinbell.cardlistener

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        layout.addView(TextView(this).apply { text = "\uD83D\uDCB3 카드 알림 자동기록"; textSize = 24f; setPadding(0,0,0,32) })
        layout.addView(TextView(this).apply { text = "알림 접근 권한을 허용하면\n카드 결제 알림이 자동으로\n회계팀장에 기록됩니다."; textSize = 16f; setPadding(0,0,0,32) })
        layout.addView(Button(this).apply { text = "알림 접근 권한 설정"; setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } })
        setContentView(layout)
    }
}
