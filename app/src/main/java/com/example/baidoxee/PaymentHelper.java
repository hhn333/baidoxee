package com.example.baidoxee;

import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PaymentHelper {
    private ThanhToanActivity activity;

    public PaymentHelper(ThanhToanActivity activity) {
        this.activity = activity;
    }

    public static class PaymentData {
        public String activityId, bienSoXe, thoiGianVao, thoiGianRa, vehicleId, vehicleType, hinhThucThanhToan;
        public long giaVe;
        public boolean needsCheckoutUpdate;
    }

    public PaymentData processPaymentData(JSONObject data) throws JSONException {
        PaymentData paymentData = new PaymentData();

        paymentData.activityId = data.getString("id");

        // Xử lý biển số xe
        if (data.has("plateNumber") && !data.isNull("plateNumber") && !data.getString("plateNumber").trim().isEmpty()) {
            paymentData.bienSoXe = data.getString("plateNumber");
        } else if (data.has("plate_text") && !data.isNull("plate_text") && !data.getString("plate_text").trim().isEmpty()) {
            paymentData.bienSoXe = data.getString("plate_text");
        } else if (data.has("bienSoXe") && !data.isNull("bienSoXe") && !data.getString("bienSoXe").trim().isEmpty()) {
            paymentData.bienSoXe = data.getString("bienSoXe");
        } else {
            paymentData.bienSoXe = null;
        }

        // Lấy thời gian vào
        if (data.has("timeIn") && !data.isNull("timeIn")) {
            if (data.get("timeIn") instanceof JSONObject) {
                JSONObject timeInObj = data.getJSONObject("timeIn");
                paymentData.thoiGianVao = timeInObj.has("$date") ? timeInObj.getString("$date") : timeInObj.toString();
            } else {
                paymentData.thoiGianVao = data.getString("timeIn");
            }
        } else if (data.has("thoiGianVao") && !data.isNull("thoiGianVao")) {
            paymentData.thoiGianVao = data.getString("thoiGianVao");
        }

        // Lấy vehicle ID và type
        if (data.has("vehicle_id") && !data.isNull("vehicle_id")) {
            paymentData.vehicleId = data.getString("vehicle_id");
        } else if (data.has("vehicleId") && !data.isNull("vehicleId")) {
            paymentData.vehicleId = data.getString("vehicleId");
        }

        paymentData.vehicleType = data.has("vehicleType") && !data.isNull("vehicleType")
                ? data.getString("vehicleType") : "CAR_UNDER_9";

        // Kiểm tra thời gian ra
        if (data.has("timeOut") && !data.isNull("timeOut")) {
            if (data.get("timeOut") instanceof JSONObject) {
                JSONObject timeOutObj = data.getJSONObject("timeOut");
                paymentData.thoiGianRa = timeOutObj.has("$date") ? timeOutObj.getString("$date") : timeOutObj.toString();
            } else {
                paymentData.thoiGianRa = data.getString("timeOut");
            }
            paymentData.needsCheckoutUpdate = false;
        } else if (data.has("thoiGianRa") && !data.isNull("thoiGianRa")) {
            paymentData.thoiGianRa = data.getString("thoiGianRa");
            paymentData.needsCheckoutUpdate = false;
        } else {
            paymentData.thoiGianRa = getCurrentTime();
            paymentData.needsCheckoutUpdate = true;
        }

        // Hình thức thanh toán
        paymentData.hinhThucThanhToan = data.has("hinhThucThanhToan") && !data.isNull("hinhThucThanhToan")
                ? data.getString("hinhThucThanhToan") : "tien_mat";

        // Lấy giá vé từ database (không tính toán)
        if (data.has("fee") && !data.isNull("fee")) {
            paymentData.giaVe = data.getLong("fee");
        } else if (data.has("giaVe") && !data.isNull("giaVe")) {
            paymentData.giaVe = data.getLong("giaVe");
        } else {
            paymentData.giaVe = 0; // Sẽ được cập nhật từ server
        }

        return paymentData;
    }

    public void updatePaymentMethod(String activityId, String vehicleId, String vehicleType, String phuongThuc) {
        if (activityId == null || activityId.isEmpty()) return;

        try {
            JSONObject updateData = new JSONObject();
            updateData.put("hinhThucThanhToan", phuongThuc);
            updateData.put("thoiGianChonPhuongThuc", getCurrentTime());

            if (vehicleId != null && !vehicleId.isEmpty()) {
                updateData.put("vehicle_id", vehicleId);
            }
            if (vehicleType != null && !vehicleType.isEmpty()) {
                updateData.put("vehicleType", vehicleType);
            }

            ApiHelper.updateParkingLogPayment(activityId, updateData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    // Không hiển thị thông báo khi chọn hình thức thanh toán
                }

                @Override
                public void onError(String errorMessage) {
                    activity.runOnUiThread(() -> Toast.makeText(activity,
                            "Lỗi cập nhật hình thức thanh toán: " + errorMessage, Toast.LENGTH_SHORT).show());
                }
            });
        } catch (JSONException e) {
            Toast.makeText(activity, "Lỗi tạo dữ liệu cập nhật", Toast.LENGTH_SHORT).show();
        }
    }

    public void updateVehicleCheckout(String activityId, String thoiGianRa, long giaVe, String vehicleId, String vehicleType) {
        try {
            JSONObject updateData = new JSONObject();
            updateData.put("timeOut", thoiGianRa);
            updateData.put("status", "COMPLETED");
            updateData.put("fee", giaVe);

            if (vehicleId != null && !vehicleId.isEmpty()) {
                updateData.put("vehicle_id", vehicleId);
            }
            if (vehicleType != null && !vehicleType.isEmpty()) {
                updateData.put("vehicleType", vehicleType);
            }

            ApiHelper.updateParkingLog(activityId, updateData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {}

                @Override
                public void onError(String errorMessage) {
                    activity.runOnUiThread(() -> Toast.makeText(activity,
                            "Lỗi cập nhật dữ liệu: " + errorMessage, Toast.LENGTH_SHORT).show());
                }
            });
        } catch (JSONException e) {}
    }

    public void processPaymentAndPrint(String activityId, String bienSoXe, String thoiGianVao,
                                       String thoiGianRa, long giaVe, String hinhThucThanhToan,
                                       String vehicleId, String vehicleType) {
        String paymentMethodText = hinhThucThanhToan.equals("tien_mat") ? "Tiền mặt" : "Chuyển khoản";

        try {
            JSONObject updatePaymentData = new JSONObject();
            updatePaymentData.put("trangThaiThanhToan", "da_thanh_toan");
            updatePaymentData.put("thoiGianThanhToan", getCurrentTime());
            updatePaymentData.put("hinhThucThanhToan", hinhThucThanhToan);
            updatePaymentData.put("fee", giaVe);

            if (vehicleId != null && !vehicleId.isEmpty()) {
                updatePaymentData.put("vehicle_id", vehicleId);
            }
            if (vehicleType != null && !vehicleType.isEmpty()) {
                updatePaymentData.put("vehicleType", vehicleType);
            }

            activity.setButtonProcessing();

            ApiHelper.updateParkingLog(activityId, updatePaymentData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    sendPrintCommand(activityId, bienSoXe, thoiGianVao, thoiGianRa,
                            giaVe, vehicleId, vehicleType, paymentMethodText);
                }

                @Override
                public void onError(String errorMessage) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "Lỗi cập nhật trạng thái thanh toán: " + errorMessage, Toast.LENGTH_LONG).show();
                        activity.resetButton();
                    });
                }
            });
        } catch (JSONException e) {
            Toast.makeText(activity, "Lỗi tạo dữ liệu thanh toán", Toast.LENGTH_SHORT).show();
            activity.resetButton();
        }
    }

    private void sendPrintCommand(String activityId, String bienSoXe, String thoiGianVao,
                                  String thoiGianRa, long giaVe, String vehicleId,
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

            if (vehicleId != null && !vehicleId.isEmpty()) {
                printData.put("vehicle_id", vehicleId);
            }
            if (vehicleType != null && !vehicleType.isEmpty()) {
                printData.put("vehicleType", vehicleType);
            }

            ApiHelper.sendPrintCommand(printData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "Thanh toán thành công! Đã gửi lệnh in hóa đơn!", Toast.LENGTH_LONG).show();
                        activity.showSuccessDialog(bienSoXe, thoiGianVao, thoiGianRa, giaVe, paymentMethodText);
                        activity.resetButton();
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "Thanh toán thành công nhưng lỗi in hóa đơn: " + errorMessage, Toast.LENGTH_LONG).show();
                        activity.showSuccessDialog(bienSoXe, thoiGianVao, thoiGianRa, giaVe, paymentMethodText);
                        activity.resetButton();
                    });
                }
            });
        } catch (JSONException e) {
            activity.runOnUiThread(() -> {
                Toast.makeText(activity, "Thanh toán thành công nhưng lỗi tạo dữ liệu in", Toast.LENGTH_SHORT).show();
                activity.resetButton();
            });
        }
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

    private String getDisplayVehicleType(String vehicleType) {
        return vehicleType.equals("CAR_9_TO_16") ? "Ô tô 9-16 chỗ" : "Ô tô dưới 9 chỗ";
    }
}