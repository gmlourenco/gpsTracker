package com.segurancarural.gpstracker.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object OfflineLogger {
    private var logFile: File? = null
    private val executor = Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        logFile = File(dir, "gps_tracker_logs.txt")
        // Rotate log if too big (> 5MB)
        logFile?.let {
            if (it.exists() && it.length() > 5 * 1024 * 1024) {
                val oldFile = File(dir, "gps_tracker_logs_old.txt")
                oldFile.delete()
                it.renameTo(oldFile)
            }
        }
    }

    fun log(level: String, tag: String, message: String) {
        val file = logFile ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val line = "$timestamp [$level] $tag: $message\n"
        
        executor.execute {
            try {
                FileOutputStream(file, true).use {
                    it.write(line.toByteArray())
                }
            } catch (e: IOException) {
                // Cannot log the logger failing
            }
        }
    }
}
