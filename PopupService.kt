package com.qui.wordpopup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit

class PopupService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var popupView: View? = null
    private var popupWindowManager: WindowManager? = null
    private var wordsIndex = 0

    private val words = listOf(
        "earthy" to "có hương/vị tự nhiên, gợi mùi đất",
        "feathery" to "nhẹ như lông vũ",
        "varsity" to "đội tuyển thể thao chính của trường",
        "communion" to "sự hiệp thông; lễ ban Thánh Thể",
        "longitudinal" to "theo chiều dọc; theo thời gian",
        "doomed" to "bị định sẵn kết cục xấu/thất bại",
        "tenacious" to "kiên trì, bám dai",
        "prudent" to "thận trọng, khôn ngoan",
        "unsated" to "chưa được thỏa mãn"
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        handler.post(showTask)
    }

    private val showTask = object : Runnable {
        override fun run() {
            if (Settings.canDrawOverlays(this@PopupService)) {
                showNextWord()
            }
            val minutes = getSharedPreferences("settings", MODE_PRIVATE)
                .getLong("interval_min", 10L)
                .coerceIn(1, 1440)
            handler.postDelayed(this, TimeUnit.MINUTES.toMillis(minutes))
        }
    }

    private fun showNextWord() {
        removePopup()
        val (word, meaning) = words[wordsIndex % words.size]
        wordsIndex++

        val view = LayoutInflater.from(this).inflate(R.layout.popup_word, null)
        val title = view.findViewById<TextView>(R.id.wordText)
        val sub = view.findViewById<TextView>(R.id.meaningText)
        val close = view.findViewById<TextView>(R.id.closeButton)
        title.text = word
        sub.text = meaning
        close.setOnClickListener { removePopup() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 80
            horizontalMargin = 0.04f
        }

        popupWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        popupWindowManager?.addView(view, params)
        popupView = view

        handler.postDelayed({ removePopup() }, POPUP_DURATION_MS)
    }

    private fun removePopup() {
        popupView?.let { view ->
            try {
                popupWindowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        popupView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Word Popup service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Word Popup đang chạy")
        .setContentText("Đang nhắc từ vựng định kỳ")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removePopup()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "word_popup"
        private const val NOTIFICATION_ID = 1001
        private const val POPUP_DURATION_MS = 8_000L
    }
}
