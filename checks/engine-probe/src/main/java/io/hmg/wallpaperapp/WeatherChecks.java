package io.hmg.wallpaperapp;
import android.content.Context;
import android.util.Log;
import org.json.*;
import java.time.*;

public final class WeatherChecks {
 private static int passed;
 private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;}
 public static void run(Context context){try{
  passed=0;LocalDate today=LocalDate.of(2026,10,22);check("D-DAY".equals(DDayWidget.count(today,today)),"today");check("D-1".equals(DDayWidget.count(today,today.plusDays(1))),"tomorrow");check("D+1".equals(DDayWidget.count(today,today.minusDays(1))),"yesterday");check("D-2".equals(DDayWidget.count(LocalDate.of(2028,2,28),LocalDate.of(2028,3,1))),"leap year");
  JSONObject sample=new JSONObject("{\"current\":{\"temperature_2m\":18.5,\"weather_code\":61,\"time\":\"2026-10-22T23:15\"},\"hourly\":{\"time\":[\"2026-10-22T22:00\",\"2026-10-22T23:00\",\"2026-10-23T00:00\"],\"precipitation_probability\":[0,70,null],\"precipitation\":[0,1.2,null]}}");WeatherData result=WeatherData.parse(sample);check(result.temperature.contains("18.5"),"temperature");check(result.condition.equals("비"),"rain code");check(result.rain.contains("23시 70%")&&result.rain.contains("00시 —")&&!result.rain.contains("22시"),"midnight and null forecast");
  sample.getJSONObject("hourly").getJSONArray("precipitation_probability").put(1,200);try{WeatherData.parse(sample);throw new AssertionError("invalid chance accepted");}catch(IllegalArgumentException expected){passed++;}
  JSONObject config=CreatorTheme.defaults();config.getJSONObject("layouts").put("full/weather","0.7,0.1,1").put("split3/dday","0.5,0.5,1");config.getJSONObject("panel").put("dday",true).put("weather",true);config.put("weatherLocation","PRIVATE").put("ddayDate","PRIVATE");JSONObject valid=CreatorTheme.validate(config);check(!valid.toString().contains("PRIVATE"),"private values excluded");check(valid.getJSONObject("panel").getBoolean("weather")&&valid.getJSONObject("layouts").length()==2,"new widget share support");
  Log.i("WeatherChecks","PASS "+passed);
 }catch(Throwable e){Log.e("WeatherChecks","FAIL",e);throw new IllegalStateException(e);}}
}
