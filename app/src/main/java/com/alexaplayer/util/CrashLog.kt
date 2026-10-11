package com.alexaplayer.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dumps an uncaught exception to the public Download/AlexaPlayer folder so a crash on
 * someone else's device can be read back without adb. Uses MediaStore on API 29+ (no
 * permission needed) and the legacy public folder below that.
 */
object CrashLog {

    fun write(context: Context, throwable: Throwable, tag: String) {
        runCatching { writeInternal(context.applicationContext, throwable, tag) }
    }

    private fun writeInternal(context: Context, throwable: Throwable, tag: String) {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        val text = buildString {
            append("tag=").append(tag).append('\n')
            append("time=")
                .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                .append('\n')
            append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            append("  android=").append(Build.VERSION.RELEASE)
            append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n\n")
            append(writer.toString())
        }
        val bytes = text.toByteArray()
        val name = "crash-$tag-${System.currentTimeMillis()}.txt"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/AlexaPlayer",
                )
            }
            val uri = context.contentResolver
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "AlexaPlayer",
            )
            dir.mkdirs()
            File(dir, name).writeBytes(bytes)
        }
    }
}