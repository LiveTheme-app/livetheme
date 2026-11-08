package io.hmg.wallpaperapp;

import org.json.*;

import java.time.*;
import java.util.*;

final class WeatherData {
    final String temperature, condition, rain, updated;

    WeatherData(String temp, String condition, String rain, String updated) {
        this.temperature = temp;
        this.condition = condition;
        this.rain = rain;
        this.updated = updated;
    }

    static WeatherData parse(JSONObject json) throws Exception {
        JSONObject current = json.getJSONObject("current"), hourly = json.getJSONObject("hourly");
        double temp = current.getDouble("temperature_2m");
        if (!Double.isFinite(temp) || temp < -100 || temp > 70)
            throw new IllegalArgumentException("temperature");
        LocalDateTime now = LocalDateTime.parse(current.getString("time"));
        JSONArray times = hourly.getJSONArray("time"), prob = hourly.getJSONArray(
                "precipitation_probability"), mm = hourly.getJSONArray("precipitation");
        if (times.length() != prob.length() || times.length() != mm.length() || times.length() > 400)
            throw new IllegalArgumentException("forecast arrays");
        StringBuilder rain = new StringBuilder();
        int count = 0;
        for (int i = 0; i < times.length() && count < 6; i++) {
            LocalDateTime t = LocalDateTime.parse(times.getString(i));
            if (t.isBefore(now.withMinute(0).withSecond(0).withNano(0)))
                continue;
            int chance = prob.isNull(i) ? -1 : prob.getInt(i);
            double amount = mm.isNull(i) ? Double.NaN : mm.getDouble(i);
            if (chance < -1 || chance > 100 ||
                    (!Double.isNaN(amount) && (!Double.isFinite(amount) || amount < 0 || amount > 1000)))
                throw new IllegalArgumentException("precipitation");
            if (count % 3 == 0 && count > 0)
                rain.append('\n');
            else if (count > 0)
                rain.append("   ");
            rain.append(String.format(Locale.KOREA, "%02d시 %s", t.getHour(), chance < 0 ? "—" : chance + "%"));
            if (Double.isFinite(amount))
                rain.append(String.format(Locale.KOREA, " · %.1f㎜", amount));
            count++;
        }
        if (count == 0)
            throw new IllegalArgumentException("forecast missing");
        return new WeatherData(String.format(Locale.KOREA, "%.1f°C", temp), condition(current.getInt("weather_code")),
                rain.toString(), current.getString("time").replace('T', ' '));
    }

    private static String condition(int code) {
        if (code == 0)
            return "맑음";
        if (code <= 3)
            return "구름";
        if (code == 45 || code == 48)
            return "안개";
        if (code >= 71 && code <= 77 || code == 85 || code == 86)
            return "눈";
        if (code >= 95)
            return "뇌우";
        if (code >= 51 && code <= 82)
            return "비";
        return "날씨";
    }
}
