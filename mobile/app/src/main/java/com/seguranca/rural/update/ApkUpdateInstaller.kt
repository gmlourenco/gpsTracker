package com.seguranca.rural.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.seguranca.rural.util.AppLog
import java.io.File

/**
 * Downloads an APK via [DownloadManager] and launches the system package installer.
 * SharedPreferences and app data are preserved (same package name + signing key).
 */
object ApkUpdateInstaller {

    fun startDownload(context: Context, downloadUrl: String, versionName: String) {
        val fileName = "seguranca-rural-$versionName.apk"
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("Segurança Rural")
            setDescription("A transferir versão $versionName…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                try {
                    ctx.unregisterReceiver(this)
                } catch (_: Exception) {
                }
                installDownloadedApk(ctx, dm, downloadId)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        Toast.makeText(context, "Transferência iniciada…", Toast.LENGTH_SHORT).show()
        AppLog.i("ApkUpdateInstaller", "Download enqueued: $downloadUrl")
    }

    private fun installDownloadedApk(context: Context, dm: DownloadManager, downloadId: Long) {
        val uri = dm.getUriForDownloadedFile(downloadId)
        if (uri == null) {
            Toast.makeText(context, "Falha na transferência do APK", Toast.LENGTH_LONG).show()
            return
        }

        val installUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = queryFilePath(dm, downloadId) ?: run {
                openInstallIntent(context, uri)
                return
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(path),
            )
        } else {
            uri
        }

        openInstallIntent(context, installUri)
    }

    private fun openInstallIntent(context: Context, apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun queryFilePath(dm: DownloadManager, downloadId: Long): String? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        dm.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val col = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (col < 0) return null
            val localUri = cursor.getString(col) ?: return null
            return Uri.parse(localUri).path
        }
    }
}
