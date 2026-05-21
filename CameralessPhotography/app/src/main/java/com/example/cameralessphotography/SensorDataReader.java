package com.example.cameralessphotography;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class SensorDataReader implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor megnetometer;
    private SensorEventListener listener;
    private float[] accelerometerValue;
    private float[] gyroscopeValue;
    private float[] megnetometerValue;

    public SensorDataReader(Context context)
    {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        megnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }
    public void startListening()
    {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, megnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }
    public void stopListening()
    {
        sensorManager.unregisterListener(this);
    }
    @Override
    public void onSensorChanged(SensorEvent event)
    {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) // 加速規
        {
            accelerometerValue = event.values;
        }
        else if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE) //陀螺儀
        {
            gyroscopeValue = event.values;
        }
        else if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) //磁強計
        {
            megnetometerValue = event.values;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i)
    {

    }

    public float[] getAccelerometerValue()
    {
        return accelerometerValue;
    }

    public float[] getGyroscopeValue()
    {
        return gyroscopeValue;
    }

    public float[] getMegnetometerValue() { return megnetometerValue; }
}
