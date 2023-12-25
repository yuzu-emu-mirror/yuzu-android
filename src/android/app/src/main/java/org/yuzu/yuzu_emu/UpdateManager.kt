package org.yuzu.yuzu_emu

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

class UpdateManager(private val context: Context) {

    private val TAG = "UpdateManager"

    fun checkForUpdates() {
        val currentVersion = BuildConfig.VERSION_NAME // 当前应用版本
        val updateUrl = "http://mkoc.cn/aip/version.php" // 用于检查更新的服务器端点

        // 异步任务执行更新检查
        object : AsyncTask<Void, Void, String>() {
            override fun doInBackground(vararg params: Void): String {
                try {
                    val client = OkHttpClient()
                    val request = Request.Builder()
                        .url(updateUrl)
                        .build()

                    val response: Response = client.newCall(request).execute()

                    val responseBody = response.body()
                    val responseCode = response.code()

                    if (responseBody != null) {
                        if (responseCode == 200) {
                            val result = responseBody.string()
                            return result
                        } else {
                            Log.e(TAG, "Unsuccessful response: $responseCode")
                        }
                    } else {
                        Log.e(TAG, "Response body is empty")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking for updates: ${e.message}")
                }
                return ""
            }

            override fun onPostExecute(result: String) {
                super.onPostExecute(result)
                if (result.isNotEmpty()) {
                    handleUpdateResponse(result, currentVersion)
                }
            }
        }.execute()
    }

    private fun handleUpdateResponse(response: String, currentVersion: String) {
        try {
            val jsonObject = JSONObject(response)
            val latestVersion = jsonObject.getString("version")
            val releaseNotes = jsonObject.getString("release_notes")
            val downloadUrl = jsonObject.getString("download_url")

            if (latestVersion != currentVersion) {
                showUpdateDialog(latestVersion, releaseNotes, downloadUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing update response: ${e.message}")
        }
    }

    private fun showUpdateDialog(version: String, releaseNotes: String, downloadUrl: String) {
        val alertDialogBuilder = AlertDialog.Builder(context)
        alertDialogBuilder.setTitle("有新的更新可用")
        alertDialogBuilder.setMessage("版本: $version\n\n$releaseNotes")
        alertDialogBuilder.setPositiveButton("立即更新") { dialog, _ ->
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(downloadUrl)
            context.startActivity(intent)
            dialog.dismiss()
        }
        alertDialogBuilder.setNegativeButton("稍后再说") { dialog, _ ->
            dialog.dismiss()
        }
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }
}
