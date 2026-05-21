package com.example.cameralessphotography;

import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataTypeConverter {
    private Map<String, Object> weatherInfoMap;
    public DataTypeConverter() { weatherInfoMap = new HashMap<>(); }
    public Map<String, Object> getWeatherInfoMap4FirebaseQuery(HashMap<String, String> weatherInformation)
    {
        float wind_direction = Float.parseFloat(weatherInformation.get("windDirection"));
        float wind_speed = Float.parseFloat(weatherInformation.get("windSpeed"));
        float temperature = Float.parseFloat(weatherInformation.get("temperature"));
        int humidity = Integer.parseInt(weatherInformation.get("humidity"));
        float pressure = Float.parseFloat(weatherInformation.get("pressure"));
        float precipitation = Float.parseFloat(weatherInformation.get("dayRain"));
        float gust_speed = Float.parseFloat(weatherInformation.get("gustSpeed"));
        float gust_direction = Float.parseFloat(weatherInformation.get("gustDirection"));

        weatherInfoMap.clear();
        weatherInfoMap.put("station_name", weatherInformation.get("stationName"));
        weatherInfoMap.put("wind_direction", wind_direction);
        weatherInfoMap.put("wind_speed", wind_speed);
        weatherInfoMap.put("temperature", temperature);
        weatherInfoMap.put("humidity", humidity);
        weatherInfoMap.put("pressure", pressure);
        weatherInfoMap.put("precipitation", precipitation);
        weatherInfoMap.put("gust_speed", gust_speed);
        weatherInfoMap.put("gust_direction", gust_direction);
        weatherInfoMap.put("weather", weatherInformation.get("weather"));

        return weatherInfoMap;
    }
    public Map<String, Object> getShotResultMap4Firebase(Map<String, Object> shot_parameters, Map<String, Object> photograph_parameters)
    {
        Map<String, Object> shotResultMap = photograph_parameters;
        shotResultMap.put("R_station_name", shot_parameters.get("station_name"));
        shotResultMap.put("R_wind_direction", shot_parameters.get("wind_direction"));
        shotResultMap.put("R_wind_speed", shot_parameters.get("wind_speed"));
        shotResultMap.put("R_temperature", shot_parameters.get("temperature"));
        shotResultMap.put("R_humidity", shot_parameters.get("humidity"));
        shotResultMap.put("R_pressure", shot_parameters.get("pressure"));
        shotResultMap.put("R_precipitation", shot_parameters.get("precipitation"));
        shotResultMap.put("R_gust_speed", shot_parameters.get("gust_speed"));
        shotResultMap.put("R_gust_direction", shot_parameters.get("gust_direction"));
        shotResultMap.put("R_weather", shot_parameters.get("weather"));

        shotResultMap.put("R_latitude", shot_parameters.get("latitude"));
        shotResultMap.put("R_longitude", shot_parameters.get("longitude"));
        shotResultMap.put("R_pitch", shot_parameters.get("pitch"));
        shotResultMap.put("R_roll", shot_parameters.get("roll"));
        shotResultMap.put("R_yaw", shot_parameters.get("yaw"));

        shotResultMap.put("R_datetime", shot_parameters.get("datetime"));

        shotResultMap.put("photo_url", shot_parameters.get("photo_url"));
        shotResultMap.put("location_provider", shot_parameters.get("location_provider"));

        return shotResultMap;
    }
    public List<String> getLongiRoundList(double longitude)
    {
        List<String> list = new ArrayList<>();
        BigDecimal scale = new BigDecimal("0.001");
        for (double longi : Arrays.asList(longitude - 0.001, longitude, longitude + 0.001)) {
            BigDecimal bdLongi = new BigDecimal(longi);
            BigDecimal rounded = bdLongi.divide(scale, 0, RoundingMode.HALF_UP).multiply(scale);
            list.add(rounded.toString());
        }
        return list;
    }
    public List<Integer> getOrientationList(float pitch)
    {
        List<Integer> list;
        if(pitch<-45||pitch>45)
        {
            list = Arrays.asList(5, 6, 7, 8);
        }
        else
        {
            list = Arrays.asList(1, 2, 3, 4);
        }
        return list;
    }
    public int getTimeSlot(String datetime)
    {
        String hour = datetime.substring(11, 13);
        if(hour.equals("23") || hour.equals("00") || hour.equals("01")) return 0;
        if(hour.equals("02") || hour.equals("03") || hour.equals("04")) return 1;
        if(hour.equals("05") || hour.equals("06") || hour.equals("07")) return 2;
        if(hour.equals("08") || hour.equals("09") || hour.equals("10")) return 3;
        if(hour.equals("11") || hour.equals("12") || hour.equals("13")) return 4;
        if(hour.equals("14") || hour.equals("15") || hour.equals("16")) return 5;
        if(hour.equals("17") || hour.equals("18") || hour.equals("19")) return 6;
        if(hour.equals("20") || hour.equals("21") || hour.equals("22")) return 7;
        return -1;
    }
    public int getSeason(String datetime)
    {
        String month = datetime.substring(5, 7);
        if(month.equals("03") || month.equals("04") || month.equals("05")) return 0;
        if(month.equals("06") || month.equals("07") || month.equals("08")) return 1;
        if(month.equals("09") || month.equals("10") || month.equals("11")) return 2;
        if(month.equals("12") || month.equals("01") || month.equals("02")) return 3;
        return -1;
    }
}
