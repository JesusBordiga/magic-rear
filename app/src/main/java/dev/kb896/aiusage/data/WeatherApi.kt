package dev.kb896.aiusage.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Open-Meteo, for one current reading and for turning a typed place into coordinates.
 *
 * Free, no key, and the terms ask only for attribution, which the settings row gives.
 * Same shape as LyricsApi: a parsed value or null, and nothing thrown across the
 * boundary — a dead network on a screen nobody is looking at is not news.
 */
class WeatherApi(
    client: OkHttpClient,
    private val forecastUrl: HttpUrl = "https://api.open-meteo.com/".toHttpUrl(),
    private val geocodeUrl: HttpUrl = "https://geocoding-api.open-meteo.com/".toHttpUrl(),
) {
    /** Ten seconds all in: a slow answer is worth exactly as much as none. */
    private val client = client.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()

    /**
     * Why the last read came back empty — "HTTP 429", "SocketTimeoutException", ... — so
     * the debug card can say what a blank forecast was. Null after a successful read.
     */
    @Volatile
    var trouble: String? = null
        private set

    data class Place(val label: String, val latitude: Double, val longitude: Double)

    /**
     * A coordinate as it leaves the phone: two decimals, about a kilometre (1.1 km of
     * latitude, less of longitude away from the equator). The weather is the same a street
     * away, and the device's own location, sent every few minutes, says no more than the
     * neighbourhood. The owner's call, 2026-10-30.
     */
    private fun nearKm(degrees: Double): String =
        java.math.BigDecimal(degrees).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    fun current(latitude: Double, longitude: Double, place: String, nowMs: Long): WeatherReading? {
        val url = forecastUrl.newBuilder()
            .addPathSegments("v1/forecast")
            .addQueryParameter("latitude", nearKm(latitude))
            .addQueryParameter("longitude", nearKm(longitude))
            .addQueryParameter("current", "temperature_2m,weather_code,is_day")
            .addQueryParameter("timezone", "auto")
            .build()
        val body = get(url) ?: return null
        return try {
            val cur = (Json.parseToJsonElement(body) as? JsonObject)?.get("current") as? JsonObject
                ?: return null
            val t = (cur["temperature_2m"] as? JsonPrimitive)?.floatOrNull ?: return null
            val code = (cur["weather_code"] as? JsonPrimitive)?.intOrNull ?: return null
            val day = ((cur["is_day"] as? JsonPrimitive)?.intOrNull ?: 1) != 0
            WeatherReading(t, code, day, place, nowMs)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The current conditions plus [days] days of forecast, in one request. Same coordinates
     * as [current] so the clock line and the weather page can never disagree about where
     * they are; the daily array starts at today.
     */
    fun forecast(latitude: Double, longitude: Double, place: String, nowMs: Long, days: Int = 6): WeatherForecast? {
        val url = forecastUrl.newBuilder()
            .addPathSegments("v1/forecast")
            .addQueryParameter("latitude", nearKm(latitude))
            .addQueryParameter("longitude", nearKm(longitude))
            .addQueryParameter("current", "temperature_2m,weather_code,is_day,wind_speed_10m," +
                "wind_direction_10m,wind_gusts_10m,relative_humidity_2m,precipitation")
            .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min," +
                "precipitation_probability_max,precipitation_sum,wind_speed_10m_max," +
                "wind_gusts_10m_max,wind_direction_10m_dominant,sunrise,sunset")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", days.toString())
            .build()
        val body = get(url) ?: return null
        return try {
            val root = Json.parseToJsonElement(body) as? JsonObject ?: return null
            val cur = root["current"] as? JsonObject ?: return null
            val daily = root["daily"] as? JsonObject ?: return null
            val time = daily["time"] as? JsonArray ?: return null
            val codes = daily["weather_code"] as? JsonArray
            val maxes = daily["temperature_2m_max"] as? JsonArray
            val mins = daily["temperature_2m_min"] as? JsonArray
            val chances = daily["precipitation_probability_max"] as? JsonArray
            val sums = daily["precipitation_sum"] as? JsonArray
            val winds = daily["wind_speed_10m_max"] as? JsonArray
            val gusts = daily["wind_gusts_10m_max"] as? JsonArray
            val dirs = daily["wind_direction_10m_dominant"] as? JsonArray
            val rises = daily["sunrise"] as? JsonArray
            val sets = daily["sunset"] as? JsonArray

            val now = WeatherNow(
                temperatureC = cur.floatAt("temperature_2m") ?: return null,
                code = cur.intAt("weather_code") ?: return null,
                isDay = (cur.intAt("is_day") ?: 1) != 0,
                windKmh = cur.floatAt("wind_speed_10m"),
                windGustKmh = cur.floatAt("wind_gusts_10m"),
                windDirDeg = cur.intAt("wind_direction_10m"),
                humidity = cur.intAt("relative_humidity_2m"),
                precipitationMm = cur.floatAt("precipitation"),
                // Open-Meteo has no current rain probability; today's daily figure is the
                // one the forecast already carries, and half a "now" that disagrees with
                // the Today row below it would be worse than borrowing it.
                rainChance = chances?.intAt(0),
            )

            val list = ArrayList<WeatherDay>(time.size)
            for (i in 0 until minOf(days, time.size)) {
                val date = time.stringAt(i) ?: continue
                val max = maxes?.floatAt(i) ?: continue
                val min = mins?.floatAt(i) ?: continue
                list += WeatherDay(
                    date = date,
                    code = codes?.intAt(i) ?: now.code,
                    maxC = max,
                    minC = min,
                    rainChance = chances?.intAt(i),
                    precipitationMm = sums?.floatAt(i),
                    windMaxKmh = winds?.floatAt(i),
                    windGustKmh = gusts?.floatAt(i),
                    windDirDeg = dirs?.intAt(i),
                    sunrise = rises?.stringAt(i),
                    sunset = sets?.stringAt(i),
                )
            }
            if (list.isEmpty()) return null
            WeatherForecast(now, list, place, nowMs)
        } catch (e: Exception) {
            trouble = "parse: " + (e.message ?: e.javaClass.simpleName)
            null
        }
    }

    private fun JsonObject.floatAt(key: String): Float? = (this[key] as? JsonPrimitive)?.floatOrNull
    private fun JsonObject.intAt(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.stringAt(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonArray.floatAt(index: Int): Float? = (getOrNull(index) as? JsonPrimitive)?.floatOrNull
    private fun JsonArray.intAt(index: Int): Int? = (getOrNull(index) as? JsonPrimitive)?.intOrNull
    private fun JsonArray.stringAt(index: Int): String? = (getOrNull(index) as? JsonPrimitive)?.contentOrNull

    fun geocode(query: String): Place? {
        if (query.isBlank()) return null
        val url = geocodeUrl.newBuilder()
            .addPathSegments("v1/search")
            .addQueryParameter("name", query.trim())
            .addQueryParameter("count", "1")
            .addQueryParameter("language", "en")
            .addQueryParameter("format", "json")
            .build()
        val body = get(url) ?: return null
        return try {
            val first = ((Json.parseToJsonElement(body) as? JsonObject)?.get("results") as? JsonArray)
                ?.firstOrNull() as? JsonObject ?: return null
            val name = (first["name"] as? JsonPrimitive)?.contentOrNull ?: return null
            val country = (first["country"] as? JsonPrimitive)?.contentOrNull
            val lat = (first["latitude"] as? JsonPrimitive)?.doubleOrNull ?: return null
            val lon = (first["longitude"] as? JsonPrimitive)?.doubleOrNull ?: return null
            Place(if (country.isNullOrBlank()) name else "$name, $country", lat, lon)
        } catch (e: Exception) {
            null
        }
    }

    private fun get(url: HttpUrl): String? {
        val req = Request.Builder().url(url)
            .header("User-Agent", Agent.saying("rear panel weather"))
            .get().build()
        return try {
            client.newCall(req).execute().use { res ->
                trouble = if (res.isSuccessful) null else "HTTP " + res.code
                if (res.isSuccessful) res.body?.string() else null
            }
        } catch (e: IOException) {
            trouble = e.javaClass.simpleName + (e.message?.let { ": " + it } ?: "")
            null
        } catch (e: IllegalStateException) {
            trouble = "IllegalStateException" + (e.message?.let { ": " + it } ?: "")
            null
        }
    }
}
