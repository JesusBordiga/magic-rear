package dev.kb896.aiusage.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A fuller weather picture than [WeatherReading]: the current conditions plus one entry
 * per day — today and the five after it — with the figures the panel's weather page and
 * the front's Weather tab show: the wind (speed, gusts and where it comes from), the
 * chance of rain and how much is expected.
 *
 * [WeatherReading] stays the small, frequently-crossed fact the clock line needs. This is
 * the same reading plus the forecast, fetched together so the two can never disagree.
 */
data class WeatherForecast(
    val now: WeatherNow,
    val days: List<WeatherDay>,
    val place: String,
    val fetchedAt: Long,
) {
    /** Wall-clock, for the same reason [WeatherReading.isFresh] is. */
    fun isFresh(nowMs: Long): Boolean = nowMs - fetchedAt <= MAX_AGE_MS

    /** Today's entry, when the API returned one. */
    val today: WeatherDay? get() = days.firstOrNull()

    /** The reading the clock line knows how to draw, out of the same payload. */
    fun reading(): WeatherReading =
        WeatherReading(now.temperatureC, now.code, now.isDay, place, fetchedAt)

    companion object {
        /** Two hours, matching the reading: older than that is a guess. */
        const val MAX_AGE_MS = 7_200_000L
    }
}

/** What it is doing now. The optional figures are absent when Open-Meteo omits them. */
data class WeatherNow(
    val temperatureC: Float,
    val code: Int,
    val isDay: Boolean,
    val windKmh: Float?,
    val windGustKmh: Float?,
    val windDirDeg: Int?,
    val humidity: Int?,
    val precipitationMm: Float?,
    /** Today's highest chance of rain, which is the only "rain probability" Open-Meteo gives for a day. */
    val rainChance: Int?,
) {
    val condition: Condition get() = Condition.of(code, isDay)
}

/** One day of the forecast. [date] is Open-Meteo's ISO date, in the phone's own timezone. */
data class WeatherDay(
    val date: String,
    val code: Int,
    val maxC: Float,
    val minC: Float,
    val rainChance: Int?,
    val precipitationMm: Float?,
    val windMaxKmh: Float?,
    val windGustKmh: Float?,
    val windDirDeg: Int?,
    val sunrise: String?,
    val sunset: String?,
) {
    val condition: Condition get() = Condition.of(code, true)
}

/**
 * The words and the units, kept out of both views so the front tab and the rear page say
 * a figure the same way. Nothing here touches Android, so it is JVM-testable.
 */
object WeatherFormat {

    /** Whole degrees, with the degree sign; Fahrenheit when the setting settled on it. */
    fun temperature(celsius: Float, fahrenheit: Boolean): String =
        "${Math.round(if (fahrenheit) celsius * 9f / 5f + 32f else celsius)}°"

    /** High over low, the order a forecast row is read in. */
    fun range(minC: Float, maxC: Float, fahrenheit: Boolean): String =
        "${temperature(maxC, fahrenheit)} / ${temperature(minC, fahrenheit)}"

    /** "12 km/h NW", with the gusts when the API has them. */
    fun wind(speedKmh: Float?, gustKmh: Float?, directionDeg: Int?, fahrenheit: Boolean = false): String {
        if (speedKmh == null) return "—"
        val unit = if (fahrenheit) "mph" else "km/h"
        val speed = if (fahrenheit) speedKmh / KMH_PER_MPH else speedKmh
        val head = "${speed.roundToInt()} $unit${directionDeg?.let { " ${compass(it)}" }.orEmpty()}"
        val gust = gustKmh?.let {
            val g = if (fahrenheit) it / KMH_PER_MPH else it
            " · gusts ${g.roundToInt()} $unit"
        }.orEmpty()
        return head + gust
    }

    /** "40% · 0.5 mm", or whichever half the API returned. */
    fun rain(chance: Int?, precipitationMm: Float?): String {
        val percent = chance?.let { "$it%" }
        val amount = precipitationMm?.let { "${trim(it)} mm" }
        return listOfNotNull(percent, amount).joinToString(" · ").ifEmpty { "—" }
    }

    /** Just the gusts, for the day column that already names the wind beside it. */
    fun gusts(gustKmh: Float?, fahrenheit: Boolean): String {
        if (gustKmh == null) return "—"
        val g = if (fahrenheit) gustKmh / KMH_PER_MPH else gustKmh
        return "gusts ${g.roundToInt()} ${if (fahrenheit) "mph" else "km/h"}"
    }

    fun humidity(percent: Int?): String = percent?.let { "$it%" } ?: "—"

    /** A clock time out of an ISO stamp: "2026-11-09T07:03" → "07:03". */
    fun clock(iso: String?): String? = iso?.substringAfter('T', "")?.take(5)?.takeIf { it.length == 5 }

    /** "07:03 · 16:55", or whichever half exists. */
    fun sun(sunrise: String?, sunset: String?): String {
        val up = clock(sunrise)
        val down = clock(sunset)
        return listOfNotNull(up, down).joinToString(" · ").ifEmpty { "—" }
    }

    /**
     * The row's name: Today and Tomorrow are what a person calls the first two days, and
     * the rest are the short weekday and the date — "Wed 12".
     */
    fun dayLabel(index: Int, date: String, locale: Locale = Locale.getDefault()): String = when (index) {
        0 -> "Today"
        1 -> "Tomorrow"
        else -> try {
            LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE d", locale))
        } catch (_: Exception) {
            date
        }
    }

    /** The sixteen-point compass, rounded to the nearest eighth for a line this short. */
    fun compass(degrees: Int): String {
        val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val at = ((degrees % 360 + 360) % 360 + 22.5) / 45.0
        return points[at.toInt() % 8]
    }

    private fun trim(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString()
        else String.format(Locale.US, "%.1f", value)

    private const val KMH_PER_MPH = 1.609344f
}
