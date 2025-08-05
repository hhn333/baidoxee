package com.example.baidoxee;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class XevaoAdapter extends RecyclerView.Adapter<XevaoAdapter.XeVaoViewHolder> {

    private List<xevao> carList;

    public XevaoAdapter(List<xevao> carList) {
        this.carList = carList;
    }

    @NonNull
    @Override
    public XeVaoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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

        // Hiển thị hình ảnh (placeholder cho đến khi có URL thực)
        if (car.getImageUrl() != null && !car.getImageUrl().isEmpty()) {
            // Sử dụng Glide hoặc Picasso để load image từ URL
            // Glide.with(holder.itemView.getContext()).load(car.getImageUrl()).into(holder.imagePlate);
            holder.imagePlate.setImageResource(android.R.drawable.ic_menu_camera); // placeholder
        } else {
            // Hiển thị placeholder khi không có ảnh
            holder.imagePlate.setImageResource(android.R.drawable.ic_menu_camera);
        }

        // Thêm divider giữa các item (nếu có trong layout)
        if (holder.divider != null) {
            if (position < getItemCount() - 1) {
                holder.divider.setVisibility(View.VISIBLE);
            } else {
                holder.divider.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return carList != null ? carList.size() : 0;
    }

    // Method để update data khi filter
    public void updateData(List<xevao> filteredList) {
        this.carList = filteredList;
        notifyDataSetChanged();
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