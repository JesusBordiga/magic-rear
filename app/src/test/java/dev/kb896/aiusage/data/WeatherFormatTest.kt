package dev.kb896.aiusage.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The forecast's words, which both the front tab and the rear sheet spell: these are the
 * strings a person reads, so the rounding, the unit switch and the empty cases are pinned.
 */
class WeatherFormatTest {

    @Test fun `whole degrees, with the sign, in either unit`() {
        assertEquals("20°", WeatherFormat.temperature(20.4f, fahrenheit = false))
        assertEquals("32°", WeatherFormat.temperature(0f, fahrenheit = true))
    }

    @Test fun `a range is high over low`() {
        assertEquals("20° / 10°", WeatherFormat.range(10f, 20f, fahrenheit = false))
    }

    @Test fun `wind carries the gust when there is one, and mph divides`() {
        assertEquals("20 km/h W · gusts 35 km/h", WeatherFormat.wind(20f, 35f, 270, fahrenheit = false))
        assertEquals("12 mph", WeatherFormat.wind(20f, null, null, fahrenheit = true))
        assertEquals("—", WeatherFormat.wind(null, null, null))
    }

    @Test fun `gusts alone, for the day card that names the wind beside it`() {
        assertEquals("gusts 30 mph", WeatherFormat.gusts(48.28032f, fahrenheit = true))
        assertEquals("gusts 35 km/h", WeatherFormat.gusts(35f, fahrenheit = false))
        assertEquals("—", WeatherFormat.gusts(null, fahrenheit = false))
    }

    @Test fun `rain joins whichever halves the API returned`() {
        assertEquals("40% · 0.5 mm", WeatherFormat.rain(40, 0.5f))
        assertEquals("2 mm", WeatherFormat.rain(null, 2f))
        assertEquals("—", WeatherFormat.rain(null, null))
    }

    @Test fun `clock and sun cut the ISO stamp to a time`() {
        assertEquals("07:03", WeatherFormat.clock("2026-11-09T07:03"))
        assertEquals("07:03 · 16:55", WeatherFormat.sun("2026-11-09T07:03", "2026-11-09T16:55"))
        assertEquals("—", WeatherFormat.sun(null, null))
    }

    @Test fun `the first two days have names and the rest are weekdays`() {
        assertEquals("Today", WeatherFormat.dayLabel(0, "2026-11-09", Locale.US))
        assertEquals("Tomorrow", WeatherFormat.dayLabel(1, "2026-11-10", Locale.US))
        assertEquals("Wed 11", WeatherFormat.dayLabel(2, "2026-11-11", Locale.US))
    }

    @Test fun `the compass rounds to the nearest eighth`() {
        assertEquals("N", WeatherFormat.compass(0))
        assertEquals("N", WeatherFormat.compass(359))
        assertEquals("E", WeatherFormat.compass(90))
        assertEquals("SW", WeatherFormat.compass(225))
    }
}
