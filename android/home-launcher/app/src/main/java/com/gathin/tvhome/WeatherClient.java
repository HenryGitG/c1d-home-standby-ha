package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Public-IP city, then current conditions from Open-Meteo. All requests stay off the UI thread. */
final class WeatherClient {
    static final String CITY_KEY = "weather_city"; // empty means automatic public-IP location
    private static final long WEATHER_AGE = 45 * 60_000L;
    private static final long LOCATION_AGE = 24 * 60 * 60_000L;
    private final SharedPreferences cache;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean closed;
    private boolean loading;

    static final class Snapshot {
        final String city;
        final int temperature, high, low, code;
        final long updated;
        Snapshot(String city, int temperature, int high, int low, int code, long updated) {
            this.city = city;
            this.temperature = temperature;
            this.high = high;
            this.low = low;
            this.code = code;
            this.updated = updated;
        }
    }

    private static final class Place {
        final String city;
        final double latitude, longitude;
        Place(String city, double latitude, double longitude) {
            this.city = city;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    WeatherClient(Context context) {
        cache = context.getSharedPreferences("weather", Context.MODE_PRIVATE);
    }

    void refresh(Consumer<Snapshot> display) {
        String mode = cache.getString(CITY_KEY, "").trim();
        Snapshot previous = cached(mode);
        if (previous != null) display.accept(previous);
        if (closed || loading || previous != null && System.currentTimeMillis() - previous.updated < WEATHER_AGE) return;
        loading = true;
        worker.execute(() -> {
            Snapshot fresh = null;
            try {
                Place place = resolvePlace(mode);
                String url = "https://api.open-meteo.com/v1/forecast?latitude=" + place.latitude
                        + "&longitude=" + place.longitude
                        + "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min"
                        + "&timezone=auto&forecast_days=1";
                JSONObject response = read(url);
                JSONObject current = response.getJSONObject("current");
                JSONObject daily = response.getJSONObject("daily");
                int temperature = (int) Math.round(current.getDouble("temperature_2m"));
                int high = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
                int low = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
                int code = current.getInt("weather_code");
                fresh = new Snapshot(place.city, temperature, high, low, code, System.currentTimeMillis());
                cache.edit().putString("weather_mode", mode).putString("weather_city_name", fresh.city)
                        .putInt("weather_temp", temperature).putInt("weather_high", high)
                        .putInt("weather_low", low).putInt("weather_code", code)
                        .putLong("weather_updated", fresh.updated).apply();
            } catch (Exception ignored) {
                // A failed IP lookup or forecast must never invent weather values.
            }
            Snapshot result = fresh;
            main.post(() -> {
                loading = false;
                if (!closed) display.accept(result != null ? result : previous);
            });
        });
    }

    private Snapshot cached(String mode) {
        long updated = cache.getLong("weather_updated", 0);
        if (updated == 0 || System.currentTimeMillis() - updated > 6 * 60 * 60_000L
                || !mode.equals(cache.getString("weather_mode", ""))) return null;
        return new Snapshot(cache.getString("weather_city_name", ""), cache.getInt("weather_temp", 0),
                cache.getInt("weather_high", 0), cache.getInt("weather_low", 0),
                cache.getInt("weather_code", 0), updated);
    }

    private Place resolvePlace(String mode) throws Exception {
        long updated = cache.getLong("place_updated", 0);
        if (mode.equals(cache.getString("place_mode", "")) && updated > 0
                && System.currentTimeMillis() - updated < LOCATION_AGE) {
            return new Place(cache.getString("place_city", ""),
                    Double.longBitsToDouble(cache.getLong("place_lat", 0)),
                    Double.longBitsToDouble(cache.getLong("place_lon", 0)));
        }
        JSONObject data;
        if (mode.isEmpty()) {
            data = read("https://ipwho.is/");
            if (!data.optBoolean("success", false)) throw new IllegalStateException("IP location unavailable");
        } else {
            String query = java.net.URLEncoder.encode(mode, "UTF-8");
            JSONArray results = read("https://geocoding-api.open-meteo.com/v1/search?name=" + query
                    + "&count=1&language=" + (Locale.getDefault().getLanguage().equals("zh") ? "zh" : "en"))
                    .optJSONArray("results");
            if (results == null || results.length() == 0) throw new IllegalStateException("City not found");
            data = results.getJSONObject(0);
        }
        Place place = new Place(data.getString(mode.isEmpty() ? "city" : "name"),
                data.getDouble("latitude"), data.getDouble("longitude"));
        cache.edit().putString("place_mode", mode).putString("place_city", place.city)
                .putLong("place_lat", Double.doubleToRawLongBits(place.latitude))
                .putLong("place_lon", Double.doubleToRawLongBits(place.longitude))
                .putLong("place_updated", System.currentTimeMillis()).apply();
        return place;
    }

    private static JSONObject read(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() != 200) throw new IllegalStateException("HTTP " + connection.getResponseCode());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            try (java.io.InputStream input = connection.getInputStream()) {
                while ((count = input.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                    if (bytes.size() > 128 * 1024) throw new IllegalStateException("Response too large");
                }
            }
            return new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    void close() {
        closed = true;
        worker.shutdownNow();
    }
}
