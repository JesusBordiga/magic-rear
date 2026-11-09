package dev.kb896.aiusage.ui

import android.widget.LinearLayout
import dev.kb896.aiusage.R
import dev.kb896.aiusage.data.PanelConfig
import dev.kb896.aiusage.data.TemperatureUnit
import dev.kb896.aiusage.data.WeatherForecast
import dev.kb896.aiusage.data.WeatherFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Weather tab's body: the current conditions, then today and the five days after it,
 * with the wind and the chance of rain the clock line has no room for.
 *
 * Built on every visit from whatever forecast the activity last fetched. A missing
 * forecast is a state to explain rather than an error: the place may not be set yet, or
 * the phone may not have a fix, and the reason is what the tab shows.
 */
object WeatherTab {

    fun render(
        rows: LinearLayout,
        config: PanelConfig,
        forecast: WeatherForecast?,
        loading: Boolean,
        onRefresh: () -> Unit,
    ) {
        rows.removeAllViews()
        SettingsRows.title(rows, "Weather")

        val fahrenheit = TemperatureUnit.resolve(config.temperatureUnit) == TemperatureUnit.FAHRENHEIT
        if (forecast == null) {
            val typed = config.weatherPlaceLabel.ifEmpty { config.weatherPlace }
            SettingsRows.note(
                rows,
                when {
                    loading -> "Looking up the forecast…"
                    config.weatherUseDevice ->
                        "Waiting for a location fix. The forecast appears once the phone knows where it is."
                    typed.isBlank() ->
                        "Set a place in General → Weather, or use the phone's location."
                    else -> "No forecast yet. It updates every few minutes while the panel is on."
                },
            )
        } else {
            val updated = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(forecast.fetchedAt))
            val where = forecast.place.ifBlank { "Here" }
            SettingsRows.note(rows, "Now · $where · updated $updated")

            val now = forecast.now
            SettingsRows.readout(rows, "Temperature", WeatherFormat.temperature(now.temperatureC, fahrenheit))
            SettingsRows.readout(rows, "Condition", now.condition.word)
            SettingsRows.readout(
                rows, "Wind",
                WeatherFormat.wind(now.windKmh, now.windGustKmh, now.windDirDeg, fahrenheit),
            )
            SettingsRows.readout(rows, "Rain today", WeatherFormat.rain(now.rainChance, now.precipitationMm))
            SettingsRows.readout(rows, "Humidity", WeatherFormat.humidity(now.humidity))

            SettingsRows.title(rows, "Forecast")
            forecast.days.forEachIndexed { index, day ->
                SettingsRows.readout(
                    rows,
                    WeatherFormat.dayLabel(index, day.date),
                    "${WeatherFormat.range(day.minC, day.maxC, fahrenheit)} · ${day.condition.word}",
                )
                SettingsRows.dim(
                    rows,
                    "Rain ${WeatherFormat.rain(day.rainChance, day.precipitationMm)} · " +
                        "wind ${WeatherFormat.wind(day.windMaxKmh, day.windGustKmh, day.windDirDeg, fahrenheit)}",
                )
                SettingsRows.dim(rows, "Sun ${WeatherFormat.sun(day.sunrise, day.sunset)}")
            }
        }

        SettingsRows.navigation(
            rows,
            if (loading) "Refreshing…" else "Refresh forecast",
            icon = R.drawable.ic_refresh,
        ) { onRefresh() }
        SettingsRows.note(rows, "Weather by Open-Meteo · the place is sent to it to be found")
    }
}
