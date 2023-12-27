package org.yuzu.yuzu_emu

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException

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
        val downloadDirectory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val apkFile = File(downloadDirectory, "update.apk")

        AlertDialog.Builder(context)
            .setTitle("有新版本可用")
            .setMessage("有新版本可用。现在更新吗？")
            .setPositiveButton("更新") { dialog, which ->
                downloadAndInstallUpdate(context, apkFile)
            }
            .setNegativeButton("稍后") { dialog, which ->
                // 处理稍后更新的逻辑
            }
            .show()
    }

    private fun showNoUpdateMessage(context: Context) {
        // 显示没有更新的消息
    }

    private fun downloadAndInstallUpdate(context: Context, apkFile: File) {
        val downloadUrl = "http://mkoc.cn/app/yuzu.apk"
        val request = Request.Builder()
            .url(downloadUrl)
            .build()

        val progressDialog = ProgressDialog(context)
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progressDialog.setTitle("下载中") // 设置对话框标题
        progressDialog.setMessage("请稍候...")
        progressDialog.isIndeterminate = false
        progressDialog.setCancelable(false)
        progressDialog.max = 100
        progressDialog.show()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("UpdateManager", "下载失败: ${e.message}")
                progressDialog.dismiss()
                // 处理下载失败
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val inputStream = response.body?.byteStream()
                    val totalSize = response.body?.contentLength() ?: 0

                    try {
                        inputStream?.use { input ->
                            apkFile.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                var totalBytesRead: Long = 0

                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead.toLong()
                                    val progress = (totalBytesRead * 100 / totalSize).toInt()
                                    progressDialog.progress = progress

                                    // 更新下载进度消息
                                    val progressMessage = "下载进度: $progress%"
                                    progressDialog.setMessage(progressMessage)
                                }
                            }
                        }
                        progressDialog.dismiss()
                        installUpdate(context, apkFile)
                    } catch (e: IOException) {
                        Log.e("UpdateManager", "复制文件时出错: ${e.message}")
                        progressDialog.dismiss()
                        // 处理文件复制错误
                    }
                } else {
                    Log.e("UpdateManager", "下载失败: HTTP ${response.code()}")
                    progressDialog.dismiss()
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
