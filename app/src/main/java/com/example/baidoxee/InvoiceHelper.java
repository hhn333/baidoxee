package com.example.baidoxee;

import android.util.Log;
import android.widget.TextView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class InvoiceHelper {
    private static final String TAG = "InvoiceHelper";
    private ThanhToanActivity activity;

    public InvoiceHelper(ThanhToanActivity activity) {
        this.activity = activity;
    }

    public void displayInvoiceInfo(TextView tvBienSoXe, TextView tvThoiGianVao,
                                   TextView tvThoiGianRa, TextView tvGiaVe, String bienSoXe,
                                   String vehicleType, String thoiGianVao, String thoiGianRa, long giaVe) {

        // Hiển thị biển số xe với emoji
        if (tvBienSoXe != null) {
            tvBienSoXe.setText("🚗 Biển số xe: " + bienSoXe);
            Log.d(TAG, "Displaying license plate: " + bienSoXe);
        }

        // Hiển thị thời gian vào
        if (tvThoiGianVao != null) {
            String formattedTimeIn = formatDisplayTime(thoiGianVao);
            tvThoiGianVao.setText("⏰ Thời gian vào: " + formattedTimeIn);
            Log.d(TAG, "Displaying time in: " + formattedTimeIn);
        }

        // Hiển thị thời gian ra
        if (tvThoiGianRa != null) {
            String formattedTimeOut = formatDisplayTime(thoiGianRa);
            tvThoiGianRa.setText("🚪 Thời gian ra: " + formattedTimeOut);
            Log.d(TAG, "Displaying time out: " + formattedTimeOut);
        }

        // Hiển thị giá vé với định dạng tiền tệ Việt Nam
        if (tvGiaVe != null) {
            NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
            String formattedPrice = formatter.format(giaVe) + "đ";
            tvGiaVe.setText("💰 Giá vé: " + formattedPrice);
            Log.d(TAG, "Displaying price: " + formattedPrice);
        }
    }

    public void showSuccessDialog(String bienSoXe, String thoiGianVao, String thoiGianRa,
                                  long giaVe, String paymentMethodText) {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(activity);

        NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));

        builder.setTitle("✅ Thanh toán thành công")
                .setMessage("Cảm ơn quý khách đã sử dụng dịch vụ!\n\n" +
                        "════════════════════════\n" +
                        "📋 THÔNG TIN HÓA ĐƠN\n" +
                        "════════════════════════\n" +
                        "🚗 Biển số: " + bienSoXe + "\n" +
                        "⏰ Vào lúc: " + formatDisplayTime(thoiGianVao) + "\n" +
                        "🚪 Ra lúc: " + formatDisplayTime(thoiGianRa) + "\n" +
                        "💰 Số tiền: " + formatter.format(giaVe) + " đ\n" +
                        "💳 Hình thức: " + paymentMethodText + "\n" +
                        "════════════════════════\n\n" )
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    // Reset dữ liệu và hiển thị thông báo không có xe
                    activity.resetActivityData();
                })
                .setCancelable(false)
                .show();
    }

    private String formatDisplayTime(String timeString) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
            Date date = inputFormat.parse(timeString);
            return outputFormat.format(date);
        } catch (Exception e) {
            Log.e(TAG, "Error formatting time: " + timeString, e);
            // Thử format khác nếu format đầu không thành công
            try {
                SimpleDateFormat inputFormat2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
                Date date = inputFormat2.parse(timeString.replace("Z", ""));
                return outputFormat.format(date);
            } catch (Exception e2) {
                Log.e(TAG, "Error formatting time with alternative format: " + timeString, e2);
                return timeString;
            }
        }
    }

    private String getDisplayVehicleType(String vehicleType) {
        return null;
    }

    public void displayInvoiceInfoFromEvents(TextView tvBienSoXe, TextView tvThoiGianVao, TextView tvThoiGianRa, TextView tvGiaVe, String bienSoXe, String vehicleType, String thoiGianVao, String thoiGianRa, long giaVe, String eventEnterId, String eventExitId, boolean hasParkingLog) {
    }

    public void showSuccessDialogFromEvents(String bienSoXe, String thoiGianVao, String thoiGianRa, long giaVe, String paymentMethodText, String eventEnterId, String eventExitId) {
    }
}