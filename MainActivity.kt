package com.qui.wordpopup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var intervalEdit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        intervalEdit = findViewById(R.id.intervalEdit)

        findViewById<Button>(R.id.overlayButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        findViewById<Button>(R.id.startButton).setOnClickListener {
            val minutes = intervalEdit.text.toString().toLongOrNull()?.coerceIn(1, 1440) ?: 10L
            getSharedPreferences("settings", MODE_PRIVATE).edit().putLong("interval_min", minutes).apply()
            if (Settings.canDrawOverlays(this)) {
                val intent = Intent(this, PopupService::class.java)
                ContextCompat.startForegroundService(this, intent)
                statusText.text = "Đã chạy: popup mỗi $minutes phút."
            } else {
                statusText.text = "Bro chưa cấp quyền popup nổi."
            }
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, PopupService::class.java))
            statusText.text = "Đã dừng."
        }
    }

    override fun onResume() {
        super.onResume()
        statusText.text = if (Settings.canDrawOverlays(this)) {
            "✅ Đã có quyền popup nổi."
        } else {
            "⚠️ Chưa có quyền popup nổi."
        }
    }
}
