package com.example.baidoxee;

import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AlignmentSpan;
import android.view.Gravity;
import android.widget.TextView;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class InvoiceHelper {
    private ThanhToanActivity activity;

    public InvoiceHelper(ThanhToanActivity activity) {
        this.activity = activity;
    }

    public void displayInvoiceInfo(TextView tvBienSoXe, TextView tvThoiGianVao, TextView tvThoiGianRa,
                                   TextView tvGiaVe, String bienSoXe, String vehicleType,
                                   String thoiGianVao, String thoiGianRa, long giaVe) {

        if (tvBienSoXe != null) {
            setFormattedLicensePlate(tvBienSoXe, bienSoXe);
        }

        if (tvThoiGianVao != null) {
            tvThoiGianVao.setText("⏰ Thời gian vào: " + formatDisplayTime(thoiGianVao));
        }

        if (tvThoiGianRa != null) {
            tvThoiGianRa.setText("🚪 Thời gian ra: " + formatDisplayTime(thoiGianRa));
        }

        if (tvGiaVe != null) {
            NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
            tvGiaVe.setText("💰 Giá vé: " + formatter.format(giaVe) + "đ");
        }
    }

    private void setFormattedLicensePlate(TextView textView, String bienSoXe) {
        String plateText = bienSoXe != null ? bienSoXe : "N/A";
        String fullText = "🚗 Biển số xe:\n" + plateText;

        SpannableString spannableString = new SpannableString(fullText);

        int startIndex = fullText.indexOf('\n') + 1;
        if (startIndex < fullText.length()) {
            spannableString.setSpan(
                    new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                    startIndex, fullText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        textView.setText(spannableString);
        textView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    }

    public void showSuccessDialog(String bienSoXe, String thoiGianVao, String thoiGianRa,
                                  long giaVe, String paymentMethodText) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(activity);

        NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
        String dialogLicensePlate = bienSoXe != null ? bienSoXe.replace("\n", " ") : "N/A";

        builder.setTitle("✅ Thanh toán thành công")
                .setMessage("Cảm ơn quý khách đã sử dụng dịch vụ!\n\n" +
                        "════════════════════════\n" +
                        "📋 THÔNG TIN HÓA ĐƠN\n" +
                        "════════════════════════\n" +
                        "🚗 Biển số: " + dialogLicensePlate + "\n" +
                        "⏰ Vào lúc: " + formatDisplayTime(thoiGianVao) + "\n" +
                        "🚪 Ra lúc: " + formatDisplayTime(thoiGianRa) + "\n" +
                        "💰 Số tiền: " + formatter.format(giaVe) + " đ\n" +
                        "💳 Hình thức: " + paymentMethodText + "\n" +
                        "════════════════════════\n\n" )
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
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
            try {
                SimpleDateFormat inputFormat2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
                Date date = inputFormat2.parse(timeString.replace("Z", ""));
                return outputFormat.format(date);
            } catch (Exception e2) {
                return timeString;
            }
        }
    }
}