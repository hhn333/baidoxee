package com.example.baidoxee;

import static com.bumptech.glide.load.model.stream.HttpGlideUrlLoader.TIMEOUT;

import android.os.AsyncTask;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.io.IOException;

public class ApiHelper {

    // Cập nhật IP address đúng
    private static final String BASE_URL = "http://192.168.1.191:3000/api";
    private static final String TAG = "ApiHelper";

    // Cấu hình timeout
    private static final int CONNECT_TIMEOUT = 15000; // 15 giây
    private static final int READ_TIMEOUT = 20000;    // 20 giây
    private static final int MAX_RETRY_ATTEMPTS = 3;

    // Interface để handle callback
    public interface OnDataReceivedListener {
        void onDataReceived(String jsonData);
        void onError(String errorMessage);
    }

    public interface OnResponseListener {
        void onSuccess(String response);
        void onError(String errorMessage);
    }

    // ===== PARKINGLOGS ENDPOINTS =====

    /**
     * Lấy tất cả parking logs với thông tin events populated
     */
    public static void getAllParkingLogsWithEvents(OnDataReceivedListener listener) {
        String url = BASE_URL + "/parkinglogs?populate=events";
        Log.d(TAG, "Getting all parking logs with events: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Lấy parking log theo ID với thông tin events populated
     */
    public static void getParkingLogWithEventsByID(String parkingLogId, OnDataReceivedListener listener) {
        if (parkingLogId == null || parkingLogId.trim().isEmpty()) {
            listener.onError("Parking log ID is empty");
            return;
        }

        String url = BASE_URL + "/parkinglogs/" + parkingLogId.trim() + "?populate=events";
        Log.d(TAG, "Getting parking log with events by ID: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Lấy parking logs theo vehicle_id với thông tin events
     */
    public static void getParkingLogsByVehicleId(String vehicleId, OnDataReceivedListener listener) {
        if (vehicleId == null || vehicleId.trim().isEmpty()) {
            listener.onError("Vehicle ID is empty");
            return;
        }

        String url = BASE_URL + "/parkinglogs/vehicle/" + vehicleId.trim();
        Log.d(TAG, "Getting parking logs by vehicle ID: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Lấy parking logs theo biển số xe (từ events.plate_text)
     */
    public static void getParkingLogsByPlateText(String plateText, OnDataReceivedListener listener) {
        if (plateText == null || plateText.trim().isEmpty()) {
            listener.onError("Plate text is empty");
            return;
        }

        String encodedPlate = plateText.trim().replace(" ", "%20");
        String url = BASE_URL + "/parkinglogs/plate/" + encodedPlate;
        Log.d(TAG, "Getting parking logs by plate text: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Lấy vehicles cần thanh toán từ parkinglogs với status = COMPLETED
     */
    public static void getVehiclesNeedPaymentFromParkinglogs(OnDataReceivedListener listener) {
        String url = BASE_URL + "/parkinglogs/need-payment";
        Log.d(TAG, "Getting vehicles need payment from parking logs: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Lấy thông tin xe theo biển số từ parkinglogs với events populated
     */
    public static void getVehicleByLicensePlateFromParkinglogs(String plateNumber, OnDataReceivedListener listener) {
        if (plateNumber == null || plateNumber.trim().isEmpty()) {
            listener.onError("Plate number is empty");
            return;
        }

        String encodedPlate = plateNumber.trim().replace(" ", "%20");
        String url = BASE_URL + "/parkinglogs/by-plate/" + encodedPlate;
        Log.d(TAG, "Getting vehicle by license plate from parking logs: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Cập nhật parking log
     */
    public static void updateParkingLog(String parkingLogId, String updateData, OnDataReceivedListener listener) {
        updateParkingLogWithResponse(parkingLogId, updateData, new OnResponseListener() {
            @Override
            public void onSuccess(String response) {
                listener.onDataReceived(response);
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError(errorMessage);
            }
        });
    }

    /**
     * Cập nhật parking log với payment information
     */
    public static void updateParkingLogWithPayment(String parkingLogId, String paymentData, OnResponseListener listener) {
        if (parkingLogId == null || parkingLogId.trim().isEmpty()) {
            listener.onError("Parking log ID is empty");
            return;
        }

        if (paymentData == null || paymentData.trim().isEmpty()) {
            listener.onError("Payment data is empty");
            return;
        }

        String url = BASE_URL + "/parkinglogs/payment/" + parkingLogId.trim();
        Log.d(TAG, "Updating parking log with payment: " + url);
        Log.d(TAG, "Payment data: " + paymentData);

        makePutRequestWithRetry(url, paymentData, listener, MAX_RETRY_ATTEMPTS);
    }

    /**
     * Cập nhật parking log (generic)
     */
    public static void updateParkingLogWithResponse(String parkingLogId, String updateData, OnResponseListener listener) {
        if (parkingLogId == null || parkingLogId.trim().isEmpty()) {
            listener.onError("Parking log ID is empty");
            return;
        }

        if (updateData == null || updateData.trim().isEmpty()) {
            listener.onError("Update data is empty");
            return;
        }

        String url = BASE_URL + "/parkinglogs/" + parkingLogId.trim();
        Log.d(TAG, "Updating parking log: " + url);
        Log.d(TAG, "Update data: " + updateData);

        makePutRequestWithRetry(url, updateData, listener, MAX_RETRY_ATTEMPTS);
    }

    /**
     * Tạo mới parking log
     */
    public static void createParkingLog(String parkingLogData, String additionalData, OnResponseListener listener) {
        if (parkingLogData == null || parkingLogData.trim().isEmpty()) {
            listener.onError("Parking log data is empty");
            return;
        }

        String url = BASE_URL + "/parkinglogs";
        Log.d(TAG, "Creating parking log: " + url);
        Log.d(TAG, "Parking log data: " + parkingLogData);

        makePostRequest(url, parkingLogData, listener);
    }

    /**
     * Xóa parking log
     */
    public static void deleteParkingLog(String parkingLogId, OnResponseListener listener) {
        if (parkingLogId == null || parkingLogId.trim().isEmpty()) {
            listener.onError("Parking log ID is empty");
            return;
        }

        String url = BASE_URL + "/parkinglogs/" + parkingLogId.trim();
        Log.d(TAG, "Deleting parking log: " + url);
        makeDeleteRequest(url, listener);
    }

    /**
     * Lấy thống kê parking logs
     */
    public static void getParkingLogsStats(OnDataReceivedListener listener) {
        String url = BASE_URL + "/parkinglogs/stats";
        Log.d(TAG, "Getting parking logs statistics: " + url);
        makeGetRequest(url, listener);
    }

    // ===== EVENTS ENDPOINTS =====

    /**
     * Lấy tất cả events
     */
    public static void getAllEvents(OnDataReceivedListener listener) {
        String url = BASE_URL + "/events";
        Log.d(TAG, "Getting all events: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Lấy event theo ID
     */
    public static void getEventById(String eventId, OnDataReceivedListener listener) {
        if (eventId == null || eventId.trim().isEmpty()) {
            listener.onError("Event ID is empty");
            return;
        }

        String url = BASE_URL + "/events/" + eventId.trim();
        Log.d(TAG, "Getting event by ID: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Lấy events theo vehicle_id
     */
    public static void getEventsByVehicleId(String vehicleId, OnDataReceivedListener listener) {
        if (vehicleId == null || vehicleId.trim().isEmpty()) {
            listener.onError("Vehicle ID is empty");
            return;
        }

        String url = BASE_URL + "/events/vehicle/" + vehicleId.trim();
        Log.d(TAG, "Getting events by vehicle ID: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Lấy events theo plate_text
     */
    public static void getEventsByPlateText(String plateText, OnDataReceivedListener listener) {
        if (plateText == null || plateText.trim().isEmpty()) {
            listener.onError("Plate text is empty");
            return;
        }

        String encodedPlate = plateText.trim().replace(" ", "%20");
        String url = BASE_URL + "/events/plate/" + encodedPlate;
        Log.d(TAG, "Getting events by plate text: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Lấy latest enter event theo plate_text
     */
    public static void getLatestEnterEventByPlate(String plateText, OnDataReceivedListener listener) {
        if (plateText == null || plateText.trim().isEmpty()) {
            listener.onError("Plate text is empty");
            return;
        }

        String encodedPlate = plateText.trim().replace(" ", "%20");
        String url = BASE_URL + "/events/latest-enter/" + encodedPlate;
        Log.d(TAG, "Getting latest enter event by plate: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Lấy latest exit event theo plate_text
     */
    public static void getLatestExitEventByPlate(String plateText, OnDataReceivedListener listener) {
        if (plateText == null || plateText.trim().isEmpty()) {
            listener.onError("Plate text is empty");
            return;
        }

        String encodedPlate = plateText.trim().replace(" ", "%20");
        String url = BASE_URL + "/events/latest-exit/" + encodedPlate;
        Log.d(TAG, "Getting latest exit event by plate: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Lấy enter event trước thời điểm cụ thể
     */
    public static void getEnterEventByPlateBeforeTime(String plateNumber, String beforeTime, OnDataReceivedListener listener) {
        if (plateNumber == null || plateNumber.trim().isEmpty()) {
            listener.onError("Plate number is empty");
            return;
        }

        String url = BASE_URL + "/events/enter-before";
        Log.d(TAG, "Getting enter event by plate before time: " + url);

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("plateNumber", plateNumber.trim());
            if (beforeTime != null && !beforeTime.trim().isEmpty()) {
                requestBody.put("beforeTime", beforeTime.trim());
            }

            Log.d(TAG, "Enter event request body: " + requestBody.toString());

            makePostRequest(url, requestBody.toString(), new OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Enter event by plate response: " + response);
                    listener.onDataReceived(response);
                }

                @Override
                public void onError(String error) {
                    Log.w(TAG, "Enter event by plate error: " + error);
                    listener.onError(error);
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error creating enter event request: " + e.getMessage());
            listener.onError("Error creating request: " + e.getMessage());
        }
    }

    /**
     * Tạo mới event
     */
    public static void createEvent(String eventData, OnResponseListener listener) {
        if (eventData == null || eventData.trim().isEmpty()) {
            listener.onError("Event data is empty");
            return;
        }

        String url = BASE_URL + "/events";
        Log.d(TAG, "Creating event: " + url);
        Log.d(TAG, "Event data: " + eventData);

        makePostRequest(url, eventData, listener);
    }

    // ===== COMBINED METHODS FOR PARKING MANAGEMENT =====

    /**
     * Lấy thông tin đầy đủ của xe theo biển số (kết hợp events và parkinglogs)
     */
    public static void getCompleteVehicleInfoByPlate(String plateText, OnDataReceivedListener listener) {
        Log.d(TAG, "Getting complete vehicle info by plate: " + plateText);

        // Bước 1: Lấy events theo plate_text
        getEventsByPlateText(plateText, new OnDataReceivedListener() {
            @Override
            public void onDataReceived(String eventsData) {
                Log.d(TAG, "Events data received: " + eventsData);

                try {
                    JSONObject eventsResponse = new JSONObject(eventsData);
                    JSONArray events = null;

                    if (eventsResponse.has("data") && eventsResponse.get("data") instanceof JSONArray) {
                        events = eventsResponse.getJSONArray("data");
                    } else if (eventsResponse.has("events")) {
                        events = eventsResponse.getJSONArray("events");
                    }

                    if (events != null && events.length() > 0) {
                        JSONObject firstEvent = events.getJSONObject(0);
                        String vehicleId = firstEvent.optString("vehicle_id", "");

                        if (!vehicleId.isEmpty()) {
                            // Bước 2: Lấy parking logs theo vehicle_id
                            getParkingLogsByVehicleId(vehicleId, new OnDataReceivedListener() {
                                @Override
                                public void onDataReceived(String parkingLogsData) {
                                    Log.d(TAG, "Parking logs data received: " + parkingLogsData);

                                    try {
                                        // Kết hợp dữ liệu events và parking logs
                                        JSONObject combinedData = new JSONObject();
                                        combinedData.put("events", eventsResponse);
                                        combinedData.put("parking_logs", new JSONObject(parkingLogsData));
                                        combinedData.put("vehicle_id", vehicleId);
                                        combinedData.put("plate_text", plateText);

                                        listener.onDataReceived(combinedData.toString());
                                    } catch (JSONException e) {
                                        Log.e(TAG, "Error combining data: " + e.getMessage());
                                        listener.onError("Error combining vehicle data: " + e.getMessage());
                                    }
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    // Nếu không có parking logs, vẫn trả về events data
                                    Log.w(TAG, "No parking logs found, returning events only: " + errorMessage);
                                    try {
                                        JSONObject fallbackData = new JSONObject();
                                        fallbackData.put("events", eventsResponse);
                                        fallbackData.put("vehicle_id", vehicleId);
                                        fallbackData.put("plate_text", plateText);
                                        fallbackData.put("parking_logs_error", errorMessage);

                                        listener.onDataReceived(fallbackData.toString());
                                    } catch (JSONException e) {
                                        listener.onError("Error creating fallback data: " + e.getMessage());
                                    }
                                }
                            });
                        } else {
                            listener.onError("No vehicle_id found in events data");
                        }
                    } else {
                        listener.onError("No events found for plate: " + plateText);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing events data: " + e.getMessage());
                    listener.onError("Error parsing events data: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error getting events by plate: " + errorMessage);
                listener.onError("Error getting events: " + errorMessage);
            }
        });
    }

    /**
     * Lấy thông tin thanh toán đầy đủ theo biển số
     */
    public static void getPaymentInfoByPlate(String plateText, OnDataReceivedListener listener) {
        Log.d(TAG, "Getting payment info by plate: " + plateText);

        getCompleteVehicleInfoByPlate(plateText, new OnDataReceivedListener() {
            @Override
            public void onDataReceived(String combinedData) {
                try {
                    JSONObject data = new JSONObject(combinedData);

                    // Extract payment-relevant information
                    JSONObject paymentInfo = new JSONObject();
                    paymentInfo.put("plate_text", plateText);

                    if (data.has("vehicle_id")) {
                        paymentInfo.put("vehicle_id", data.getString("vehicle_id"));
                    }

                    // Extract latest events
                    if (data.has("events")) {
                        JSONObject eventsData = data.getJSONObject("events");
                        if (eventsData.has("data")) {
                            JSONArray events = eventsData.getJSONArray("data");

                            JSONObject latestEnter = null;
                            JSONObject latestExit = null;

                            for (int i = 0; i < events.length(); i++) {
                                JSONObject event = events.getJSONObject(i);
                                String eventType = event.optString("event_type", "");

                                if ("ENTER".equals(eventType)) {
                                    if (latestEnter == null ||
                                            event.optString("timestamp", "").compareTo(latestEnter.optString("timestamp", "")) > 0) {
                                        latestEnter = event;
                                    }
                                } else if ("EXIT".equals(eventType)) {
                                    if (latestExit == null ||
                                            event.optString("timestamp", "").compareTo(latestExit.optString("timestamp", "")) > 0) {
                                        latestExit = event;
                                    }
                                }
                            }

                            if (latestEnter != null) {
                                paymentInfo.put("latest_enter", latestEnter);
                            }
                            if (latestExit != null) {
                                paymentInfo.put("latest_exit", latestExit);
                            }
                        }
                    }

                    // Extract parking logs info
                    if (data.has("parking_logs")) {
                        paymentInfo.put("parking_logs", data.getJSONObject("parking_logs"));
                    }

                    listener.onDataReceived(paymentInfo.toString());

                } catch (JSONException e) {
                    Log.e(TAG, "Error creating payment info: " + e.getMessage());
                    listener.onError("Error processing payment info: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError(errorMessage);
            }
        });
    }

    // ===== PRINT ENDPOINT =====

    /**
     * In hóa đơn
     */
    public static void printInvoice(String invoiceData, OnDataReceivedListener listener) {
        printInvoiceWithResponse(invoiceData, new OnResponseListener() {
            @Override
            public void onSuccess(String response) {
                listener.onDataReceived(response);
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError(errorMessage);
            }
        });
    }

    /**
     * Gửi lệnh in hóa đơn
     */
    public static void printInvoiceWithResponse(String invoiceData, OnResponseListener listener) {
        if (invoiceData == null || invoiceData.trim().isEmpty()) {
            listener.onError("Invoice data is empty");
            return;
        }

        try {
            JSONObject printData = new JSONObject(invoiceData);
            if (!printData.has("bienSoXe") || printData.getString("bienSoXe").trim().isEmpty()) {
                listener.onError("Missing license plate number in invoice data");
                return;
            }
        } catch (JSONException e) {
            listener.onError("Invalid invoice data format: " + e.getMessage());
            return;
        }

        String url = BASE_URL + "/print/invoice";
        Log.d(TAG, "Printing invoice: " + url);
        Log.d(TAG, "Invoice data: " + invoiceData);

        makePostRequestWithRetry(url, invoiceData, listener, 2);
    }

    // ===== LEGACY COMPATIBILITY METHODS =====

    public static void getEnterEventForPlate(String plateNumber, String exitEventTimestamp, OnDataReceivedListener listener) {
        getEnterEventByPlateBeforeTime(plateNumber, exitEventTimestamp, listener);
    }

    public static void processPayment(String updateId, String paymentData, OnResponseListener listener) {
        updateParkingLogWithPayment(updateId, paymentData, listener);
    }

    public static void getVehicleInfoFromEvents(String bienSo, OnDataReceivedListener listener) {
        getEventsByPlateText(bienSo, listener);
    }

    public static void getVehicleInfoByEventId(String enterEventId, OnDataReceivedListener listener) {
        getEventById(enterEventId, listener);
    }

    public static void getCompletedVehicleByVehicleId(String vehicleId, OnDataReceivedListener listener) {
        getParkingLogsByVehicleId(vehicleId, listener);
    }

    public static void getCompletedVehicleByPlateText(String plateText, OnDataReceivedListener listener) {
        getVehicleByLicensePlateFromParkinglogs(plateText, listener);
    }

    public static void getCompletedVehiclesForPayment(OnDataReceivedListener listener) {
        getVehiclesNeedPaymentFromParkinglogs(listener);
    }

    public static void updateParkingLogPayment(String parkingLogId, String paymentData, OnResponseListener listener) {
        updateParkingLogWithPayment(parkingLogId, paymentData, listener);
    }

    public static void sendPrintCommand(String jsonData, OnResponseListener listener) {
        printInvoiceWithResponse(jsonData, listener);
    }

    // ===== DASHBOARD ENDPOINTS =====

    public static void getDashboardStats(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting dashboard statistics from /api/dashboard-stats");
        makeGetRequest(BASE_URL + "/dashboard-stats", listener);
    }

    public static void getRecentActivities(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting recent activities from /api/recent-activities");
        makeGetRequest(BASE_URL + "/recent-activities", listener);
    }

    public static void getRecentActivitiesWithLimit(int limit, OnDataReceivedListener listener) {
        Log.d(TAG, "Getting recent activities with limit: " + limit);
        makeGetRequest(BASE_URL + "/recent-activities?limit=" + limit, listener);
    }

    public static void getRealtimeStatus(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting realtime WebSocket status");
        makeGetRequest(BASE_URL + "/realtime-status", listener);
    }

    // ===== ACTIVITIES ENDPOINTS (LEGACY) =====

    public static void getActivities(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting all activities (legacy) - redirecting to parking logs");
        getAllParkingLogsWithEvents(listener);
    }

    public static void getVehiclesNeedPayment(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting vehicles needing payment (legacy) - redirecting to parking logs");
        getVehiclesNeedPaymentFromParkinglogs(listener);
    }

    public static void getVehicleByLicensePlate(String plateNumber, OnDataReceivedListener listener) {
        Log.d(TAG, "Getting vehicle by license plate (legacy) - using combined method");
        getCompleteVehicleInfoByPlate(plateNumber, listener);
    }

    public static void updateActivity(String id, String jsonData, OnResponseListener listener) {
        Log.d(TAG, "Updating activity (legacy) - redirecting to parking log update");
        updateParkingLogWithResponse(id, jsonData, listener);
    }

    // ===== TEST ENDPOINTS =====

    public static void testConnection(OnDataReceivedListener listener) {
        Log.d(TAG, "Testing server connection at /api/test");
        makeGetRequest(BASE_URL + "/test", listener);
    }

    public static void pingServer(OnResponseListener listener) {
        testConnection(new OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                listener.onSuccess("Server connection successful: " + jsonData);
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError("Server connection failed: " + errorMessage);
            }
        });
    }

    // ===== HTTP REQUEST METHODS =====

    private static void makeGetRequest(String urlString, OnDataReceivedListener listener) {
        new AsyncTask<Void, Void, ApiResponse>() {
            @Override
            protected ApiResponse doInBackground(Void... voids) {
                return executeGetRequest(urlString);
            }

            @Override
            protected void onPostExecute(ApiResponse response) {
                if (response.isSuccess) {
                    listener.onDataReceived(response.data);
                } else {
                    listener.onError(response.errorMessage);
                }
            }
        }.execute();
    }

    private static void makePostRequest(String urlString, String jsonData, OnResponseListener listener) {
        makePostRequestWithRetry(urlString, jsonData, listener, MAX_RETRY_ATTEMPTS);
    }

    private static void makePostRequestWithRetry(String urlString, String jsonData, OnResponseListener listener, int maxRetries) {
        new AsyncTask<Void, Void, ApiResponse>() {
            @Override
            protected ApiResponse doInBackground(Void... voids) {
                return executePostRequestWithRetry(urlString, jsonData, maxRetries);
            }

            @Override
            protected void onPostExecute(ApiResponse response) {
                if (response.isSuccess) {
                    listener.onSuccess(response.data);
                } else {
                    listener.onError(response.errorMessage);
                }
            }
        }.execute();
    }

    private static void makePutRequestWithRetry(String urlString, String jsonData, OnResponseListener listener, int maxRetries) {
        new AsyncTask<Void, Void, ApiResponse>() {
            @Override
            protected ApiResponse doInBackground(Void... voids) {
                return executePutRequestWithRetry(urlString, jsonData, maxRetries);
            }

            @Override
            protected void onPostExecute(ApiResponse response) {
                if (response.isSuccess) {
                    listener.onSuccess(response.data);
                } else {
                    listener.onError(response.errorMessage);
                }
            }
        }.execute();
    }

    private static void makeDeleteRequest(String urlString, OnResponseListener listener) {
        new AsyncTask<Void, Void, ApiResponse>() {
            @Override
            protected ApiResponse doInBackground(Void... voids) {
                return executeDeleteRequest(urlString);
            }

            @Override
            protected void onPostExecute(ApiResponse response) {
                if (response.isSuccess) {
                    listener.onSuccess(response.data);
                } else {
                    listener.onError(response.errorMessage);
                }
            }
        }.execute();
    }

    // ===== CORE HTTP EXECUTION METHODS =====

    private static ApiResponse executeGetRequest(String urlString) {
        HttpURLConnection connection = null;
        try {
            Log.d(TAG, "Making GET request to: " + urlString);

            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "BaiDoXeAndroidApp/1.0");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setUseCaches(false);

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "GET Response code: " + responseCode);

            return handleResponse(connection, responseCode);

        } catch (Exception e) {
            Log.e(TAG, "GET request failed: " + urlString, e);
            return new ApiResponse(false, null, getErrorMessage(e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static ApiResponse executePostRequestWithRetry(String urlString, String jsonData, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Log.d(TAG, "POST attempt " + attempt + "/" + maxRetries + " to: " + urlString);

            ApiResponse response = executePostRequest(urlString, jsonData);

            if (response.isSuccess || !shouldRetry(response.errorMessage) || attempt == maxRetries) {
                if (!response.isSuccess && attempt > 1) {
                    Log.w(TAG, "POST failed after " + attempt + " attempts: " + response.errorMessage);
                }
                return response;
            }

            // Wait before retry
            try {
                Thread.sleep(1000 * attempt); // Exponential backoff
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ApiResponse(false, null, "Request interrupted");
            }
        }

        return new ApiResponse(false, null, "Max retries exceeded");
    }

    private static ApiResponse executePutRequestWithRetry(String urlString, String jsonData, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Log.d(TAG, "PUT attempt " + attempt + "/" + maxRetries + " to: " + urlString);

            ApiResponse response = executePutRequest(urlString, jsonData);

            if (response.isSuccess || !shouldRetry(response.errorMessage) || attempt == maxRetries) {
                if (!response.isSuccess && attempt > 1) {
                    Log.w(TAG, "PUT failed after " + attempt + " attempts: " + response.errorMessage);
                }
                return response;
            }

            // Wait before retry
            try {
                Thread.sleep(1000 * attempt); // Exponential backoff
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ApiResponse(false, null, "Request interrupted");
            }
        }

        return new ApiResponse(false, null, "Max retries exceeded");
    }

    private static ApiResponse executePostRequest(String urlString, String jsonData) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "BaiDoXeAndroidApp/1.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setUseCaches(false);

            // Send JSON data
            if (jsonData != null && !jsonData.isEmpty()) {
                try (OutputStream outputStream = connection.getOutputStream()) {
                    byte[] input = jsonData.getBytes("UTF-8");
                    outputStream.write(input, 0, input.length);
                    outputStream.flush();
                }
            }

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "POST Response code: " + responseCode);

            return handleResponse(connection, responseCode);

        } catch (Exception e) {
            Log.e(TAG, "POST request failed: " + urlString, e);
            return new ApiResponse(false, null, getErrorMessage(e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static ApiResponse executePutRequest(String urlString, String jsonData) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "BaiDoXeAndroidApp/1.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setUseCaches(false);

            // Send JSON data
            if (jsonData != null && !jsonData.isEmpty()) {
                try (OutputStream outputStream = connection.getOutputStream()) {
                    byte[] input = jsonData.getBytes("UTF-8");
                    outputStream.write(input, 0, input.length);
                    outputStream.flush();
                }
            }

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "PUT Response code: " + responseCode);

            return handleResponse(connection, responseCode);

        } catch (Exception e) {
            Log.e(TAG, "PUT request failed: " + urlString, e);
            return new ApiResponse(false, null, getErrorMessage(e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static ApiResponse executeDeleteRequest(String urlString) {
        HttpURLConnection connection = null;
        try {
            Log.d(TAG, "Making DELETE request to: " + urlString);

            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "BaiDoXeAndroidApp/1.0");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setUseCaches(false);

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "DELETE Response code: " + responseCode);

            return handleResponse(connection, responseCode);

        } catch (Exception e) {
            Log.e(TAG, "DELETE request failed: " + urlString, e);
            return new ApiResponse(false, null, getErrorMessage(e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // ===== RESPONSE HANDLING =====

    private static ApiResponse handleResponse(HttpURLConnection connection, int responseCode) throws IOException {
        StringBuilder response = new StringBuilder();
        BufferedReader reader = null;

        try {
            if (responseCode >= 200 && responseCode < 300) {
                // Success response
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                String result = response.toString();
                Log.d(TAG, "Success response: " + result);
                return new ApiResponse(true, result, null);

            } else {
                // Error response
                if (connection.getErrorStream() != null) {
                    reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                String errorBody = response.toString();
                String errorMessage = parseErrorMessage(responseCode, errorBody);
                Log.e(TAG, "HTTP Error " + responseCode + ": " + errorMessage);

                return new ApiResponse(false, null, errorMessage);
            }

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.w(TAG, "Error closing reader", e);
                }
            }
        }
    }

    private static String parseErrorMessage(int responseCode, String errorBody) {
        try {
            if (errorBody != null && !errorBody.trim().isEmpty()) {
                JSONObject errorJson = new JSONObject(errorBody);
                if (errorJson.has("message")) {
                    return "HTTP " + responseCode + ": " + errorJson.getString("message");
                }
                if (errorJson.has("error")) {
                    return "HTTP " + responseCode + ": " + errorJson.getString("error");
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "Could not parse error response as JSON", e);
        }

        // Fallback to generic HTTP error messages
        switch (responseCode) {
            case 400: return "Yêu cầu không hợp lệ (400)";
            case 401: return "Chưa xác thực (401)";
            case 403: return "Không có quyền truy cập (403)";
            case 404: return "Không tìm thấy tài nguyên (404)";
            case 405: return "Phương thức không được hỗ trợ (405)";
            case 409: return "Xung đột dữ liệu (409)";
            case 422: return "Dữ liệu không thể xử lý (422)";
            case 429: return "Quá nhiều yêu cầu (429)";
            case 500: return "Lỗi máy chủ nội bộ (500)";
            case 502: return "Gateway lỗi (502)";
            case 503: return "Dịch vụ không khả dụng (503)";
            case 504: return "Gateway timeout (504)";
            default: return "Lỗi HTTP " + responseCode + (errorBody.isEmpty() ? "" : ": " + errorBody);
        }
    }

    private static String getErrorMessage(Exception e) {
        if (e instanceof SocketTimeoutException) {
            return "Timeout: Kết nối tới server quá chậm";
        } else if (e instanceof ConnectException) {
            return "Không thể kết nối tới server. Kiểm tra mạng và địa chỉ IP.";
        } else if (e instanceof UnknownHostException) {
            return "Không tìm thấy server. Kiểm tra địa chỉ IP: " + BASE_URL;
        } else if (e instanceof IOException) {
            return "Lỗi kết nối mạng: " + e.getMessage();
        } else {
            return "Lỗi không xác định: " + e.getMessage();
        }
    }

    private static boolean shouldRetry(String errorMessage) {
        if (errorMessage == null) return false;

        // Retry on network errors and 5xx server errors, but not on client errors (4xx)
        return errorMessage.contains("timeout") ||
                errorMessage.contains("connection") ||
                errorMessage.contains("500") ||
                errorMessage.contains("502") ||
                errorMessage.contains("503") ||
                errorMessage.contains("504");
    }

    // ===== UTILITY CLASSES =====

    private static class ApiResponse {
        final boolean isSuccess;
        final String data;
        final String errorMessage;

        ApiResponse(boolean isSuccess, String data, String errorMessage) {
            this.isSuccess = isSuccess;
            this.data = data;
            this.errorMessage = errorMessage;
        }
    }

    // ===== VALIDATION AND HELPER METHODS =====

    /**
     * Validate event data
     */
    private static boolean isValidEventData(JSONObject event) {
        return event != null &&
                !event.optString("_id", "").isEmpty() &&
                !event.optString("plate_text", "").isEmpty() &&
                !event.optString("timestamp", "").isEmpty();
    }

    /**
     * Validate vehicle data
     */
    private static boolean isValidVehicleData(JSONObject vehicle) {
        return vehicle != null &&
                !vehicle.optString("_id", "").isEmpty() &&
                !vehicle.optString("plateNumber", "").isEmpty();
    }

    /**
     * Validate parking log data
     */
    private static boolean isValidParkingLogData(JSONObject parkingLog) {
        return parkingLog != null &&
                !parkingLog.optString("_id", "").isEmpty() &&
                parkingLog.has("fee");
    }

    /**
     * Log request details for debugging
     */
    private static void logRequestDetails(String method, String url, String data) {
        Log.d(TAG, "=== API REQUEST ===");
        Log.d(TAG, "Method: " + method);
        Log.d(TAG, "URL: " + url);
        if (data != null && !data.isEmpty()) {
            Log.d(TAG, "Data: " + data);
        }
        Log.d(TAG, "==================");
    }

    // ===== UTILITY METHODS =====

    public static boolean isValidUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Format JSON response cho logging
     */
    public static String formatJsonForLogging(String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            return json.toString(2); // Indent with 2 spaces
        } catch (JSONException e) {
            return jsonString; // Return original if not valid JSON
        }
    }

    /**
     * Kiểm tra network connection
     */
    public static void checkNetworkConnection(OnResponseListener listener) {
        pingServer(new OnResponseListener() {
            @Override
            public void onSuccess(String response) {
                listener.onSuccess("Network connection is working: " + response);
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError("Network connection failed: " + errorMessage);
            }
        });
    }

    /**
     * Validate JSON string
     */
    public static boolean isValidJson(String jsonString) {
        try {
            new JSONObject(jsonString);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    /**
     * Tạo JSON object từ key-value pairs
     */
    public static String createJsonString(String... keyValuePairs) {
        try {
            JSONObject json = new JSONObject();
            for (int i = 0; i < keyValuePairs.length; i += 2) {
                if (i + 1 < keyValuePairs.length) {
                    json.put(keyValuePairs[i], keyValuePairs[i + 1]);
                }
            }
            return json.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON string", e);
            return "{}";
        }
    }

    // ===== CONVENIENCE METHODS =====

    public static void getDashboardData(OnDataReceivedListener statsListener, OnDataReceivedListener activitiesListener) {
        Log.d(TAG, "Getting complete dashboard data");

        getDashboardStats(new OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                Log.d(TAG, "Dashboard stats received");
                statsListener.onDataReceived(jsonData);
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Dashboard stats error: " + errorMessage);
                statsListener.onError(errorMessage);
            }
        });

        getRecentActivitiesWithLimit(10, new OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                Log.d(TAG, "Recent activities received");
                activitiesListener.onDataReceived(jsonData);
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Recent activities error: " + errorMessage);
                activitiesListener.onError(errorMessage);
            }
        });
    }

    public static void getPaymentManagementData(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting payment management data");
        getVehiclesNeedPaymentFromParkinglogs(listener);
    }

    // ===== SPECIAL METHODS FOR PARKING FLOW INTEGRATION =====

    /**
     * Lấy thông tin xe vào mới nhất (cho màn hình xe vào)
     */
    public static void getLatestVehicleEntry(OnDataReceivedListener listener) {
        String url = BASE_URL + "/events/latest-entry";
        Log.d(TAG, "Getting latest vehicle entry: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Lấy thông tin xe ra mới nhất (cho màn hình xe ra)
     */
    public static void getLatestVehicleExit(OnDataReceivedListener listener) {
        String url = BASE_URL + "/events/latest-exit";
        Log.d(TAG, "Getting latest vehicle exit: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Xử lý sự kiện xe vào (tạo event và parking log)
     */
    public static void processVehicleEntry(String entryData, OnResponseListener listener) {
        String url = BASE_URL + "/parking/process-entry";
        Log.d(TAG, "Processing vehicle entry: " + url);
        makePostRequest(url, entryData, listener);
    }

    /**
     * Xử lý sự kiện xe ra (cập nhật event và parking log)
     */
    public static void processVehicleExit(String exitData, OnResponseListener listener) {
        String url = BASE_URL + "/parking/process-exit";
        Log.d(TAG, "Processing vehicle exit: " + url);
        makePostRequest(url, exitData, listener);
    }

    /**
     * Tính toán phí đỗ xe
     */
    public static void calculateParkingFee(String feeData, OnDataReceivedListener listener) {
        String url = BASE_URL + "/parking/calculate-fee";
        Log.d(TAG, "Calculating parking fee: " + url);
        makePostRequest(url, feeData, new OnResponseListener() {
            @Override
            public void onSuccess(String response) {
                listener.onDataReceived(response);
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError(errorMessage);
            }
        });
    }

    /**
     * Lấy danh sách xe đang đỗ (có enter nhưng chưa exit)
     */
    public static void getCurrentlyParkedVehicles(OnDataReceivedListener listener) {
        String url = BASE_URL + "/parking/currently-parked";
        Log.d(TAG, "Getting currently parked vehicles: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Lấy lịch sử đỗ xe theo biển số
     */
    public static void getParkingHistoryByPlate(String plateText, OnDataReceivedListener listener) {
        if (plateText == null || plateText.trim().isEmpty()) {
            listener.onError("Plate text is empty");
            return;
        }

        String encodedPlate = plateText.trim().replace(" ", "%20");
        String url = BASE_URL + "/parking/history/" + encodedPlate;
        Log.d(TAG, "Getting parking history by plate: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Tìm kiếm xe theo nhiều tiêu chí
     */
    public static void searchVehicles(String searchQuery, OnDataReceivedListener listener) {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            listener.onError("Search query is empty");
            return;
        }

        try {
            JSONObject query = new JSONObject();
            query.put("query", searchQuery.trim());

            String url = BASE_URL + "/parking/search";
            Log.d(TAG, "Searching vehicles: " + url);
            makePostRequest(url, query.toString(), new OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    listener.onDataReceived(response);
                }

                @Override
                public void onError(String errorMessage) {
                    listener.onError(errorMessage);
                }
            });
        } catch (JSONException e) {
            listener.onError("Error creating search query: " + e.getMessage());
        }
    }

    // ===== SYSTEM STATUS AND HEALTH CHECK =====

    /**
     * Kiểm tra trạng thái hệ thống
     */
    public static void getSystemStatus(OnDataReceivedListener listener) {
        String url = BASE_URL + "/system/status";
        Log.d(TAG, "Getting system status: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Lấy thông tin phiên bản API
     */
    public static void getApiVersion(OnDataReceivedListener listener) {
        String url = BASE_URL + "/system/version";
        Log.d(TAG, "Getting API version: " + url);
        makeGetRequest(url, listener);
    }

    /**
     * Health check endpoint
     */
    public static void healthCheck(OnDataReceivedListener listener) {
        String url = BASE_URL + "/health";
        Log.d(TAG, "Performing health check: " + url);
        makeGetRequest(url, listener);
    }
}