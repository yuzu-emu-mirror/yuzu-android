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

object UpdateManager {

    private val client = OkHttpClient()

    fun checkAndInstallUpdate(context: Context) {
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
