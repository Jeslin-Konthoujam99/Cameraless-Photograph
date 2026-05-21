package com.example.cameralessphotography;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MenuActivity extends Activity {

    private MenuAdapter menuAdapter;
    private RecyclerView menuRCV;
    private Bundle bd;
    private Map<String, String> camera_settings;
    private List<String> menuItemList = Arrays.asList("Shooting Direction", "Aspect Ratio", "Exposure Time", "White Balance", "Focal Length");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        init();
    }

    private void init()
    {
        bd = getIntent().getExtras();
        if(bd == null) bd = new Bundle();
        initCameraSettings();

        menuRCV = findViewById(R.id.menuRecyclerview);
        menuAdapter = new MenuAdapter(this, menuItemList);
        menuRCV.setAdapter(menuAdapter);
        menuRCV.setLayoutManager(new LinearLayoutManager(this));
    }
    public int getItemPosition(int position)
    {
        String setStr;
        String[] items;
        switch (position)
        {
            case 0:
                setStr = camera_settings.get("orientation");
                items = getResources().getStringArray(R.array.orientationSpinnerArray);
                break;
            case 1:
                setStr = camera_settings.get("aspect_ratio");
                items = getResources().getStringArray(R.array.aspect_ratioSpinnerArray);
                break;
            case 2:
                setStr = camera_settings.get("exposure");
                items = getResources().getStringArray(R.array.exposureSpinnerArray);
                break;
            case 3:
                setStr = camera_settings.get("white_balance");
                items = getResources().getStringArray(R.array.white_balanceSpinnerArray);
                break;
            case 4:
                setStr = camera_settings.get("focalLen");
                items = getResources().getStringArray(R.array.focalLenSpinnerArray);
                break;
            default:
                setStr = new String();
                items = new String[]{};
        }
        for(int i = 0; i < items.length; ++i)
            if(items[i].equals(setStr)) return i;
        return -1;
    }
    public void CameraSetting(int position, int item)
    {
        String[] items;
        String key;
        switch (position)
        {
            case 0:
                items = getResources().getStringArray(R.array.orientationSpinnerArray);
                key = "orientation";
                break;
            case 1:
                items = getResources().getStringArray(R.array.aspect_ratioSpinnerArray);
                key = "aspect_ratio";
                break;
            case 2:
                items = getResources().getStringArray(R.array.exposureSpinnerArray);
                key = "exposure";
                break;
            case 3:
                items = getResources().getStringArray(R.array.white_balanceSpinnerArray);
                key = "white_balance";
                break;
            case 4:
                items = getResources().getStringArray(R.array.focalLenSpinnerArray);
                key = "focalLen";
                break;
            default:
                items = new String[]{};
                key = new String();
        }
        if(items.length == 0) return;
        camera_settings.put(key, items[item]);
    }
    private void initCameraSettings()
    {
        camera_settings = new HashMap<>();
        if(bd.isEmpty())
        {
            camera_settings.put("orientation", "Auto");
            camera_settings.put("aspect_ratio", "Auto");
            camera_settings.put("exposure", "Auto");
            camera_settings.put("white_balance", "Auto");
            camera_settings.put("focalLen", "Auto");
        }
        else
        {
            camera_settings.put("orientation", bd.getString("orientation"));
            camera_settings.put("aspect_ratio", bd.getString("aspect_ratio"));
            camera_settings.put("exposure", bd.getString("exposure"));
            camera_settings.put("white_balance", bd.getString("white_balance"));
            camera_settings.put("focalLen", bd.getString("focalLen"));
        }
    }
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            Intent intent = new Intent(MenuActivity.this, MainActivity.class);
            bd.putString("orientation", camera_settings.get("orientation"));
            bd.putString("aspect_ratio", camera_settings.get("aspect_ratio"));
            bd.putString("exposure", camera_settings.get("exposure"));
            bd.putString("white_balance", camera_settings.get("white_balance"));
            bd.putString("focalLen", camera_settings.get("focalLen"));
            intent.putExtras(bd);
            startActivity(intent);
            finish();
        }
        return true;
    }
}