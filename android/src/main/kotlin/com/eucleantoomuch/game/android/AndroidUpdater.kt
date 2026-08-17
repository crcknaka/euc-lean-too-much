package com.eucleantoomuch.game.android

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Keeps a side-loaded install up to date.
 *
 * The APK is not on any store, so nothing would otherwise tell a player a new build exists.
 * On startup this checks one small file published alongside the APK in the GitHub release,
 * and offers to install anything newer. After the first manual download the player never has
 * to visit the site again.
 *
 * Deliberately quiet: it runs on a background thread, and if the network is down, the file is
 * missing or malformed, it simply says nothing. A failed update check must never be something
 * that interrupts a game.
 */
class AndroidUpdater(private val activity: Activity) {

    fun checkInBackground() {
        Thread({ check() }, "euc-update-check").apply { isDaemon = true }.start()
    }

    private fun check() {
        val latest = try {
            fetchLatest()
        } catch (t: Throwable) {
            Log.i(TAG, "update check skipped: ${t.message}")
            return
        } ?: return

        val installed = installedVersionCode()
        if (latest.versionCode <= installed) {
            Log.i(TAG, "up to date (installed $installed, published ${latest.versionCode})")
            return
        }

        activity.runOnUiThread {
            if (activity.isFinishing) return@runOnUiThread
            AlertDialog.Builder(activity)
                .setTitle("Update available")
                .setMessage(
                    buildString {
                        append("Version ${latest.versionName} is out.")
                        if (latest.notes.isNotBlank()) append("\n\n${latest.notes}")
                    }
                )
                .setPositiveButton("Update") { _, _ -> download(latest) }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun fetchLatest(): Release? {
        val connection = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true   // /releases/latest/download/ is a redirect
            setRequestProperty("Accept", "application/json")
        }
        val body = try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val json = JSONObject(body)
        val code = json.optInt("versionCode", 0)
        val apk = json.optString("apk", "")
        if (code <= 0 || apk.isBlank()) return null
        return Release(
            versionCode = code,
            versionName = json.optString("versionName", ""),
            notes = json.optString("notes", ""),
            apkUrl = apk
        )
    }

    private fun installedVersionCode(): Int {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            info.versionCode
        }
    }

    private fun download(release: Release) {
        val target = File(activity.getExternalFilesDir(null), APK_NAME)
        // A leftover from a previous run would otherwise be installed instead of the new one
        if (target.exists()) target.delete()

        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("EUC Rider ${release.versionName}")
            .setDescription("Downloading update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(target))

        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = manager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                try {
                    activity.unregisterReceiver(this)
                } catch (_: IllegalArgumentException) {
                    // Already gone - harmless
                }
                if (target.exists()) install(target) else Log.w(TAG, "download produced no file")
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            activity.registerReceiver(receiver, filter)
        }
    }

    private fun install(apk: File) {
        // A file:// URI would be rejected from Android 7 on, hence the provider
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activity.startActivity(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "could not start the installer: ${t.message}")
        }
    }

    private data class Release(
        val versionCode: Int,
        val versionName: String,
        val notes: String,
        val apkUrl: String
    )

    private companion object {
        const val TAG = "AndroidUpdater"
        const val TIMEOUT_MS = 8000
        const val APK_NAME = "EUC-Rider-update.apk"

        /** Published with every release; the /latest/ path always resolves to the newest one. */
        const val LATEST_URL =
            "https://github.com/crcknaka/euc-lean-too-much/releases/latest/download/latest.json"
    }
}
