package org.yuzu.yuzu_emu

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.*

object UpdateManager {

    private val client = OkHttpClient()

    fun checkAndInstallUpdate(context: Context) {
        GlobalScope.launch(Dispatchers.IO) {
            val currentVersion = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName
            val latestVersion = getLatestVersionFromServer()

            withContext(Dispatchers.Main) {
                if (isUpdateAvailable(currentVersion, latestVersion)) {
                    showUpdateDialog(context)
                } else {
                    showNoUpdateMessage(context)
                }
            }
        }
    }

    private suspend fun getLatestVersionFromServer(): String {
        val request = Request.Builder()
            .url("http://mkoc.cn/aip/version.php")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            JSONObject(responseBody).getString("versionName")
        } catch (e: IOException) {
            Log.e("UpdateManager", "检查更新时出错: ${e.message}")
            ""
        }
    }

    private fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        return latestVersion > currentVersion
    }

    private fun showUpdateDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("有新版本可用")
            .setMessage("有新版本可用。现在更新吗？")
            .setPositiveButton("更新") { dialog, which ->
                downloadAndInstallUpdate(context)
            }
            .setNegativeButton("稍后") { dialog, which ->
                // 处理稍后更新的逻辑
            }
            .show()
    }

    private fun showNoUpdateMessage(context: Context) {
        Toast.makeText(context, "您的应用已经是最新版本。", Toast.LENGTH_SHORT).show()
    }

    private fun downloadAndInstallUpdate(context: Context) {
        val downloadUrl = "http://mkoc.cn/app/yuzu.apk"
        val request = Request.Builder()
            .url(downloadUrl)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("UpdateManager", "下载失败: ${e.message}")
                // 处理下载失败
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val apkFile = File(context.getExternalFilesDir(null), "update.apk")
                    val inputStream = response.body?.byteStream()

                    try {
                        inputStream?.use { input ->
                            apkFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        installUpdate(context, apkFile)
                    } catch (e: IOException) {
                        Log.e("UpdateManager", "复制文件时出错: ${e.message}")
                        // 处理文件复制错误
                    }
                } else {
                    Log.e("UpdateManager", "下载失败: HTTP ${response.code()}")
                    // 处理下载失败
                }
            }
        })
    }

    private fun installUpdate(context: Context, apkFile: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, context.packageName + ".provider", apkFile)
        } else {
            Uri.fromFile(apkFile)
        }

        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        installIntent.data = uri
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(installIntent)
    }
}
