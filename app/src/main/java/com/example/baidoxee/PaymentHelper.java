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
        public String activityId, bienSoXe, thoiGianVao, thoiGianRa, eventEnterId, eventExitId, vehicleType, hinhThucThanhToan;
        public long giaVe;
        public boolean needsCheckoutUpdate;
    }

    public static class EventsPaymentData {
        public String activityId, bienSoXe, thoiGianVao, thoiGianRa, eventEnterId, eventExitId;
        public String vehicleType, hinhThucThanhToan, parkingLogId, cameraId, spotName, locationName;
        public String enterEventTimestamp, exitEventTimestamp;
        public long giaVe;
        public double plateConfidence;
        public boolean hasParkingLog, needsUpdateParkingLog;
    }

    public PaymentData processPaymentData(JSONObject data) throws JSONException {
        PaymentData pd = new PaymentData();
        pd.activityId = data.getString("_id");
        pd.bienSoXe = data.getString("bienSoXe");
        pd.thoiGianVao = data.getString("thoiGianVao");
        pd.eventEnterId = data.optString("event_enter_id", null);
        pd.eventExitId = data.optString("event_exit_id", null);
        pd.vehicleType = data.optString("vehicleType", "CAR_UNDER_9");
        pd.hinhThucThanhToan = data.optString("hinhThucThanhToan", "tien_mat");

        if (data.has("thoiGianRa") && !data.isNull("thoiGianRa")) {
            pd.thoiGianRa = data.getString("thoiGianRa");
            pd.needsCheckoutUpdate = false;
        } else {
            pd.thoiGianRa = getCurrentTime();
            pd.needsCheckoutUpdate = true;
        }

        pd.giaVe = data.has("giaVe") && !data.isNull("giaVe") ?
                data.getLong("giaVe") :
                calculateParkingFee(pd.thoiGianVao, pd.thoiGianRa, pd.vehicleType);

        return pd;
    }

    public EventsPaymentData processEventsPaymentData(JSONObject data) throws JSONException {
        EventsPaymentData epd = new EventsPaymentData();

        // Xử lý enter event
        if (data.has("enter_event") && !data.isNull("enter_event")) {
            JSONObject enterEvent = data.getJSONObject("enter_event");
            epd.bienSoXe = enterEvent.optString("plate_text");
            epd.enterEventTimestamp = enterEvent.optString("timestamp");
            epd.thoiGianVao = convertEventTimestamp(epd.enterEventTimestamp);
            epd.eventEnterId = enterEvent.optString("_id");
            epd.cameraId = enterEvent.optString("camera_id");
            epd.spotName = enterEvent.optString("spot_name");
            epd.locationName = enterEvent.optString("location_name");
            epd.plateConfidence = enterEvent.optDouble("plate_confidence", 0.0);
        } else {
            epd.bienSoXe = data.optString("plate_text", data.optString("bienSoXe"));
            epd.enterEventTimestamp = data.optString("enter_timestamp");
            epd.thoiGianVao = data.optString("thoiGianVao", convertEventTimestamp(epd.enterEventTimestamp));
        }

        // Xử lý exit event
        if (data.has("exit_event") && !data.isNull("exit_event")) {
            JSONObject exitEvent = data.getJSONObject("exit_event");
            epd.exitEventTimestamp = exitEvent.optString("timestamp");
            epd.thoiGianRa = convertEventTimestamp(epd.exitEventTimestamp);
            epd.eventExitId = exitEvent.optString("_id");
        } else {
            epd.exitEventTimestamp = data.optString("exit_timestamp");
            epd.thoiGianRa = data.optString("thoiGianRa",
                    epd.exitEventTimestamp != null ? convertEventTimestamp(epd.exitEventTimestamp) : getCurrentTime());
            if (epd.thoiGianRa.equals(getCurrentTime())) epd.needsUpdateParkingLog = true;
        }

        epd.eventEnterId = data.optString("event_enter_id", epd.eventEnterId);
        epd.eventExitId = data.optString("event_exit_id", epd.eventExitId);
        epd.parkingLogId = data.optString("parking_log_id");
        epd.hasParkingLog = data.optBoolean("has_parking_log", !epd.parkingLogId.isEmpty());
        epd.activityId = epd.hasParkingLog && !epd.parkingLogId.isEmpty() ? epd.parkingLogId : epd.eventEnterId;
        epd.vehicleType = data.optString("vehicleType", data.optString("vehicle_type", detectVehicleTypeFromPlate(epd.bienSoXe)));
        epd.hinhThucThanhToan = data.optString("hinhThucThanhToan", data.optString("payment_method", "tien_mat"));
        epd.giaVe = data.optLong("giaVe", data.optLong("fee",
                calculateParkingFeeFromEventTimestamps(epd.enterEventTimestamp, epd.exitEventTimestamp, epd.vehicleType)));

        if (!epd.hasParkingLog) epd.needsUpdateParkingLog = true;

        return epd;
    }

    private String convertEventTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return getCurrentTime();

        try {
            if (timestamp.contains("T")) return timestamp.endsWith("Z") ? timestamp : timestamp + "Z";

            if (timestamp.matches("\\d+")) {
                long ts = Long.parseLong(timestamp);
                if (timestamp.length() == 10) ts *= 1000;
                return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(new Date(ts));
            }

            SimpleDateFormat[] formats = {
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
                    new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()),
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            };

            for (SimpleDateFormat format : formats) {
                try {
                    Date date = format.parse(timestamp);
                    return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(date);
                } catch (Exception ignored) {}
            }
            return timestamp;
        } catch (Exception e) {
            Log.e(TAG, "Error converting timestamp: " + timestamp, e);
            return getCurrentTime();
        }
    }

    private long calculateParkingFeeFromEventTimestamps(String enterTs, String exitTs, String vehicleType) {
        if (enterTs == null || enterTs.isEmpty()) return getDefaultFeeByVehicleType(vehicleType);
        String timeOut = exitTs != null && !exitTs.isEmpty() ? exitTs : getCurrentTime();

        try {
            return calculateParkingFee(convertEventTimestamp(enterTs), convertEventTimestamp(timeOut), vehicleType);
        } catch (Exception e) {
            return getDefaultFeeByVehicleType(vehicleType);
        }
    }

    private String detectVehicleTypeFromPlate(String plate) {
        if (plate == null || plate.isEmpty()) return "CAR_UNDER_9";
        plate = plate.toUpperCase().trim();

        if (plate.matches("\\d{2}[A-Z]\\d-\\d{4,5}") || plate.matches("\\d{2}[A-Z]\\d{4,5}")) return "MOTORCYCLE";
        if (plate.matches("\\d{2}[A-Z]-\\d{3}\\.\\d{2}") || plate.contains("LD") || plate.contains("MD")) return "TRUCK";
        return "CAR_UNDER_9";
    }

    private long getDefaultFeeByVehicleType(String vehicleType) {
        switch (vehicleType) {
            case "CAR_9_TO_16": return 5000;
            case "MOTORCYCLE": return 2000;
            case "TRUCK":
            case "BUS": return 8000;
            default: return 3000;
        }
    }

    public void updatePaymentMethodFromEvents(String activityId, String eventEnterId, String eventExitId,
                                              String vehicleType, String parkingLogId, String phuongThuc, boolean hasParkingLog) {
        if (eventEnterId == null || eventEnterId.isEmpty()) return;

        try {
            JSONObject updateData = new JSONObject();
            updateData.put("hinhThucThanhToan", phuongThuc);
            updateData.put("thoiGianChonPhuongThuc", getCurrentTime());
            updateData.put("event_enter_id", eventEnterId);
            if (eventExitId != null) updateData.put("event_exit_id", eventExitId);
            if (vehicleType != null) updateData.put("vehicleType", vehicleType);
            updateData.put("dataSource", "events_collection");
            updateData.put("hasParkingLog", hasParkingLog);

            String updateId = hasParkingLog && parkingLogId != null ? parkingLogId : activityId;

            ApiHelper.updateActivity(updateId, updateData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    activity.runOnUiThread(() -> {
                        String text = phuongThuc.equals("tien_mat") ? "Tiền mặt" : "Chuyển khoản";
                        Toast.makeText(activity, "✅ Đã chọn: " + text + " (Events Data)", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String error) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "Lỗi cập nhật: " + error, Toast.LENGTH_SHORT).show());
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error updating payment method", e);
        }
    }

    public void updateOrCreateParkingLogFromEvents(String eventEnterId, String eventExitId,
                                                   String thoiGianVao, String thoiGianRa, long giaVe,
                                                   String vehicleType, String parkingLogId, boolean hasParkingLog) {
        try {
            JSONObject updateData = new JSONObject();
            updateData.put("event_enter_id", eventEnterId);
            if (eventExitId != null) updateData.put("event_exit_id", eventExitId);
            updateData.put("timeIn", thoiGianVao);
            if (thoiGianRa != null) {
                updateData.put("timeOut", thoiGianRa);
                updateData.put("status", "COMPLETED");
            } else {
                updateData.put("status", "IN_PROGRESS");
            }
            updateData.put("fee", giaVe);
            updateData.put("vehicleType", vehicleType);
            updateData.put("dataSource", "events_collection");
            updateData.put("lastUpdated", getCurrentTime());

            if (hasParkingLog && parkingLogId != null) {
                ApiHelper.updateActivity(parkingLogId, updateData.toString(), new ApiHelper.OnResponseListener() {
                    @Override
                    public void onSuccess(String response) {
                        Log.d(TAG, "Parking log updated from events");
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Failed to update parking log: " + error);
                    }
                });
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error updating parking log", e);
        }
    }

    public void processPaymentAndPrintFromEvents(String activityId, String bienSoXe, String thoiGianVao,
                                                 String thoiGianRa, long giaVe, String hinhThucThanhToan,
                                                 String eventEnterId, String eventExitId, String vehicleType,
                                                 String parkingLogId, boolean hasParkingLog) {
        String paymentMethodText = hinhThucThanhToan.equals("tien_mat") ? "Tiền mặt" : "Chuyển khoản";

        try {
            JSONObject updateData = new JSONObject();
            updateData.put("trangThaiThanhToan", "da_thanh_toan");
            updateData.put("thoiGianThanhToan", getCurrentTime());
            updateData.put("hinhThucThanhToan", hinhThucThanhToan);
            updateData.put("giaVe", giaVe);
            updateData.put("event_enter_id", eventEnterId);
            if (eventExitId != null) updateData.put("event_exit_id", eventExitId);
            if (vehicleType != null) updateData.put("vehicleType", vehicleType);
            updateData.put("dataSource", "events_collection");
            updateData.put("hasParkingLog", hasParkingLog);

            activity.setButtonProcessing();
            String updateId = hasParkingLog && parkingLogId != null ? parkingLogId : activityId;

            ApiHelper.updateActivity(updateId, updateData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    sendPrintCommandFromEvents(activityId, bienSoXe, thoiGianVao, thoiGianRa,
                            giaVe, eventEnterId, eventExitId, vehicleType, paymentMethodText, parkingLogId, hasParkingLog);
                }

                @Override
                public void onError(String error) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "Lỗi cập nhật thanh toán: " + error, Toast.LENGTH_LONG).show();
                        activity.resetButton();
                    });
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error processing payment", e);
            activity.resetButton();
        }
    }

    private void sendPrintCommandFromEvents(String activityId, String bienSoXe, String thoiGianVao,
                                            String thoiGianRa, long giaVe, String eventEnterId, String eventExitId,
                                            String vehicleType, String paymentMethodText, String parkingLogId, boolean hasParkingLog) {
        try {
            JSONObject printData = new JSONObject();
            printData.put("bienSoXe", bienSoXe);
            printData.put("loaiXe", getDisplayVehicleType(vehicleType));
            printData.put("thoiGianVao", formatDisplayTime(thoiGianVao));
            printData.put("thoiGianRa", formatDisplayTime(thoiGianRa));
            printData.put("giaVe", giaVe);
            printData.put("hinhThucThanhToan", paymentMethodText);
            printData.put("activityId", activityId);
            printData.put("eventEnterId", eventEnterId);
            if (eventExitId != null) printData.put("eventExitId", eventExitId);
            printData.put("vehicleType", vehicleType);
            if (hasParkingLog && parkingLogId != null) printData.put("parkingLogId", parkingLogId);
            printData.put("dataSource", "events_collection");
            printData.put("timestamp", System.currentTimeMillis());

            ApiHelper.sendPrintCommand(printData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "✅ Thanh toán thành công! (Events Data)", Toast.LENGTH_LONG).show();
                        activity.showSuccessDialogFromEvents(bienSoXe, thoiGianVao, thoiGianRa, giaVe, paymentMethodText, eventEnterId, eventExitId);
                        activity.resetButton();
                    });
                }

                @Override
                public void onError(String error) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "Thanh toán thành công nhưng lỗi in: " + error, Toast.LENGTH_LONG).show();
                        activity.showSuccessDialogFromEvents(bienSoXe, thoiGianVao, thoiGianRa, giaVe, paymentMethodText, eventEnterId, eventExitId);
                        activity.resetButton();
                    });
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error creating print data", e);
            activity.resetButton();
        }
    }

    // Legacy methods for parking_logs compatibility
    public void updatePaymentMethod(String activityId, String eventEnterId, String vehicleType, String phuongThuc) {
        if (activityId == null || activityId.isEmpty()) return;

        try {
            JSONObject updateData = new JSONObject();
            updateData.put("hinhThucThanhToan", phuongThuc);
            updateData.put("thoiGianChonPhuongThuc", getCurrentTime());
            if (eventEnterId != null) updateData.put("event_enter_id", eventEnterId);
            if (vehicleType != null) updateData.put("vehicleType", vehicleType);

            ApiHelper.updateActivity(activityId, updateData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    activity.runOnUiThread(() -> {
                        String text = phuongThuc.equals("tien_mat") ? "Tiền mặt" : "Chuyển khoản";
                        Toast.makeText(activity, "Đã chọn: " + text, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String error) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "Lỗi cập nhật: " + error, Toast.LENGTH_SHORT).show());
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error updating payment method", e);
        }
    }

    public void processPaymentAndPrint(String activityId, String bienSoXe, String thoiGianVao,
                                       String thoiGianRa, long giaVe, String hinhThucThanhToan,
                                       String eventEnterId, String vehicleType) {
        String paymentMethodText = hinhThucThanhToan.equals("tien_mat") ? "Tiền mặt" : "Chuyển khoản";

        try {
            JSONObject updateData = new JSONObject();
            updateData.put("trangThaiThanhToan", "da_thanh_toan");
            updateData.put("thoiGianThanhToan", getCurrentTime());
            updateData.put("hinhThucThanhToan", hinhThucThanhToan);
            updateData.put("giaVe", giaVe);
            if (eventEnterId != null) updateData.put("event_enter_id", eventEnterId);
            if (vehicleType != null) updateData.put("vehicleType", vehicleType);

            activity.setButtonProcessing();

            ApiHelper.updateActivity(activityId, updateData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    sendPrintCommand(activityId, bienSoXe, thoiGianVao, thoiGianRa, giaVe, eventEnterId, vehicleType, paymentMethodText);
                }

                @Override
                public void onError(String error) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "Lỗi cập nhật: " + error, Toast.LENGTH_LONG).show();
                        activity.resetButton();
                    });
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error processing payment", e);
            activity.resetButton();
        }
    }

    private void sendPrintCommand(String activityId, String bienSoXe, String thoiGianVao, String thoiGianRa,
                                  long giaVe, String eventEnterId, String vehicleType, String paymentMethodText) {
        try {
            JSONObject printData = new JSONObject();
            printData.put("bienSoXe", bienSoXe);
            printData.put("loaiXe", getDisplayVehicleType(vehicleType));
            printData.put("thoiGianVao", formatDisplayTime(thoiGianVao));
            printData.put("thoiGianRa", formatDisplayTime(thoiGianRa));
            printData.put("giaVe", giaVe);
            printData.put("hinhThucThanhToan", paymentMethodText);
            printData.put("activityId", activityId);
            if (eventEnterId != null) printData.put("eventEnterId", eventEnterId);
            if (vehicleType != null) printData.put("vehicleType", vehicleType);
            printData.put("dataSource", "parking_logs_collection");
            printData.put("timestamp", System.currentTimeMillis());

            ApiHelper.sendPrintCommand(printData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "Thanh toán thành công!", Toast.LENGTH_LONG).show();
                        activity.showSuccessDialog(bienSoXe, thoiGianVao, thoiGianRa, giaVe, paymentMethodText);
                        activity.resetButton();
                    });
                }

                @Override
                public void onError(String error) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "Thanh toán thành công nhưng lỗi in: " + error, Toast.LENGTH_LONG).show();
                        activity.showSuccessDialog(bienSoXe, thoiGianVao, thoiGianRa, giaVe, paymentMethodText);
                        activity.resetButton();
                    });
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error creating print data", e);
            activity.resetButton();
        }
    }

    private long calculateParkingFee(String thoiGianVao, String thoiGianRa, String vehicleType) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            Date dateVao = sdf.parse(thoiGianVao);
            Date dateRa = sdf.parse(thoiGianRa);

            if (dateVao != null && dateRa != null) {
                long diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(dateRa.getTime() - dateVao.getTime());
                if (diffInMinutes < 0) diffInMinutes = 0;

                long basePrice, blockPrice;
                switch (vehicleType) {
                    case "CAR_9_TO_16": basePrice = 5000; blockPrice = 3000; break;
                    case "MOTORCYCLE": basePrice = 2000; blockPrice = 1000; break;
                    case "TRUCK":
                    case "BUS": basePrice = 8000; blockPrice = 5000; break;
                    default: basePrice = 3000; blockPrice = 2000; break;
                }

                long gia = basePrice;
                if (diffInMinutes > 30) {
                    long extraBlocks = (diffInMinutes - 30 + 29) / 30;
                    gia += extraBlocks * blockPrice;
                }
                return gia;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating fee", e);
        }
        return getDefaultFeeByVehicleType(vehicleType);
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(new Date());
    }

    private String formatDisplayTime(String timeString) {
        if (timeString == null || timeString.isEmpty()) return "N/A";

        try {
            SimpleDateFormat[] inputFormats = {
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()),
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            };
            SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());

            for (SimpleDateFormat format : inputFormats) {
                try {
                    Date date = format.parse(timeString.replace("Z", ""));
                    return outputFormat.format(date);
                } catch (Exception ignored) {}
            }

            if (timeString.matches("\\d+")) {
                long timestamp = Long.parseLong(timeString);
                if (timeString.length() == 10) timestamp *= 1000;
                return outputFormat.format(new Date(timestamp));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error formatting time: " + timeString, e);
        }
        return timeString;
    }

    private String getDisplayVehicleType(String vehicleType) {
        if (vehicleType == null || vehicleType.isEmpty()) return "Ô tô dưới 9 chỗ";

        switch (vehicleType.toUpperCase()) {
            case "CAR_UNDER_9": return "Ô tô dưới 9 chỗ";
            case "CAR_9_TO_16": return "Ô tô 9-16 chỗ";
            default: return "Ô tô dưới 9 chỗ";
        }
    }
}