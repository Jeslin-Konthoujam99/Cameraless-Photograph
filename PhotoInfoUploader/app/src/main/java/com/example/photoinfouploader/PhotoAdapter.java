package com.example.photoinfouploader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.Rotate;
import com.bumptech.glide.request.RequestOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.ViewHolder> {
    private Context context;
    private JSONArray photoArray;
    private int albumId;
    private EXIFReader exifReader;

    public PhotoAdapter(Context context, JSONArray photoArray, int albumId) {
        this.context = context;
        this.photoArray = photoArray;
        this.albumId = albumId;
        exifReader = new EXIFReader();
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try
        {
            JSONObject photoObject = photoArray.getJSONObject(position);
            String photoPath = photoObject.getString("photo path");

            new Thread(() -> {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 16;
                Bitmap bitmap = BitmapFactory.decodeFile(photoPath, options);

                holder.itemView.post(() -> {
                    RequestOptions glideOptions = new RequestOptions()
                            .override(300, 300)
                            .format(DecodeFormat.PREFER_RGB_565)
                            .transform(new CenterCrop());
                    
                    if ("6".equals(exifReader.getPhotoOrientation(photoPath))) {
                        glideOptions = glideOptions.transform(new CenterCrop(), new Rotate(90));
                    }
                    
                    Glide.with(context)
                            .load(bitmap)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .placeholder(R.drawable.main_default_album_icon)
                            .apply(glideOptions)
                            .into(holder.imageView);
                });
            }).start();

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, PhotoActivity.class);
                Bundle bundle = new Bundle();
                bundle.putInt("album ID", albumId);
                bundle.putString("photo json object", photoObject.toString());
                bundle.putBoolean("preview mode", false);
                intent.putExtras(bundle);
                context.startActivity(intent);
            });
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return photoArray.length();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.photosImageView);
        }
    }
}
