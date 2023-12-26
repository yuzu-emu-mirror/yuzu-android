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
                val title = jsonObject.optString("title", "发现新版本")
                val message = jsonObject.optString("message", "有新版本可用，是否立即更新？")
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
}

