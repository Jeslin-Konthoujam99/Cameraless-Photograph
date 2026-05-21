package com.example.cameralessphotography;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.List;

public class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.ViewHolder>  {
    private Context context;
    private List<String> menuItemList;
    public MenuAdapter(Context context, List<String> menuItemList)
    {
        this.context = context;
        this.menuItemList = menuItemList;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_menu, parent, false);
        return new MenuAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position)
    {
        MenuActivity mac = (MenuActivity) context;
        TextView menuItemTV = holder.itemView.findViewById(R.id.menuTextView);
        Spinner menuItemSP = (Spinner)holder.itemView.findViewById(R.id.menuSpinner);
        menuItemTV.setText(menuItemList.get(position));
        int selectedPosition;
        ArrayAdapter<CharSequence> adapter;
        switch (position)
        {
            case 0:
                adapter = ArrayAdapter.createFromResource(context, R.array.orientationSpinnerArray, android.R.layout.simple_spinner_dropdown_item);
                break;
            case 1:
                adapter = ArrayAdapter.createFromResource(context, R.array.aspect_ratioSpinnerArray, android.R.layout.simple_spinner_dropdown_item);
                break;
            case 2:
                adapter = ArrayAdapter.createFromResource(context, R.array.exposureSpinnerArray, android.R.layout.simple_spinner_dropdown_item);
                break;
            case 3:
                adapter = ArrayAdapter.createFromResource(context, R.array.white_balanceSpinnerArray, android.R.layout.simple_spinner_dropdown_item);
                break;
            case 4:
                adapter = ArrayAdapter.createFromResource(context, R.array.focalLenSpinnerArray, android.R.layout.simple_spinner_dropdown_item);
                break;
            default:
                adapter = ArrayAdapter.createFromResource(context, R.array.defaltSpinnerArray, android.R.layout.simple_spinner_dropdown_item);
        }
        menuItemSP.setAdapter(adapter);
        menuItemSP.setSelection(mac.getItemPosition(position));
        int p = position;
        menuItemSP.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                mac.CameraSetting(p, i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    @Override
    public int getItemCount() {
        return menuItemList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        RelativeLayout relativeLayout;
        public ViewHolder(View itemView) {
            super(itemView);
            relativeLayout = itemView.findViewById(R.id.menuRelativeLayout);
        }
    }


}