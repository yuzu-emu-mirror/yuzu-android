package org.yuzu.yuzu_emu

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

object UpdateManager {
    private val client = OkHttpClient()

    fun checkAndInstallUpdate(context: Context) {
        GlobalScope.launch(Dispatchers.IO) {
            val versionInfo = getLatestVersionInfoFromServer()

            withContext(Dispatchers.Main) {
                if (versionInfo != null) {
                    val currentVersionName = context.packageManager
                        .getPackageInfo(context.packageName, 0).versionName
                    if (isNewVersionAvailable(currentVersionName, versionInfo.versionName)) {
                        showUpdateDialog(context, versionInfo.title, versionInfo.message)
                    } else {
                        showNoUpdateAvailableMessage(context)
                    }
                } else {
                    // 处理无法获取版本信息的情况
                }
            }
        }
    }

    private suspend fun getLatestVersionInfoFromServer(): VersionInfo? {
        val request = Request.Builder()
            .url("https://your-server.com/api/getLatestVersionInfo")
            .build()

        return try {
            val response: Response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (responseBody != null) {
                val jsonObject = JSONObject(responseBody)
                val versionName = jsonObject.getString("versionName")
                val title = jsonObject.optString("title", "")
                val message = jsonObject.optString("message", "")
                VersionInfo(versionName, title, message)
            } else {
                null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private data class VersionInfo(val versionName: String, val title: String, val message: String)

    private fun isNewVersionAvailable(currentVersion: String, latestVersion: String): Boolean {
        return latestVersion.compareTo(currentVersion) > 0
    }

    private fun showUpdateDialog(context: Context, title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("更新") { dialog, which ->
                // 下载并安装更新
                downloadAndInstallUpdate(context)
            }
            .setNegativeButton("稍后") { dialog, which ->
                // 处理稍后更新操作
            }
            .show()
    }

    private fun showNoUpdateAvailableMessage(context: Context) {
        Toast.makeText(context, "您的应用已经是最新版本。", Toast.LENGTH_SHORT).show()
    }

    private fun downloadAndInstallUpdate(context: Context) {
        val downloadUrl = "https://your-server.com/api/downloadLatestVersion" // 替换为实际的下载链接

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
        request.setTitle("App更新")
        request.setDescription("正在下载新版本...")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            "yuzu.apk"
        )

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        // 您可以存储下载ID并用于检查下载状态或处理重试

        // 当下载完成时安装下载的APK
        val onCompleteReceiver = DownloadCompleteReceiver()
        onCompleteReceiver.setDownloadId(downloadId)
        context.registerReceiver(
            onCompleteReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }
}

class DownloadCompleteReceiver : BroadcastReceiver() {
    private var downloadId: Long = -1

    fun setDownloadId(id: Long) {
        downloadId = id
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            return
        }

    if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val uri = Uri.parse(cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)))
                    installApk(context, uri)
                } else {
                }
            }

            cursor.close()
        }
    }

    private fun installApk(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
