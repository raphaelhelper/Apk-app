package com.qui.wordpopup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import java.util.concurrent.TimeUnit

data class Word(
    val word: String,
    val meaning: String,
    val example: String,
    val translation: String
)

object WordLoader {
    private val numberLine = Regex("^\\d+\\s*[.)]$")
    private val exampleRe = Regex("^example\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val dichRe = Regex("^d[iị]ch\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val chunkRe = Regex("\\d+|\\D+")

    fun load(context: Context, treeUri: Uri): List<Word> {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val files = dir.listFiles()
            .mapNotNull { f ->
                val n = f.name
                if (n != null && n.endsWith(".txt", ignoreCase = true)) Pair(f, n) else null
            }
            .sortedWith(Comparator { a, b -> naturalCompare(a.second, b.second) })

        val result = ArrayList<Word>()
        for ((file, _) in files) {
            try {
                val text = context.contentResolver.openInputStream(file.uri)?.use {
                    String(it.readBytes(), Charsets.UTF_8)
                } ?: continue
                result.addAll(parse(text))
            } catch (_: Exception) {
            }
        }
        return result
    }

    fun parse(text: String): List<Word> {
        val out = ArrayList<Word>()
        val block = ArrayList<String>()

        fun flush() {
            if (block.isNotEmpty()) {
                var example = ""
                var translation = ""
                val plain = ArrayList<String>()
                for (l in block) {
                    val e = exampleRe.find(l)
                    val d = dichRe.find(l)
                    when {
                        e != null -> example = e.groupValues[1].trim()
                        d != null -> translation = d.groupValues[1].trim()
                        else -> plain.add(l)
                    }
                }
                if (plain.isNotEmpty()) {
                    out.add(
                        Word(
                            plain[0],
                            plain.drop(1).joinToString(" "),
                            example,
                            translation
                        )
                    )
                }
            }
            block.clear()
        }

        for (raw in text.replace("\uFEFF", "").lines()) {
            val line = raw.trim()
            if (numberLine.matches(line)) {
                flush()
            } else if (line.isNotEmpty()) {
                block.add(line)
            }
        }
        flush()
        return out
    }

    private fun naturalCompare(a: String, b: String): Int {
        val ra = chunkRe.findAll(a.lowercase()).map { it.value }.toList()
        val rb = chunkRe.findAll(b.lowercase()).map { it.value }.toList()
        var i = 0
        while (i < ra.size && i < rb.size) {
            val x = ra[i]
            val y = rb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) {
                x.toBigInteger().compareTo(y.toBigInteger())
            } else {
                x.compareTo(y)
            }
            if (c != 0) return c
            i++
        }
        return ra.size - rb.size
    }
}

class PopupService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var popupView: View? = null
    private var popupWindowManager: WindowManager? = null
    private var words: List<Word> = DEFAULT_WORDS
    private var wordsIndex = 0

    @Volatile
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val folder = prefs.getString("folder_uri", null)

        Thread {
            val loaded: List<Word> = if (folder != null) {
                try {
                    WordLoader.load(this, Uri.parse(folder))
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            handler.post {
                if (destroyed) return@post
                words = if (loaded.isEmpty()) DEFAULT_WORDS else loaded
                wordsIndex = prefs.getInt("next_index", 0).coerceAtLeast(0) % words.size
                handler.post(showTask)
            }
        }.start()
    }

    private val showTask = object : Runnable {
        override fun run() {
            // Nếu popup cũ chưa đóng thì bỏ qua lượt này, không thay từ.
            if (Settings.canDrawOverlays(this@PopupService) && popupView == null) {
                showNextWord()
            }
            val minutes = getSharedPreferences("settings", MODE_PRIVATE)
                .getLong("interval_min", 10L)
                .coerceIn(1, 1440)
            handler.postDelayed(this, TimeUnit.MINUTES.toMillis(minutes))
        }
    }

    private fun showNextWord() {
        if (words.isEmpty()) return
        val item = words[wordsIndex % words.size]
        wordsIndex = (wordsIndex + 1) % words.size
        getSharedPreferences("settings", MODE_PRIVATE)
            .edit().putInt("next_index", wordsIndex).apply()

        val view = LayoutInflater.from(this).inflate(R.layout.popup_word, null)
        view.findViewById<TextView>(R.id.wordText).text = item.word
        view.findViewById<TextView>(R.id.meaningText).text = item.meaning
        view.findViewById<TextView>(R.id.closeButton).setOnClickListener { removePopup() }

        val exampleButton = view.findViewById<TextView>(R.id.exampleButton)
        val exampleBox = view.findViewById<View>(R.id.exampleBox)
        val exampleText = view.findViewById<TextView>(R.id.exampleText)
        val translationText = view.findViewById<TextView>(R.id.translationText)

        if (item.example.isEmpty() && item.translation.isEmpty()) {
            exampleButton.visibility = View.GONE
        } else {
            exampleText.text = item.example
            translationText.text = item.translation
            exampleText.visibility = if (item.example.isEmpty()) View.GONE else View.VISIBLE
            translationText.visibility = if (item.translation.isEmpty()) View.GONE else View.VISIBLE
            exampleButton.setOnClickListener {
                val show = exampleBox.visibility != View.VISIBLE
                exampleBox.visibility = if (show) View.VISIBLE else View.GONE
                exampleButton.text = if (show) "Ẩn ví dụ ▴" else "Ví dụ ▾"
            }
        }

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

        try {
            popupWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            popupWindowManager?.addView(view, params)
            popupView = view
        } catch (_: Exception) {
            popupView = null
        }
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
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        removePopup()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "word_popup"
        private const val NOTIFICATION_ID = 1001

        private val DEFAULT_WORDS = listOf(
            Word("earthy", "có hương/vị tự nhiên, gợi mùi đất", "", ""),
            Word("feathery", "nhẹ như lông vũ", "", ""),
            Word("varsity", "đội tuyển thể thao chính của trường", "", ""),
            Word("communion", "sự hiệp thông; lễ ban Thánh Thể", "", ""),
            Word("longitudinal", "theo chiều dọc; theo thời gian", "", ""),
            Word("doomed", "bị định sẵn kết cục xấu/thất bại", "", ""),
            Word("tenacious", "kiên trì, bám dai", "", ""),
            Word("prudent", "thận trọng, khôn ngoan", "", ""),
            Word("unsated", "chưa được thỏa mãn", "", "")
        )
    }
}
