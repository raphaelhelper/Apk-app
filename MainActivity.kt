package com.qui.wordpopup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var folderText: TextView
    private lateinit var intervalEdit: EditText

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            prefs.edit()
                .putString("folder_uri", uri.toString())
                .putInt("next_index", 0)
                .apply()
            countWords(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        folderText = findViewById(R.id.folderText)
        intervalEdit = findViewById(R.id.intervalEdit)

        intervalEdit.setText(prefs.getLong("interval_min", 10L).toString())

        val saved = prefs.getString("folder_uri", null)
        if (saved != null) {
            countWords(Uri.parse(saved))
        } else {
            folderText.text = "Chưa chọn thư mục. Đang dùng 9 từ mặc định."
        }

        findViewById<Button>(R.id.overlayButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        findViewById<Button>(R.id.folderButton).setOnClickListener {
            pickFolder.launch(null)
        }

        findViewById<Button>(R.id.startButton).setOnClickListener {
            val minutes = intervalEdit.text.toString().toLongOrNull()?.coerceIn(1, 1440) ?: 10L
            prefs.edit().putLong("interval_min", minutes).apply()
            if (Settings.canDrawOverlays(this)) {
                val intent = Intent(this, PopupService::class.java)
                stopService(intent)
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

    private fun countWords(uri: Uri) {
        folderText.text = "Đang đọc thư mục..."
        Thread {
            val n = try {
                WordLoader.load(this, uri).size
            } catch (e: Exception) {
                -1
            }
            runOnUiThread {
                folderText.text = if (n > 0) {
                    "✅ Đã nạp $n từ. Bấm Bắt đầu để áp dụng."
                } else {
                    "⚠️ Không đọc được từ nào trong thư mục này."
                }
            }
        }.start()
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
