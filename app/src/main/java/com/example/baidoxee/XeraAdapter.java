package com.example.baidoxee;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class XeraAdapter extends RecyclerView.Adapter<XeraAdapter.LogViewHolder> {

    private List<xera> logList;

    public XeraAdapter(List<xera> logList) {
        this.logList = logList;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.car_item_row, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        xera log = logList.get(position);

        // Hiển thị biển số
        if (log.getVehicle() != null && !log.getVehicle().isEmpty()) {
            holder.tvPlate.setText(log.getVehicle());
        } else {
            holder.tvPlate.setText("Không rõ");
        }

        // Hiển thị giờ vào
        if (log.getTimeIn() != null && !log.getTimeIn().isEmpty()) {
            holder.tvTimeIn.setText(log.getTimeIn());
        } else {
            holder.tvTimeIn.setText("--:--");
        }

        // Hiển thị giờ ra
        if (log.getTimeOut() != null && !log.getTimeOut().isEmpty()) {
            holder.tvTimeOut.setText(log.getTimeOut());
        } else {
            holder.tvTimeOut.setText("--:--");
        }

        // Nếu có hiển thị ảnh thì xử lý thêm ở đây (nếu ảnh là URL hoặc byte array)
        // Example: holder.imagePlate.setImageBitmap(...) hoặc Glide/Picasso
    }

    @Override
    public int getItemCount() {
        return logList != null ? logList.size() : 0;
    }

    // Method để update data khi filter
    public void updateData(List<xera> filteredList) {
        this.logList = filteredList;
        notifyDataSetChanged();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView tvPlate, tvTimeIn, tvTimeOut;
        ImageView imagePlate;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPlate = itemView.findViewById(R.id.textPlate);
            tvTimeIn = itemView.findViewById(R.id.textTimeIn);
            tvTimeOut = itemView.findViewById(R.id.textTimeOut);
            imagePlate = itemView.findViewById(R.id.imagePlate);
        }
    }
}
