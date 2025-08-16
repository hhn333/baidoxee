package com.example.baidoxee;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PaymentHelper {

    private static final String TAG = "PaymentHelper";
    private Context context;
    private ThanhToanActivity activity;

    public PaymentHelper(Context context) {
        this.context = context;
        if (context instanceof ThanhToanActivity) {
            this.activity = (ThanhToanActivity) context;
        }
    }

    // PaymentData class for events and parkinglogs integration
    public static class PaymentData {
        public String parkingLogId;          // ID từ parkinglogs collection
        public String bienSoXe;              // plate_text từ events collection
        public String thoiGianVao;           // timeIn từ parkinglogs hoặc timestamp từ enter event
        public String thoiGianRa;            // timeOut từ parkinglogs hoặc timestamp từ exit event
        public long giaVe;                   // fee từ parkinglogs
        public String eventEnterId;         // ID của enter event
        public String eventExitId;          // ID của exit event
        public String vehicleId;            // vehicle_id liên kết giữa events và parkinglogs
        public String vehicleType;          // Loại xe
        public String hinhThucThanhToan;    // Payment method
        public String status;               // Status từ parkinglogs (IN_PROGRESS, COMPLETED, PAID)
        public boolean needsPaymentDisplay; // true khi cần hiển thị thanh toán
        public boolean needsCheckoutUpdate; // true khi cần cập nhật checkout

        public PaymentData() {
            this.parkingLogId = "";
            this.bienSoXe = "";
            this.thoiGianVao = "";
            this.thoiGianRa = "";
            this.giaVe = 0;
            this.eventEnterId = "";
            this.eventExitId = "";
            this.vehicleId = "";
            this.vehicleType = "CAR_UNDER_9";
            this.hinhThucThanhToan = "tien_mat";
            this.status = "IN_PROGRESS";
            this.needsPaymentDisplay = false;
            this.needsCheckoutUpdate = false;
        }
    }

    /**
     * Xử lý dữ liệu thanh toán từ combined data (events + parkinglogs)
     */
    public PaymentData processPaymentDataFromCombined(JSONObject combinedData) throws JSONException {
        PaymentData paymentData = new PaymentData();

        Log.d(TAG, "Processing combined payment data: " + combinedData.toString());

        // Extract basic info
        paymentData.bienSoXe = combinedData.optString("plate_text", "");
        paymentData.vehicleId = combinedData.optString("vehicle_id", "");

        // Process events data
        if (combinedData.has("events")) {
            JSONObject eventsData = combinedData.getJSONObject("events");
            processEventsData(paymentData, eventsData);
        }

        // Process parking logs data
        if (combinedData.has("parking_logs")) {
            JSONObject parkingLogsData = combinedData.getJSONObject("parking_logs");
            processParkingLogsData(paymentData, parkingLogsData);
        }

        // Set defaults and validate
        validateAndSetDefaults(paymentData);

        Log.d(TAG, "Processed combined payment data:");
        logPaymentData(paymentData);

        return paymentData;
    }

    /**
     * Xử lý dữ liệu từ parkinglogs collection với events populated
     */
    public PaymentData processPaymentDataFromParkinglogs(JSONObject parkingLogData) throws JSONException {
        PaymentData paymentData = new PaymentData();

        Log.d(TAG, "Processing parking log data: " + parkingLogData.toString());

        // ID từ parkinglogs collection
        paymentData.parkingLogId = parkingLogData.optString("_id", "");
        paymentData.vehicleId = parkingLogData.optString("vehicle_id", "");

        // Extract enter event data (populated)
        if (parkingLogData.has("event_enter_id") && !parkingLogData.isNull("event_enter_id")) {
            JSONObject enterEvent = parkingLogData.getJSONObject("event_enter_id");
            paymentData.bienSoXe = enterEvent.optString("plate_text", "");
            paymentData.eventEnterId = enterEvent.optString("_id", "");

            // Use enter event timestamp if parking log timeIn is empty
            if (parkingLogData.optString("timeIn", "").isEmpty()) {
                paymentData.thoiGianVao = formatDateTime(enterEvent.optString("timestamp", ""));
            }
        }

        // Extract exit event data (populated)
        if (parkingLogData.has("event_exit_id") && !parkingLogData.isNull("event_exit_id")) {
            if (parkingLogData.get("event_exit_id") instanceof JSONObject) {
                JSONObject exitEvent = parkingLogData.getJSONObject("event_exit_id");
                paymentData.eventExitId = exitEvent.optString("_id", "");

                // Use exit event timestamp if parking log timeOut is empty
                if (parkingLogData.optString("timeOut", "").isEmpty()) {
                    paymentData.thoiGianRa = formatDateTime(exitEvent.optString("timestamp", ""));
                }
            } else {
                paymentData.eventExitId = parkingLogData.optString("event_exit_id", "");
            }
        }

        // Thời gian từ parking logs (ưu tiên hơn events)
        String timeIn = parkingLogData.optString("timeIn", "");
        String timeOut = parkingLogData.optString("timeOut", "");

        if (!timeIn.isEmpty()) {
            paymentData.thoiGianVao = formatDateTime(timeIn);
        }
        if (!timeOut.isEmpty()) {
            paymentData.thoiGianRa = formatDateTime(timeOut);
        }

        // Giá vé từ parkinglogs
        paymentData.giaVe = parkingLogData.optLong("fee", 0);

        // Vehicle type
        paymentData.vehicleType = parkingLogData.optString("vehicleType", "CAR_UNDER_9");

        // Payment method
        String paymentMethod = parkingLogData.optString("paymentMethod", "CASH");
        paymentData.hinhThucThanhToan = "BANK_TRANSFER".equals(paymentMethod) ? "chuyen_khoan" : "tien_mat";

        // Status từ parkinglogs
        paymentData.status = parkingLogData.optString("status", "IN_PROGRESS");

        // Determine if needs payment display
        paymentData.needsPaymentDisplay = "COMPLETED".equals(paymentData.status) &&
                !"PAID".equals(parkingLogData.optString("paymentStatus", ""));

        // Check if needs checkout update
        paymentData.needsCheckoutUpdate = paymentData.thoiGianRa.isEmpty() &&
                "IN_PROGRESS".equals(paymentData.status);

        validateAndSetDefaults(paymentData);
        logPaymentData(paymentData);

        return paymentData;
    }

    /**
     * Xử lý dữ liệu events trong combined data
     */
    private void processEventsData(PaymentData paymentData, JSONObject eventsData) throws JSONException {
        if (eventsData.has("data") && eventsData.get("data") instanceof JSONArray) {
            JSONArray events = eventsData.getJSONArray("data");

            JSONObject latestEnter = null;
            JSONObject latestExit = null;

            // Tìm enter và exit event mới nhất
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.getJSONObject(i);
                String eventType = event.optString("event_type", "").toUpperCase();
                String timestamp = event.optString("timestamp", "");

                if ("ENTER".equals(eventType)) {
                    if (latestEnter == null || timestamp.compareTo(latestEnter.optString("timestamp", "")) > 0) {
                        latestEnter = event;
                    }
                } else if ("EXIT".equals(eventType)) {
                    if (latestExit == null || timestamp.compareTo(latestExit.optString("timestamp", "")) > 0) {
                        latestExit = event;
                    }
                }
            }

            // Set event IDs and timestamps
            if (latestEnter != null) {
                paymentData.eventEnterId = latestEnter.optString("_id", "");
                if (paymentData.thoiGianVao.isEmpty()) {
                    paymentData.thoiGianVao = formatDateTime(latestEnter.optString("timestamp", ""));
                }
                if (paymentData.bienSoXe.isEmpty()) {
                    paymentData.bienSoXe = latestEnter.optString("plate_text", "");
                }
                if (paymentData.vehicleId.isEmpty()) {
                    paymentData.vehicleId = latestEnter.optString("vehicle_id", "");
                }
            }

            if (latestExit != null) {
                paymentData.eventExitId = latestExit.optString("_id", "");
                if (paymentData.thoiGianRa.isEmpty()) {
                    paymentData.thoiGianRa = formatDateTime(latestExit.optString("timestamp", ""));
                }
            }
        }
    }

    /**
     * Xử lý dữ liệu parking logs trong combined data
     */
    private void processParkingLogsData(PaymentData paymentData, JSONObject parkingLogsData) throws JSONException {
        if (parkingLogsData.has("data") && parkingLogsData.get("data") instanceof JSONArray) {
            JSONArray parkingLogs = parkingLogsData.getJSONArray("data");

            // Lấy parking log mới nhất
            if (parkingLogs.length() > 0) {
                JSONObject latestLog = parkingLogs.getJSONObject(0);

                paymentData.parkingLogId = latestLog.optString("_id", "");
                paymentData.giaVe = latestLog.optLong("fee", 0);
                paymentData.status = latestLog.optString("status", "IN_PROGRESS");

                // Override timestamps if available in parking log
                String timeIn = latestLog.optString("timeIn", "");
                String timeOut = latestLog.optString("timeOut", "");

                if (!timeIn.isEmpty()) {
                    paymentData.thoiGianVao = formatDateTime(timeIn);
                }
                if (!timeOut.isEmpty()) {
                    paymentData.thoiGianRa = formatDateTime(timeOut);
                }

                // Payment method
                String paymentMethod = latestLog.optString("paymentMethod", "CASH");
                paymentData.hinhThucThanhToan = "BANK_TRANSFER".equals(paymentMethod) ? "chuyen_khoan" : "tien_mat";
            }
        } else if (parkingLogsData.has("_id")) {
            // Single parking log object
            paymentData.parkingLogId = parkingLogsData.optString("_id", "");
            paymentData.giaVe = parkingLogsData.optLong("fee", 0);
            paymentData.status = parkingLogsData.optString("status", "IN_PROGRESS");

            String timeIn = parkingLogsData.optString("timeIn", "");
            String timeOut = parkingLogsData.optString("timeOut", "");

            if (!timeIn.isEmpty()) {
                paymentData.thoiGianVao = formatDateTime(timeIn);
            }
            if (!timeOut.isEmpty()) {
                paymentData.thoiGianRa = formatDateTime(timeOut);
            }

            String paymentMethod = parkingLogsData.optString("paymentMethod", "CASH");
            paymentData.hinhThucThanhToan = "BANK_TRANSFER".equals(paymentMethod) ? "chuyen_khoan" : "tien_mat";
        }
    }

    /**
     * Validate và set default values
     */
    private void validateAndSetDefaults(PaymentData paymentData) {
        if (paymentData.vehicleType.isEmpty()) {
            paymentData.vehicleType = "CAR_UNDER_9";
        }

        if (paymentData.hinhThucThanhToan.isEmpty()) {
            paymentData.hinhThucThanhToan = "tien_mat";
        }

        if (paymentData.status.isEmpty()) {
            paymentData.status = "IN_PROGRESS";
        }

        // Determine payment display need
        paymentData.needsPaymentDisplay = "COMPLETED".equals(paymentData.status) ||
                (!paymentData.thoiGianRa.isEmpty() && paymentData.giaVe > 0);

        // Determine checkout update need
        paymentData.needsCheckoutUpdate = paymentData.thoiGianRa.isEmpty() &&
                "IN_PROGRESS".equals(paymentData.status) &&
                !paymentData.eventExitId.isEmpty();
    }

    /**
     * Log payment data for debugging
     */
    private void logPaymentData(PaymentData paymentData) {
        Log.d(TAG, "=== PAYMENT DATA ===");
        Log.d(TAG, "Parking Log ID: " + paymentData.parkingLogId);
        Log.d(TAG, "Vehicle ID: " + paymentData.vehicleId);
        Log.d(TAG, "Biển số xe: " + paymentData.bienSoXe);
        Log.d(TAG, "Event Enter ID: " + paymentData.eventEnterId);
        Log.d(TAG, "Event Exit ID: " + paymentData.eventExitId);
        Log.d(TAG, "Thời gian vào: " + paymentData.thoiGianVao);
        Log.d(TAG, "Thời gian ra: " + paymentData.thoiGianRa);
        Log.d(TAG, "Giá vé: " + paymentData.giaVe);
        Log.d(TAG, "Vehicle type: " + paymentData.vehicleType);
        Log.d(TAG, "Payment method: " + paymentData.hinhThucThanhToan);
        Log.d(TAG, "Status: " + paymentData.status);
        Log.d(TAG, "Needs payment display: " + paymentData.needsPaymentDisplay);
        Log.d(TAG, "Needs checkout update: " + paymentData.needsCheckoutUpdate);
        Log.d(TAG, "==================");
    }

    /**
     * Cập nhật payment method trong parkinglogs
     */
    public void updatePaymentMethod(String parkingLogId, String eventEnterId, String vehicleType, String paymentMethod) {
        if (parkingLogId == null || parkingLogId.isEmpty()) {
            Log.w(TAG, "Cannot update payment method: parkingLogId is empty");
            return;
        }

        try {
            JSONObject updateData = new JSONObject();
            updateData.put("paymentMethod", paymentMethod.equals("chuyen_khoan") ? "BANK_TRANSFER" : "CASH");
            updateData.put("vehicleType", vehicleType);
            updateData.put("updatedAt", getCurrentDateTime());

            Log.d(TAG, "Updating payment method in parking log: " + updateData.toString());

            ApiHelper.updateParkingLogWithResponse(parkingLogId, updateData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Payment method updated successfully: " + response);
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Failed to update payment method: " + errorMessage);
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error creating payment method update data", e);
        }
    }

    /**
     * Cập nhật vehicle checkout trong parkinglogs
     */
    public void updateVehicleCheckoutInParkinglogs(String parkingLogId, String timeOut, long fee, String eventExitId, String vehicleType) {
        if (parkingLogId == null || parkingLogId.isEmpty()) {
            Log.w(TAG, "Cannot update checkout: parkingLogId is empty");
            return;
        }

        try {
            JSONObject updateData = new JSONObject();
            if (!timeOut.isEmpty()) {
                updateData.put("timeOut", convertToISOFormat(timeOut));
            }
            if (fee > 0) {
                updateData.put("fee", fee);
            }
            if (!eventExitId.isEmpty()) {
                updateData.put("event_exit_id", eventExitId);
            }
            updateData.put("status", "COMPLETED");
            updateData.put("vehicleType", vehicleType);
            updateData.put("updatedAt", getCurrentDateTime());

            Log.d(TAG, "Updating vehicle checkout in parking log: " + updateData.toString());

            ApiHelper.updateParkingLogWithResponse(parkingLogId, updateData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Vehicle checkout updated successfully: " + response);
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Failed to update checkout: " + errorMessage);
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error creating checkout update data", e);
        }
    }

    /**
     * Xử lý thanh toán và in hóa đơn
     */
    public void processPaymentAndPrint(String parkingLogId, String bienSoXe, String thoiGianVao,
                                       String thoiGianRa, long giaVe, String hinhThucThanhToan,
                                       String eventEnterId, String vehicleType) {

        if (activity != null) {
            activity.setButtonProcessing();
        }

        Log.d(TAG, "Processing payment and printing invoice:");
        Log.d(TAG, "- Parking Log ID: " + parkingLogId);
        Log.d(TAG, "- Biển số: " + bienSoXe);
        Log.d(TAG, "- Thời gian vào: " + thoiGianVao);
        Log.d(TAG, "- Thời gian ra: " + thoiGianRa);
        Log.d(TAG, "- Giá vé: " + giaVe);
        Log.d(TAG, "- Hình thức thanh toán: " + hinhThucThanhToan);

        try {
            JSONObject paymentData = new JSONObject();
            paymentData.put("paymentStatus", "PAID");
            paymentData.put("paymentMethod", hinhThucThanhToan.equals("chuyen_khoan") ? "BANK_TRANSFER" : "CASH");
            paymentData.put("paymentTime", getCurrentDateTime());
            paymentData.put("vehicleType", vehicleType);
            paymentData.put("fee", giaVe);
            paymentData.put("status", "COMPLETED");

            // Cập nhật parking log với thông tin thanh toán
            ApiHelper.updateParkingLogWithPayment(parkingLogId, paymentData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Payment processed successfully: " + response);

                    // Proceed to print invoice
                    processInvoicePrint(bienSoXe, thoiGianVao, thoiGianRa, giaVe, hinhThucThanhToan, parkingLogId);
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Payment processing failed: " + errorMessage);

                    if (activity != null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                activity.resetButton();
                                Toast.makeText(context, "Lỗi xử lý thanh toán: " + errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "Error creating payment data", e);

            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        activity.resetButton();
                        Toast.makeText(context, "Lỗi tạo dữ liệu thanh toán", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    /**
     * Xử lý in hóa đơn
     */
    private void processInvoicePrint(String bienSoXe, String thoiGianVao, String thoiGianRa,
                                     long giaVe, String hinhThucThanhToan, String parkingLogId) {
        try {
            JSONObject printData = new JSONObject();
            printData.put("bienSoXe", bienSoXe);
            printData.put("plateNumber", bienSoXe); // Alternative field name
            printData.put("thoiGianVao", thoiGianVao);
            printData.put("timeIn", thoiGianVao);
            printData.put("thoiGianRa", thoiGianRa);
            printData.put("timeOut", thoiGianRa);
            printData.put("giaVe", giaVe);
            printData.put("fee", giaVe);
            printData.put("hinhThucThanhToan", hinhThucThanhToan);
            printData.put("paymentMethod", hinhThucThanhToan.equals("chuyen_khoan") ? "BANK_TRANSFER" : "CASH");
            printData.put("thoiGianIn", getCurrentDateTime());
            printData.put("printTime", getCurrentDateTime());
            printData.put("parkingLogId", parkingLogId);
            printData.put("source", "parkinglogs");

            Log.d(TAG, "Printing invoice with data: " + printData.toString());

            ApiHelper.printInvoiceWithResponse(printData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Invoice printed successfully: " + response);

                    if (activity != null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                activity.resetButton();

                                String paymentMethodText = hinhThucThanhToan.equals("chuyen_khoan") ?
                                        "Chuyển khoản" : "Tiền mặt";

                                activity.showSuccessDialog(bienSoXe, thoiGianVao, thoiGianRa, giaVe, paymentMethodText);

                                // Reset dữ liệu activity sau khi thanh toán thành công
                                activity.resetActivityData();
                            }
                        });
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Invoice printing failed: " + errorMessage);

                    if (activity != null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                activity.resetButton();
                                Toast.makeText(context, "Thanh toán thành công nhưng in hóa đơn lỗi: " + errorMessage,
                                        Toast.LENGTH_LONG).show();

                                // Still reset data even if printing failed
                                activity.resetActivityData();
                            }
                        });
                    }
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "Error creating print data", e);

            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        activity.resetButton();
                        Toast.makeText(context, "Thanh toán thành công nhưng lỗi tạo hóa đơn", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    /**
     * Tính phí đỗ xe dựa trên thời gian
     */
    public static long calculateParkingFee(String timeIn, String timeOut, String vehicleType) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
            Date dateIn = sdf.parse(timeIn);
            Date dateOut = sdf.parse(timeOut);

            if (dateIn == null || dateOut == null) {
                return 0;
            }

            long diffInMillis = dateOut.getTime() - dateIn.getTime();
            long diffInHours = diffInMillis / (1000 * 60 * 60);

            // Làm tròn lên giờ
            if (diffInMillis % (1000 * 60 * 60) > 0) {
                diffInHours++;
            }

            // Phí theo loại xe (có thể tùy chỉnh)
            long hourlyRate = getHourlyRate(vehicleType);

            return Math.max(diffInHours * hourlyRate, hourlyRate); // Minimum 1 hour

        } catch (Exception e) {
            Log.e(TAG, "Error calculating parking fee", e);
            return 0;
        }
    }

    /**
     * Lấy giá theo giờ dựa trên loại xe
     */
    private static long getHourlyRate(String vehicleType) {
        switch (vehicleType) {
            case "MOTORBIKE":
                return 5000; // 5k/hour for motorbikes
            case "CAR_UNDER_9":
                return 10000; // 10k/hour for cars under 9 seats
            case "CAR_OVER_9":
                return 15000; // 15k/hour for cars over 9 seats
            case "TRUCK":
                return 20000; // 20k/hour for trucks
            default:
                return 10000; // Default rate
        }
    }

    /**
     * Validate payment data
     */
    public static boolean validatePaymentData(PaymentData paymentData) {
        if (paymentData == null) {
            Log.e(TAG, "Payment data is null");
            return false;
        }

        if (paymentData.bienSoXe.isEmpty()) {
            Log.e(TAG, "License plate is empty");
            return false;
        }

        if (paymentData.vehicleId.isEmpty() && paymentData.eventEnterId.isEmpty()) {
            Log.e(TAG, "Both vehicle ID and event enter ID are empty");
            return false;
        }

        if (paymentData.thoiGianVao.isEmpty()) {
            Log.e(TAG, "Entry time is empty");
            return false;
        }

        return true;
    }

    /**
     * Create parking log from events data
     */
    public void createParkingLogFromEvents(String vehicleId, String eventEnterId, String eventExitId,
                                           String plateText, String vehicleType, ApiHelper.OnResponseListener listener) {
        try {
            JSONObject parkingLogData = new JSONObject();
            parkingLogData.put("vehicle_id", vehicleId);
            parkingLogData.put("event_enter_id", eventEnterId);
            if (!eventExitId.isEmpty()) {
                parkingLogData.put("event_exit_id", eventExitId);
            }
            parkingLogData.put("plate_text", plateText);
            parkingLogData.put("vehicleType", vehicleType);
            parkingLogData.put("status", eventExitId.isEmpty() ? "IN_PROGRESS" : "COMPLETED");
            parkingLogData.put("createdAt", getCurrentDateTime());

            Log.d(TAG, "Creating parking log from events: " + parkingLogData.toString());

            ApiHelper.createParkingLog(parkingLogData.toString(), "", listener);

        } catch (JSONException e) {
            Log.e(TAG, "Error creating parking log data", e);
            listener.onError("Error creating parking log: " + e.getMessage());
        }
    }

    /**
     * Format date time string
     */
    private String formatDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.isEmpty()) {
            return "";
        }

        try {
            // Try parsing ISO format first
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());

            Date date = inputFormat.parse(dateTimeString);
            return outputFormat.format(date);
        } catch (Exception e) {
            try {
                // Try alternative ISO format
                SimpleDateFormat inputFormat2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());

                Date date = inputFormat2.parse(dateTimeString);
                return outputFormat.format(date);
            } catch (Exception e2) {
                Log.e(TAG, "Error formatting date time: " + dateTimeString, e2);
                return dateTimeString; // Return original if can't parse
            }
        }
    }

    /**
     * Convert formatted date time to ISO format
     */
    private String convertToISOFormat(String formattedDateTime) {
        if (formattedDateTime == null || formattedDateTime.isEmpty()) {
            return getCurrentDateTime();
        }

        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());

            Date date = inputFormat.parse(formattedDateTime);
            return outputFormat.format(date);
        } catch (Exception e) {
            Log.e(TAG, "Error converting to ISO format: " + formattedDateTime, e);
            return getCurrentDateTime();
        }
    }

    /**
     * Get current date time in ISO format
     */
    private String getCurrentDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * Get current date time in display format
     */
    public static String getCurrentDisplayDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * Extract plate text from various data formats
     */
    public static String extractPlateText(JSONObject data) {
        // Try different possible field names
        String[] plateFields = {"plate_text", "plateNumber", "plateText", "licensePlate", "bienSoXe"};

        for (String field : plateFields) {
            if (data.has(field) && !data.optString(field, "").isEmpty()) {
                return data.optString(field, "");
            }
        }

        return "";
    }

    /**
     * Extract vehicle ID from various data formats
     */
    public static String extractVehicleId(JSONObject data) {
        String[] vehicleIdFields = {"vehicle_id", "vehicleId", "id", "_id"};

        for (String field : vehicleIdFields) {
            if (data.has(field) && !data.optString(field, "").isEmpty()) {
                return data.optString(field, "");
            }
        }

        return "";
    }
}