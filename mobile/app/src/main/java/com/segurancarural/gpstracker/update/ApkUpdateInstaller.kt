package com.segurancarural.gpstracker.update

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
import com.segurancarural.gpstracker.util.AppLog
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
        AppLog.i("ApkUpdateInstaller", "installDownloadedApk called for downloadId: $downloadId")
        val uri = dm.getUriForDownloadedFile(downloadId)
        if (uri == null) {
            AppLog.e("ApkUpdateInstaller", "Downloaded APK URI is null")
            Toast.makeText(context, "Falha na transferência do APK", Toast.LENGTH_LONG).show()
            return
        }

        val installUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val tempFile = File(context.cacheDir, "update.apk")
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                AppLog.i("ApkUpdateInstaller", "Copying downloaded APK to cache...")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                AppLog.i("ApkUpdateInstaller", "APK copied successfully to: ${tempFile.absolutePath}")

                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile,
                )
            } catch (e: Exception) {
                AppLog.e("ApkUpdateInstaller", "Failed to copy or prepare APK for installation: ${e.message}", e)
                Toast.makeText(context, "Falha ao preparar o instalador", Toast.LENGTH_LONG).show()
                return
            }
        } else {
            uri
        }

        AppLog.i("ApkUpdateInstaller", "Opening install intent with URI: $installUri")
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
}
