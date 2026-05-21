package com.example.photoinfouploader;

import android.content.Context;
import android.location.Location;
import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class EXIFReader {

    private ExifInterface exifInterface;
    private String photoPath;
    private HashMap<String, String> exifInfo;
    public EXIFReader()
    {
        exifInfo = new HashMap<>();
    }

    public HashMap<String, String> getEXIF()
    {
        try
        {
            exifInterface = new ExifInterface(photoPath);
            String exifLAT = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
            String exifLATR = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
            String exifLONG = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
            String exifLONGR = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
            if(exifLAT==null||exifLATR==null||exifLONG==null||exifLONGR==null) //經緯度
            {
                exifInfo.put("exifGPSLAT", null);
                exifInfo.put("exifGPSLONG", null);
            }
            else
            {
                exifInfo.put("exifGPSLAT", String.valueOf(transferLocation(exifLAT, exifLATR)));
                exifInfo.put("exifGPSLONG", String.valueOf(transferLocation(exifLONG, exifLONGR)));
            }

            exifInfo.put("exifOrient", exifInterface.getAttribute(ExifInterface.TAG_ORIENTATION)); //旋轉角度

            String dateTime = exifInterface.getAttribute(ExifInterface.TAG_DATETIME);
            if (dateTime == null) {
                dateTime = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).format(new Date());
            }
            exifInfo.put("exifDatetime", dateTime); //拍攝時間

            exifInfo.put("exifMaker", exifInterface.getAttribute(ExifInterface.TAG_MAKE)); //設備品牌
            exifInfo.put("exifModel", exifInterface.getAttribute(ExifInterface.TAG_MODEL)); //設備型號
            exifInfo.put("exifFlash", exifInterface.getAttribute(ExifInterface.TAG_FLASH)); //閃光燈
            exifInfo.put("exifImgLen", exifInterface.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)); //圖片長
            exifInfo.put("exifImgWid", exifInterface.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)); //圖片寬
            exifInfo.put("exifExposure", exifInterface.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)); //曝光時間
            exifInfo.put("exifAperture", exifInterface.getAttribute(ExifInterface.TAG_F_NUMBER)); //光圈值
            exifInfo.put("exifISO", exifInterface.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)); //感光度
            exifInfo.put("exifWB", exifInterface.getAttribute(ExifInterface.TAG_WHITE_BALANCE)); //白平衡
            exifInfo.put("exifFocalLen", exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)); //焦距
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return exifInfo;
    }

    public void addGPSinfo(Location location) {
        try {
            exifInterface = new ExifInterface(photoPath);

            double latitude = location.getLatitude();
            double longitude = location.getLongitude();

            // 設置經度和緯度
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE, GPSConvertBack(latitude));
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, GPSConvertBack(longitude));

            // 設置經度和緯度方向
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latitude >= 0 ? "N" : "S");
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, longitude >= 0 ? "E" : "W");

            // Ensure DateTime is set when adding GPS
            String currentTime = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).format(new Date());
            exifInterface.setAttribute(ExifInterface.TAG_DATETIME, currentTime);

            exifInterface.saveAttributes();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getPhotoOrientation(String photoPath)
    {
        try
        {
            exifInterface = new ExifInterface(photoPath);
            String orientation = exifInterface.getAttribute(ExifInterface.TAG_ORIENTATION);
            return orientation;
        }
        catch (IOException e)
        {
            return "";
        }
    }

    private static String GPSConvertBack(double value) {
        value = Math.abs(value);
        int degrees = (int) value;
        value = (value - degrees) * 60;
        int minutes = (int) value;
        value = (value - minutes) * 60;
        int seconds = (int) value;

        return degrees + "/1," + minutes + "/1," + seconds + "/1";
    }

    public void setPhotoPath(String path)
    {
        photoPath = path;
    }

    private double transferLocation(String relationalStr, String ref)
    {
        String[] parts = relationalStr.split(",");
        String[] pair;

        pair = parts[0].split("/");
        double degrees = Double.parseDouble(pair[0].trim())/Double.parseDouble(pair[1].trim());
        pair = parts[1].split("/");
        double minutes = Double.parseDouble(pair[0].trim())/Double.parseDouble(pair[1].trim());
        pair = parts[2].split("/");
        double seconds = Double.parseDouble(pair[0].trim())/Double.parseDouble(pair[1].trim());

        double result = degrees + (minutes/60.0)+(seconds/3600.0);
        if(ref.equals("S")||ref.equals("W"))
            return -result;
        return result;
    }

    public String getShotDirection(String exifOrient)
    {
        String shotDir = "None";
        switch(exifOrient)
        {
            case "1":
                shotDir = "Horizontal";
                break;
            case "2":
                shotDir = "Horizontal Mirror";
                break;
            case "3":
                shotDir = "Upside Down";
                break;
            case "4":
                shotDir = "Vertical Mirror";
                break;
            case "5":
                shotDir = "Vertical Mirror + Rotate";
                break;
            case "6":
                shotDir = "Vertical";
                break;
            case "7":
                shotDir = "Vertical Flip Mirror";
                break;
            case "8":
                shotDir = "Vertical Flip";
                break;
        }

        return shotDir;
    }
}