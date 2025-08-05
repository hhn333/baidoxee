package com.example.baidoxee;

import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class PaymentHelper {
    private static final String TAG = "PaymentHelper";
    private ThanhToanActivity activity;

    public PaymentHelper(ThanhToanActivity activity) {
        this.activity = activity;
    }

    public static class PaymentData {
        public String activityId;
        public String bienSoXe;
        public String thoiGianVao;
        public String thoiGianRa;
        public long giaVe;
        public String eventEnterId;    // ID của event vào
        public String eventExitId;     // ID của event ra
        public String vehicleType;
        public String hinhThucThanhToan;
        public boolean needsCheckoutUpdate;
    }

    public PaymentData processPaymentData(JSONObject data) throws JSONException {
        PaymentData paymentData = new PaymentData();

        // Lấy thông tin từ response
        paymentData.activityId = data.getString("_id");
        paymentData.bienSoXe = data.getString("bienSoXe");
        paymentData.thoiGianVao = data.getString("thoiGianVao");

        // Lấy event IDs nếu có
        if (data.has("event_enter_id") && !data.isNull("event_enter_id")) {
            paymentData.eventEnterId = data.getString("event_enter_id");
        }
        if (data.has("event_exit_id") && !data.isNull("event_exit_id")) {
            paymentData.eventExitId = data.getString("event_exit_id");
        }

        // Lấy vehicle type
        if (data.has("vehicleType") && !data.isNull("vehicleType")) {
            paymentData.vehicleType = data.getString("vehicleType");
        } else {
            paymentData.vehicleType = "CAR_UNDER_9";
        }

        // Kiểm tra xem có thời gian ra chưa
        if (data.has("thoiGianRa") && !data.isNull("thoiGianRa")) {
            paymentData.thoiGianRa = data.getString("thoiGianRa");
            paymentData.needsCheckoutUpdate = false;
        } else {
            // Nếu chưa có thời gian ra, set thời gian hiện tại
            paymentData.thoiGianRa = getCurrentTime();
            paymentData.needsCheckoutUpdate = true;
        }

        // Kiểm tra xem có hình thức thanh toán đã được chọn chưa
        if (data.has("hinhThucThanhToan") && !data.isNull("hinhThucThanhToan")) {
            paymentData.hinhThucThanhToan = data.getString("hinhThucThanhToan");
        } else {
            paymentData.hinhThucThanhToan = "tien_mat";
        }

        // Kiểm tra giá vé từ database trước
        if (data.has("giaVe") && !data.isNull("giaVe")) {
            paymentData.giaVe = data.getLong("giaVe");
        } else {
            // Tính giá vé dựa trên thời gian đậu nếu chưa có
            paymentData.giaVe = calculateParkingFee(paymentData.thoiGianVao,
                    paymentData.thoiGianRa,
                    paymentData.vehicleType);
        }

        return paymentData;
    }

    public void updatePaymentMethod(String activityId, String eventEnterId, String vehicleType, String phuongThuc) {
        if (activityId == null || activityId.isEmpty()) {
            Log.w(TAG, "ActivityId is null, cannot update payment method");
            return;
        }

        try {
            JSONObject updateData = new JSONObject();
            updateData.put("hinhThucThanhToan", phuongThuc);
            updateData.put("thoiGianChonPhuongThuc", getCurrentTime());

            // Thêm event IDs và vehicle type nếu có
            if (eventEnterId != null && !eventEnterId.isEmpty()) {
                updateData.put("event_enter_id", eventEnterId);
            }
            if (vehicleType != null && !vehicleType.isEmpty()) {
                updateData.put("vehicleType", vehicleType);
            }

            Log.d(TAG, "Updating payment method to: " + phuongThuc);

            ApiHelper.updateActivity(activityId, updateData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Payment method updated successfully: " + phuongThuc);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String displayText = phuongThuc.equals("tien_mat") ? "Tiền mặt" : "Chuyển khoản";
                            Toast.makeText(activity,
                                    "Đã chọn hình thức: " + displayText,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Failed to update payment method: " + errorMessage);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity,
                                    "Lỗi cập nhật hình thức thanh toán: " + errorMessage,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "Error creating payment method update JSON", e);
            Toast.makeText(activity, "Lỗi tạo dữ liệu cập nhật", Toast.LENGTH_SHORT).show();
        }
    }

    public void updateVehicleCheckout(String activityId, String thoiGianRa, long giaVe,
                                      String eventEnterId, String vehicleType) {
        try {
            JSONObject updateData = new JSONObject();
            updateData.put("thoiGianRa", thoiGianRa);
            updateData.put("trangThai", "da_ra");
            updateData.put("giaVe", giaVe);

            // Thêm event IDs và type nếu có
            if (eventEnterId != null && !eventEnterId.isEmpty()) {
                updateData.put("event_enter_id", eventEnterId);
            }
            if (vehicleType != null && !vehicleType.isEmpty()) {
                updateData.put("vehicleType", vehicleType);
            }

            ApiHelper.updateActivity(activityId, updateData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Updated vehicle checkout successfully");
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Failed to update vehicle checkout: " + errorMessage);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity,
                                    "Lỗi cập nhật dữ liệu: " + errorMessage,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "Error creating update JSON", e);
        }
    }

    public void processPaymentAndPrint(String activityId, String bienSoXe, String thoiGianVao,
                                       String thoiGianRa, long giaVe, String hinhThucThanhToan,
                                       String eventEnterId, String vehicleType) {
        // Kiểm tra hình thức thanh toán đã được chọn
        String paymentMethodText = hinhThucThanhToan.equals("tien_mat") ? "Tiền mặt" : "Chuyển khoản";

        try {
            // Cập nhật trạng thái thanh toán trước khi in
            JSONObject updatePaymentData = new JSONObject();
            updatePaymentData.put("trangThaiThanhToan", "da_thanh_toan");
            updatePaymentData.put("thoiGianThanhToan", getCurrentTime());
            updatePaymentData.put("hinhThucThanhToan", hinhThucThanhToan);
            updatePaymentData.put("giaVe", giaVe);

            // Thêm event IDs và type nếu có
            if (eventEnterId != null && !eventEnterId.isEmpty()) {
                updatePaymentData.put("event_enter_id", eventEnterId);
            }
            if (vehicleType != null && !vehicleType.isEmpty()) {
                updatePaymentData.put("vehicleType", vehicleType);
            }

            // Disable button để tránh spam
            activity.setButtonProcessing();

            Log.d(TAG, "Processing payment and printing invoice...");

            ApiHelper.updateActivity(activityId, updatePaymentData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Payment status updated successfully");
                    // Sau khi cập nhật thành công, tiến hành in hóa đơn
                    sendPrintCommand(activityId, bienSoXe, thoiGianVao, thoiGianRa,
                            giaVe, eventEnterId, vehicleType, paymentMethodText);
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Failed to update payment status: " + errorMessage);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity,
                                    "Lỗi cập nhật trạng thái thanh toán: " + errorMessage,
                                    Toast.LENGTH_LONG).show();

                            // Reset button
                            activity.resetButton();
                        }
                    });
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "Error creating payment update JSON", e);
            Toast.makeText(activity, "Lỗi tạo dữ liệu thanh toán", Toast.LENGTH_SHORT).show();
            activity.resetButton();
        }
    }

    private void sendPrintCommand(String activityId, String bienSoXe, String thoiGianVao,
                                  String thoiGianRa, long giaVe, String eventEnterId,
                                  String vehicleType, String paymentMethodText) {
        try {
            JSONObject printData = new JSONObject();
            printData.put("bienSoXe", bienSoXe);
            printData.put("loaiXe", getDisplayVehicleType(vehicleType));
            printData.put("thoiGianVao", formatDisplayTime(thoiGianVao));
            printData.put("thoiGianRa", formatDisplayTime(thoiGianRa));
            printData.put("giaVe", giaVe);
            printData.put("hinhThucThanhToan", paymentMethodText);
            printData.put("activityId", activityId);
            printData.put("timestamp", System.currentTimeMillis());

            // Thêm event IDs và type nếu có
            if (eventEnterId != null && !eventEnterId.isEmpty()) {
                printData.put("eventEnterId", eventEnterId);
            }
            if (vehicleType != null && !vehicleType.isEmpty()) {
                printData.put("vehicleType", vehicleType);
            }

            // Thêm source để biết dữ liệu từ events
            printData.put("dataSource", "events_collection");

            Log.d(TAG, "Sending print command: " + printData.toString());

            ApiHelper.sendPrintCommand(printData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Print command sent successfully");
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity,
                                    "Thanh toán thành công! Đã gửi lệnh in hóa đơn!",
                                    Toast.LENGTH_LONG).show();

                            activity.showSuccessDialog(bienSoXe, thoiGianVao, thoiGianRa, giaVe, paymentMethodText);
                            activity.resetButton();
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Failed to send print command: " + errorMessage);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Thanh toán đã thành công nhưng in thất bại
                            Toast.makeText(activity,
                                    "Thanh toán thành công nhưng lỗi in hóa đơn: " + errorMessage,
                                    Toast.LENGTH_LONG).show();

                            activity.showSuccessDialog(bienSoXe, thoiGianVao, thoiGianRa, giaVe, paymentMethodText);
                            activity.resetButton();
                        }
                    });
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "Error creating print JSON", e);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity,
                            "Thanh toán thành công nhưng lỗi tạo dữ liệu in",
                            Toast.LENGTH_SHORT).show();
                    activity.resetButton();
                }
            });
        }
    }

    private long calculateParkingFee(String thoiGianVao, String thoiGianRa, String vehicleType) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            Date dateVao = sdf.parse(thoiGianVao);
            Date dateRa = sdf.parse(thoiGianRa);

            if (dateVao != null && dateRa != null) {
                long diffInMillis = dateRa.getTime() - dateVao.getTime();
                long diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis);

                // Quy tắc tính giá dựa theo loại xe
                long basePrice = 3000; // Mặc định cho xe dưới 9 chỗ
                long blockPrice = 2000;

                switch (vehicleType) {
                    case "CAR_9_TO_16":
                        basePrice = 5000;
                        blockPrice = 3000;
                        break;
                    case "MOTORCYCLE":
                        basePrice = 2000;
                        blockPrice = 1000;
                        break;
                    case "TRUCK":
                    case "BUS":
                        basePrice = 8000;
                        blockPrice = 5000;
                        break;
                }

                long gia = basePrice; // Giá cơ bản 30 phút đầu

                if (diffInMinutes > 30) {
                    long extraBlocks = (diffInMinutes - 30 + 29) / 30; // Làm tròn lên
                    gia += extraBlocks * blockPrice;
                }

                Log.d(TAG, "Calculated price: " + gia + " for " + diffInMinutes + " minutes, vehicle type: " + vehicleType);
                return gia;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating price", e);
        }

        return 3000; // Giá mặc định
    }

    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
        return sdf.format(new Date());
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
        switch (vehicleType) {
            case "CAR_UNDER_9":
                return "Ô tô dưới 9 chỗ";
            case "CAR_9_TO_16":
                return "Ô tô 9-16 chỗ";
            case "MOTORCYCLE":
                return "Xe máy";
            case "TRUCK":
                return "Xe tải";
            case "BUS":
                return "Xe buýt";
            default:
                return "Ô tô dưới 9 chỗ";
        }
    }
}