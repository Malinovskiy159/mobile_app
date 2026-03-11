// ui/utils/DateTimeHelper.kt
package com.darim.ui.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateTimeHelper {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())

    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "только что"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                "$minutes ${getMinutesText(minutes)} назад"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "$hours ${getHoursText(hours)} назад"
            }
            diff < TimeUnit.DAYS.toMillis(2) -> "вчера"
            diff < TimeUnit.DAYS.toMillis(7) -> {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                "$days ${getDaysText(days)} назад"
            }
            else -> {
                formatDate(timestamp)
            }
        }
    }

    fun formatDate(timestamp: Long): String {
        return fullDateFormat.format(Date(timestamp))
    }

    fun formatMeetingTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = timestamp - now

        return when {
            diff < 0 -> "Встреча прошла"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                "через $minutes ${getMinutesText(minutes)}"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "сегодня в ${timeFormat.format(Date(timestamp))}"
            }
            diff < TimeUnit.DAYS.toMillis(2) -> {
                "завтра в ${timeFormat.format(Date(timestamp))}"
            }
            else -> {
                dateFormat.format(Date(timestamp))
            }
        }
    }

    private fun getMinutesText(minutes: Long): String {
        return when {
            minutes % 10 == 1L && minutes % 100 != 11L -> "минуту"
            minutes % 10 in 2..4 && (minutes % 100 !in 12..14) -> "минуты"
            else -> "минут"
        }
    }

    private fun getHoursText(hours: Long): String {
        return when {
            hours % 10 == 1L && hours % 100 != 11L -> "час"
            hours % 10 in 2..4 && (hours % 100 !in 12..14) -> "часа"
            else -> "часов"
        }
    }

    private fun getDaysText(days: Long): String {
        return when {
            days % 10 == 1L && days % 100 != 11L -> "день"
            days % 10 in 2..4 && (days % 100 !in 12..14) -> "дня"
            else -> "дней"
        }
    }
}