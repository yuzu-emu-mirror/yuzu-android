package org.yuzu.yuzu_emu

import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val downloadUrl = "http://mkoc.cn/app/yuzu163.apk"
        UpdateManager.checkAndInstallUpdate(this, downloadUrl)
    }
}

object UpdateManager {

    private val client = OkHttpClient()

    fun checkAndInstallUpdate(context: Context, downloadUrl: String) {
        GlobalScope.launch(Dispatchers.IO) {
            val currentVersionName = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName
            val latestVersionName = getLatestVersionNameFromServer()

            withContext(Dispatchers.Main) {
                if (isNewVersionAvailable(currentVersionName, latestVersionName)) {
                    showUpdateDialog(context, downloadUrl)
                    showUpdateAvailableMessage(context)
                } else {
                    showNoUpdateAvailableMessage(context)
                }
            }
        }
    }

    private suspend fun getLatestVersionNameFromServer(): String {
        val request = Request.Builder()
            .url("http://mkoc.cn/aip/version.php")
            .build()

        return try {
            val response: Response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (responseBody != null) {
                val jsonObject = JSONObject(responseBody)
                jsonObject.getString("versionName")
            } else {
                ""
            }
        } catch (e: IOException) {
            Log.e("UpdateManager", "Error checking for updates: ${e.message}")
            ""
        }
    }

    private fun isNewVersionAvailable(currentVersion: String, latestVersion: String): Boolean {
        return latestVersion.compareTo(currentVersion) > 0
    }

    private fun showUpdateDialog(context: Context, downloadUrl: String) {
        AlertDialog.Builder(context)
            .setTitle("发现新版本")
            .setMessage("有新版本可用，是否立即更新？")
            .setPositiveButton("更新") { dialog, which ->
                // 开始下载新版本
                downloadLatestVersion(context, downloadUrl)
            }
            .setNegativeButton("稍后") { dialog, which ->
                // 稍后处理更新操作
            }
            .show()
    }

    private fun showUpdateAvailableMessage(context: Context) {
        Toast.makeText(context, "发现新版本，请及时更新。", Toast.LENGTH_LONG).show()
    }

    private fun showNoUpdateAvailableMessage(context: Context) {
        Toast.makeText(context, "您的应用已经是最新版本。", Toast.LENGTH_SHORT).show()
    }

    private fun downloadLatestVersion(context: Context, downloadUrl: String) {
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

        val onCompleteReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                installLatestVersion(context)
                context?.unregisterReceiver(this)
            }
        }

        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        context.registerReceiver(onCompleteReceiver, intentFilter)

        val downloadId = downloadManager.enqueue(request)
    }

    private fun installLatestVersion(context: Context?) {
        val downloadManager =
            context?.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().apply {
            setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL)
        }

        val cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            val apkUri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                File(cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)))
            )

            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
        }
    }
}
