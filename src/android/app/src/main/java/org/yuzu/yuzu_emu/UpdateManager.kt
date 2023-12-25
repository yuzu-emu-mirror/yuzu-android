package org.yuzu.yuzu_emu

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

interface VersionCheckCallback {
    fun onLatestVersionFetched(latestVersion: String)
    fun onError(errorMessage: String)
}

object UpdateManager {

    private val client = OkHttpClient()
    private var versionCheckCallback: VersionCheckCallback? = null

    fun checkAndInstallUpdate(context: Context, callback: VersionCheckCallback) {
        this.versionCheckCallback = callback

        GlobalScope.launch(Dispatchers.IO) {
            val currentVersionName =
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            val latestVersionName = getLatestVersionNameFromServer()

            withContext(Dispatchers.Main) {
                if (isNewVersionAvailable(currentVersionName, latestVersionName)) {
                    showUpdateDialog(context)
                    showUpdateAvailableMessage(context)
                } else {
                    showNoUpdateAvailableMessage(context)
                }
            }
        }
    }

    private suspend fun getLatestVersionNameFromServer() {
        val request = Request.Builder()
            .url("https://your-server.com/api/getLatestVersion")
            .build()

        try {
            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()

                    if (responseBody != null) {
                        val jsonObject = JSONObject(responseBody)
                        val latestVersion = jsonObject.getString("versionName")

                        // 调用回调函数，传递最新版本名称
                        versionCheckCallback?.onLatestVersionFetched(latestVersion)
                    } else {
                        // 处理没有响应体的情况
                        versionCheckCallback?.onError("Response body is empty")
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    Log.e("UpdateManager", "Error checking for updates: ${e.message}")

                    // 调用回调函数，传递错误消息
                    versionCheckCallback?.onError(e.message ?: "Unknown error")
                }
            })
        } catch (e: IOException) {
            Log.e("UpdateManager", "Error checking for updates: ${e.message}")

            // 调用回调函数，传递错误消息
            versionCheckCallback?.onError(e.message ?: "Unknown error")
        }
    }

    private fun isNewVersionAvailable(currentVersion: String, latestVersion: String): Boolean {
        return latestVersion.compareTo(currentVersion) > 0
    }

    private fun showUpdateDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("发现新版本")
            .setMessage("有新版本可用，是否立即更新？")
            .setPositiveButton("更新") { dialog, which ->
                // 处理更新操作
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
}
