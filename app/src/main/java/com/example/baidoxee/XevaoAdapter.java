package com.example.baidoxee;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import java.util.List;

public class XevaoAdapter extends RecyclerView.Adapter<XevaoAdapter.XeVaoViewHolder> {

    private static final String TAG = "XevaoAdapter";
    private List<xevao> carList;
    private Context context;

    public XevaoAdapter(List<xevao> carList) {
        this.carList = carList;
    }

    @NonNull
    @Override
    public XeVaoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        this.context = parent.getContext();
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_xevao, parent, false);
        return new XeVaoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull XeVaoViewHolder holder, int position) {
        xevao car = carList.get(position);

        // Hiển thị biển số
        if (car.getVehicle() != null && !car.getVehicle().isEmpty()) {
            holder.tvPlate.setText(car.getVehicle());
        } else {
            holder.tvPlate.setText("Không rõ");
        }

        // Hiển thị giờ vào
        if (car.getTimeIn() != null && !car.getTimeIn().isEmpty()) {
            holder.tvTimeIn.setText(car.getTimeIn());
        } else {
            holder.tvTimeIn.setText("--:--");
        }

        // Hiển thị hình ảnh biển số (base64 hoặc URL)
        loadPlateImage(holder, car);

        // Thêm divider giữa các item (nếu có trong layout)
        if (holder.divider != null) {
            if (position < getItemCount() - 1) {
                holder.divider.setVisibility(View.VISIBLE);
            } else {
                holder.divider.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Load hình ảnh biển số từ base64 hoặc URL
     */
    private void loadPlateImage(XeVaoViewHolder holder, xevao car) {
        if (context == null) {
            Log.w(TAG, "Context is null, cannot load image");
            holder.imagePlate.setImageResource(R.drawable.ic_car_placeholder);
            return;
        }

        String imageData = car.getImageUrl();

        if (imageData != null && !imageData.isEmpty() && !imageData.equals("null")) {
            Log.d(TAG, "Loading image for plate " + car.getVehicle());

            try {
                if (imageData.startsWith("data:image")) {
                    // Đây là data URI base64 - sử dụng Glide để load
                    RequestOptions requestOptions = new RequestOptions()
                            .placeholder(R.drawable.ic_loading_placeholder)
                            .error(R.drawable.ic_error_placeholder)
                            .fallback(R.drawable.ic_car_placeholder)
                            .diskCacheStrategy(DiskCacheStrategy.NONE) // Không cache base64
                            .centerCrop()
                            .override(200, 150);

                    Glide.with(context)
                            .load(imageData)
                            .apply(requestOptions)
                            .into(holder.imagePlate);

                } else if (imageData.startsWith("http")) {
                    // Đây là URL - sử dụng Glide bình thường
                    RequestOptions requestOptions = new RequestOptions()
                            .placeholder(R.drawable.ic_loading_placeholder)
                            .error(R.drawable.ic_error_placeholder)
                            .fallback(R.drawable.ic_car_placeholder)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .centerCrop()
                            .override(200, 150);

                    Glide.with(context)
                            .load(imageData)
                            .apply(requestOptions)
                            .into(holder.imagePlate);

                } else {
                    // Thử decode base64 thuần (không có prefix)
                    try {
                        byte[] decodedBytes = Base64.decode(imageData, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                        if (bitmap != null) {
                            holder.imagePlate.setImageBitmap(bitmap);
                            Log.d(TAG, "Successfully loaded base64 image for plate: " + car.getVehicle());
                        } else {
                            Log.w(TAG, "Failed to decode base64 image for plate: " + car.getVehicle());
                            holder.imagePlate.setImageResource(R.drawable.ic_error_placeholder);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error decoding base64 image for plate: " + car.getVehicle(), e);
                        holder.imagePlate.setImageResource(R.drawable.ic_error_placeholder);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error loading image for plate: " + car.getVehicle(), e);
                holder.imagePlate.setImageResource(R.drawable.ic_error_placeholder);
            }
        } else {
            // Hiển thị placeholder khi không có ảnh
            Log.d(TAG, "No image data for plate: " + car.getVehicle());
            holder.imagePlate.setImageResource(R.drawable.ic_car_placeholder);
        }
    }

    @Override
    public int getItemCount() {
        return carList != null ? carList.size() : 0;
    }

    /**
     * Method để update data khi filter
     */
    public void updateData(List<xevao> filteredList) {
        this.carList = filteredList;
        notifyDataSetChanged();
    }

    /**
     * Clear Glide cache khi cần thiết
     */
    public void clearImageCache() {
        if (context != null) {
            try {
                Glide.with(context).clear((Target<?>) context);
                Log.d(TAG, "Cleared Glide image cache");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing Glide cache", e);
            }
        }
    }

    static class XeVaoViewHolder extends RecyclerView.ViewHolder {
        TextView tvPlate, tvTimeIn;
        ImageView imagePlate;
        View divider;

        public XeVaoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPlate = itemView.findViewById(R.id.textPlate);
            tvTimeIn = itemView.findViewById(R.id.textTimeIn);
            imagePlate = itemView.findViewById(R.id.imagePlate);

            // Divider có thể không có trong layout, nên kiểm tra null
            divider = itemView.findViewById(R.id.divider);
        }
    }
}