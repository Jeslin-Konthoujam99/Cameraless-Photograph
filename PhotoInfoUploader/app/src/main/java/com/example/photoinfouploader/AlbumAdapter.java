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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.ViewHolder>  {
    private Context context;
    private List<Integer> albumIdList;
    private HashMap<Integer, Bitmap> albumImageMap;
    private List<String> albumNameList;
    private Boolean deleteMode;
    private StorageTools storageTools;
    private Button visibleBTN;
    public AlbumAdapter(Context context, List<Integer> albumIdList, HashMap<Integer, Bitmap> albumImageMap, List<String> albumNameList)
    {
        this.context = context;
        this.albumIdList = albumIdList;
        this.albumImageMap = albumImageMap;
        this.albumNameList = albumNameList;
        this.deleteMode = false;
        storageTools = new StorageTools();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_album, parent, false);
        return new AlbumAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        int currentPos = position;
        ImageView albumIV = holder.itemView.findViewById(R.id.albumImageView);
        TextView albumNameTV = holder.itemView.findViewById(R.id.albumNameTextView);
        Button deleteAlbumBTN = holder.itemView.findViewById(R.id.deleteAlbumButton);
        int albumId = albumIdList.get(position);
        albumNameTV.setText(albumNameList.get(position));

        Bitmap bitmap;
        if(albumImageMap.containsKey(albumId))
        {
            bitmap = albumImageMap.get(albumId);
        }
        else
        {
            bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.main_default_album_icon);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                RoundedCorners roundedCorners = new RoundedCorners(20);

                holder.itemView.post(new Runnable() {
                    @Override
                    public void run() {
                        RequestOptions options = new RequestOptions()
                                .override(300, 300) // Set target width and height
                                .format(DecodeFormat.PREFER_RGB_565) //Configure the decoding format to reduce memory usage.
                                .centerCrop()
                                .transform(new CenterCrop(), roundedCorners); // If the image aspect ratio does not match the target aspect ratio, perform a center crop
                        Glide.with(context).load(bitmap).diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true).apply(options).into(albumIV);
                    }
                });
            }
        }).start();


        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!deleteMode)
                {
                    Intent intent = new Intent(context, AlbumActivity.class);
                    Bundle bundle = new Bundle();
                    bundle.putInt("album ID", albumId);
                    bundle.putBoolean("save", false);
                    intent.putExtras(bundle);
                    context.startActivity(intent);
                    ((Activity)context).finish();
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            // 步骤2: 长按时显示tempButton，禁用所有itemView的点击事件
            deleteAlbumBTN.setVisibility(View.VISIBLE);
            visibleBTN = deleteAlbumBTN;
            deleteMode = true;
            return true;
        });

        deleteAlbumBTN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(storageTools.deleteAlbum(context, albumId))
                {
                    if(albumImageMap.containsKey(albumId)) albumImageMap.remove(albumId);
                    albumIdList.remove(currentPos);
                    albumNameList.remove(currentPos);
                    notifyItemRemoved(currentPos);
                    notifyItemRangeChanged(currentPos, getItemCount());
                    notifyItemRemoved(currentPos);
                    deleteMode = false;
                    if(albumIdList.isEmpty())
                    {
                        TextView tv = ((Activity)context).findViewById(R.id.mainHintTextView);
                        tv.setVisibility(View.VISIBLE);
                    }
                }
                else
                {
                    Toast toast = Toast.makeText(v.getContext(), "Deletion failed!", Toast.LENGTH_SHORT);
                    toast.show();
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return albumIdList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        RelativeLayout relativeLayout;
        public ViewHolder(View itemView) {
            super(itemView);
            relativeLayout = itemView.findViewById(R.id.albumsRelativeLayout);
        }
    }

    public boolean getDeleteMode()
    {
        return deleteMode;
    }

    public void exitDeleteMode()
    {
        deleteMode = false;
        visibleBTN.setVisibility(View.INVISIBLE);
        visibleBTN = null;
    }
}
