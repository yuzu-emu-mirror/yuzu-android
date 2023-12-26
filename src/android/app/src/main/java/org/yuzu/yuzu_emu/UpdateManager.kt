import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Response
import org.json.JSONObject
import java.io.File

object UpdateManager {

    private const val DOWNLOAD_ID = 101

    fun checkAndInstallUpdate(context: Context) {
        GlobalScope.launch(Dispatchers.IO) {
            val currentVersionName =
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            val latestVersionName = getLatestVersionNameFromServer()

            withContext(Dispatchers.Main) {
                if (isNewVersionAvailable(currentVersionName, latestVersionName)) {
                    val apkUrl = "http://mkoc.cn/app/yuzu163.apk"
                    showUpdateDialog(context, apkUrl)
                } else {
                    showNoUpdateAvailableMessage(context)
                }
            }
        }
    }

    private suspend fun getLatestVersionNameFromServer(): String {
        return try {
            val response: Response =
                yourNetworkLibrary.executeRequest("http://mkoc.cn/aip/version.php")
            val responseBody = response.body?.string()

            if (responseBody != null) {
                val jsonObject = JSONObject(responseBody)
                jsonObject.getString("versionName")
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "检查更新时出错: ${e.message}")
            ""
        }
    }

    private fun isNewVersionAvailable(
        currentVersion: String,
        latestVersion: String
    ): Boolean {
        return latestVersion.compareTo(currentVersion) > 0
    }

    private fun showUpdateDialog(context: Context, apkUrl: String) {
        AlertDialog.Builder(context)
            .setTitle("发现新版本")
            .setMessage("有新版本可用，是否立即更新？")
            .setPositiveButton("更新") { dialog, which ->
                downloadAndInstallUpdate(context, apkUrl)
            }
            .setNegativeButton("稍后") { dialog, which ->
                // Handle update later
            }
            .show()
    }

    private fun downloadAndInstallUpdate(context: Context, apkUrl: String) {
        val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val downloadUri = Uri.parse(apkUrl)
        val request = DownloadManager.Request(downloadUri)

        val fileName = "update.apk"
        val destinationUri = Uri.fromFile(
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
        )
        request.setDestinationUri(destinationUri)

        val downloadId = downloadManager.enqueue(request)

        val downloadCompleteReceiver =
            DownloadCompleteReceiver(downloadId) { downloadedUri ->
                installApk(context, downloadedUri)
            }

        Toast.makeText(context, "正在下载更新...", Toast.LENGTH_LONG).show()
    }

    private fun installApk(context: Context, apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(intent)
    }

    private fun showNoUpdateAvailableMessage(context: Context) {
        Toast.makeText(context, "您的应用已经是最新版本。", Toast.LENGTH_SHORT).show()
    }
}
