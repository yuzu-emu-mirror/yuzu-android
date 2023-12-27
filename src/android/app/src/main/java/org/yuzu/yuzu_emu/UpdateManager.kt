package org.yuzu.yuzu_emu

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {

    private companion object {
        private const val CONFIG_URL = "https://your-server.com/api/config"
    }

    fun checkForUpdates() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val configJson = fetchConfigFromServer(CONFIG_URL)
                if (configJson != null) {
                    val jsonObject = JSONObject(configJson)
                    val updateDialogTitle = jsonObject.optString("updateDialogTitle")
                    val updateDialogMessage = jsonObject.optString("updateDialogMessage")
                    val versionName = jsonObject.optString("versionName")
                    val downloadUrl = jsonObject.optString("downloadUrl")

                    if (isNewVersionAvailable(versionName)) {
                        showUpdateDialog(updateDialogTitle, updateDialogMessage, downloadUrl)
                    } else {
                        showNoUpdateAvailableMessage()
                    }
                } else {
                    showErrorToast()
                }
            } catch (e: Exception) {
                showErrorToast()
            }
        }
    }

    private fun fetchConfigFromServer(urlString: String): String? {
        var connection: HttpURLConnection? = null
        var reader: BufferedReader? = null

        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                reader = BufferedReader(InputStreamReader(connection.inputStream))
                val stringBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line).append("\n")
                }
                stringBuilder.toString()
            } else {
                null
            }
        } finally {
            reader?.close()
            connection?.disconnect()
        }
    }

    private fun isNewVersionAvailable(serverVersionName: String): Boolean {
        try {
            val currentVersionName = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName
            return serverVersionName.compareTo(currentVersionName) > 0
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
    }

    private fun showUpdateDialog(title: String, message: String, downloadUrl: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("更新") { dialog, which ->
                downloadLatestVersion(downloadUrl)
            }
            .setNegativeButton("稍后", null)
            .show()
    }
    
    private fun showNoUpdateAvailableMessage() {
        Toast.makeText(context, "您的应用已经是最新版本。", Toast.LENGTH_SHORT).show()
    }

    private fun showErrorToast() {
        Toast.makeText(context, "无法获取配置信息", Toast.LENGTH_SHORT).show()
    }

    private fun downloadLatestVersion(downloadUrl: String) {
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("应用更新")
            .setDescription("正在下载新版本")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "app-update.apk"
            )

        val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
    }

    fun installLatestVersion(downloadId: Long) {
        val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val query = DownloadManager.Query().apply {
            setFilterById(downloadId)
        }

        val cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val uri = Uri.parse(
                    cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            } else if (status == DownloadManager.STATUS_FAILED) {
                showErrorToast()
            }
        }
    }
}
