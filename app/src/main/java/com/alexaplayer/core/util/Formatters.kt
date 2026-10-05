package com.alexaplayer.core.util

import android.content.Context
import android.text.format.DateUtils
import com.alexaplayer.R
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Duration formatting used everywhere a timestamp is shown. */
object DurationFormatter {

    /** `3:07` or `1:02:44`; null-safe and never throws on negative values. */
    fun format(millis: Long?): String {
        if (millis == null || millis < 0) return "--:--"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /** Human summary such as `1 hr 24 min` for library headers. */
    fun formatTotal(millis: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> String.format(Locale.US, "%d hr %d min", hours, minutes)
            hours > 0 -> String.format(Locale.US, "%d hr", hours)
            else -> String.format(Locale.US, "%d min", minutes)
        }
    }
}

object RelativeTime {
    /** `Just now`, `2 hours ago`, `3 days ago`. */
    fun format(context: Context, timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val elapsed = (nowMillis - timestampMillis).coerceAtLeast(0L)
        return when {
            elapsed < DateUtils.MINUTE_IN_MILLIS -> context.getString(R.string.relative_time_just_now)
            else -> DateUtils.getRelativeTimeSpanString(
                timestampMillis,
                nowMillis,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString()
        }
    }
}
