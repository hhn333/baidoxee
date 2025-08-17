package com.example.baidoxee;

import android.os.AsyncTask;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.*;
import java.net.*;

public class ApiHelper {
    private static final String BASE_URL = "http://192.168.1.191:3000/api";
    private static final String TAG = "ApiHelper";
    private static final int CONNECT_TIMEOUT = 15000, READ_TIMEOUT = 20000, MAX_RETRY_ATTEMPTS = 3;

    // Legacy redirects
    public static void getEnterEventForPlate(String plateNumber, String exitEventTimestamp, OnDataReceivedListener l) { getEnterEventByPlateBeforeTime(plateNumber, exitEventTimestamp, l); }
    public static void processPayment(String updateId, String updateData, OnResponseListener l) { updateParkingLogPayment(updateId, updateData, l); }
    public static void getVehicleInfoFromEvents(String bienSo, OnDataReceivedListener l) { getVehicleByLicensePlate(bienSo, l); }
    public static void getVehicleInfoByEventId(String enterEventId, OnDataReceivedListener l) { getParkingLogByVehicleId(enterEventId, l); }
    public static void getCompletedVehicleByVehicleId(String vehicleId, OnDataReceivedListener l) { getParkingLogByVehicleId(vehicleId, l); }
    public static void getCompletedVehicleByPlateText(String plateText, OnDataReceivedListener l) { getParkingLogByPlateNumber(plateText, l); }
    public static void getCompletedVehiclesForPayment(OnDataReceivedListener l) { getVehiclesNeedPayment(l); }
    public static void updateParkingLogPayment(String id, String data, OnResponseListener l) { updateParkingLog(id, data, l); }

    public interface OnDataReceivedListener { void onDataReceived(String jsonData); void onError(String errorMessage); }
    public interface OnResponseListener { void onSuccess(String response); void onError(String errorMessage); }

    public static void updateParkingLog(String activityId, String updateData, OnResponseListener l) {
        if (activityId == null || activityId.trim().isEmpty()) { l.onError("Activity ID không được để trống"); return; }
        if (updateData == null || updateData.trim().isEmpty()) { l.onError("Dữ liệu JSON không được để trống"); return; }
        try { new JSONObject(updateData); } catch (JSONException e) { l.onError("Định dạng JSON không hợp lệ: " + e.getMessage()); return; }
        makePutRequestWithRetry(BASE_URL + "/parkinglogs/" + activityId.trim(), updateData, l, MAX_RETRY_ATTEMPTS);
    }

    public static void getParkingLogByPlateNumber(String bienSo, OnDataReceivedListener l) {
        if (bienSo == null || bienSo.trim().isEmpty()) { l.onError("Biển số xe không được để trống"); return; }
        makeGetRequest(BASE_URL + "/parkinglogs/license/" + bienSo.trim().replace(" ", "%20"), l);
    }

    public static void getAllActiveParkingLogs(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/parkinglogs", l); }
    public static void getDashboardStats(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/dashboard-stats", l); }
    public static void getRecentActivities(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/recent-activities", l); }
    public static void getRecentActivitiesWithLimit(int limit, OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/recent-activities?limit=" + limit, l); }
    public static void getRealtimeStatus(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/realtime-status", l); }
    public static void getActivities(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/activities", l); }
    public static void getVehiclesNeedPayment(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/parkinglogs/need-payment", l); }

    public static void getVehicleByLicensePlate(String plateNumber, OnDataReceivedListener l) {
        if (plateNumber == null || plateNumber.trim().isEmpty()) { l.onError("Biển số xe không được để trống"); return; }
        makeGetRequest(BASE_URL + "/parkinglogs/license/" + plateNumber.trim().replace(" ", "%20"), l);
    }

    public static void updateActivity(String id, String jsonData, OnResponseListener l) { updateActivityWithRetry(id, jsonData, l, MAX_RETRY_ATTEMPTS); }
    public static void updateActivityWithRetry(String id, String jsonData, OnResponseListener l, int maxRetries) {
        if (id == null || id.trim().isEmpty()) { l.onError("ID hoạt động không được để trống"); return; }
        if (jsonData == null || jsonData.trim().isEmpty()) { l.onError("Dữ liệu JSON không được để trống"); return; }
        try { new JSONObject(jsonData); } catch (JSONException e) { l.onError("Định dạng JSON không hợp lệ: " + e.getMessage()); return; }
        makePutRequestWithRetry(BASE_URL + "/parkinglogs/" + id.trim(), jsonData, l, maxRetries);
    }

    public static void getEvents(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/events", l); }
    public static void getEnterPlates(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/events/enter-plates", l); }
    public static void processEvents(OnResponseListener l) { makePostRequest(BASE_URL + "/events/process", "", l); }
    public static void getParkingLogs(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/parkinglogs", l); }
    public static void getParkingLogsNeedPayment(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/parkinglogs/need-payment", l); }
    public static void getPaidParkingLogs(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/parkinglogs/paid", l); }
    public static void getUnpaidParkingLogs(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/parkinglogs/unpaid", l); }

    public static void getParkingLogsByDate(String date, OnDataReceivedListener l) {
        if (date == null || date.trim().isEmpty()) { l.onError("Ngày không được để trống"); return; }
        makeGetRequest(BASE_URL + "/parkinglogs/by-date/" + date.trim(), l);
    }

    public static void getParkingLogsStats(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/parkinglogs/stats", l); }
    public static void getLatestExitEvent(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/events/latest-exit", l); }

    public static void getVehicleByPlateNumber(String plateNumber, OnDataReceivedListener l) {
        if (plateNumber == null || plateNumber.trim().isEmpty()) { l.onError("Plate number is empty"); return; }
        makeGetRequest(BASE_URL + "/vehicles/by-plate/" + plateNumber.trim().replace(" ", "%20"), l);
    }

    public static void getEnterEventByPlateBeforeTime(String plateNumber, String beforeTime, OnDataReceivedListener l) {
        if (plateNumber == null || plateNumber.trim().isEmpty()) { l.onError("Plate number is empty"); return; }
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("plateNumber", plateNumber.trim());
            if (beforeTime != null && !beforeTime.trim().isEmpty()) requestBody.put("beforeTime", beforeTime.trim());
            makePostRequest(BASE_URL + "/events/enter-before", requestBody.toString(), new OnResponseListener() {
                public void onSuccess(String response) { l.onDataReceived(response); }
                public void onError(String error) { l.onError(error); }
            });
        } catch (JSONException e) { l.onError("Error creating request: " + e.getMessage()); }
    }

    public static void getParkingLogByVehicleId(String vehicleId, OnDataReceivedListener l) {
        if (vehicleId == null || vehicleId.trim().isEmpty()) { l.onError("Vehicle ID is empty"); return; }
        makeGetRequest(BASE_URL + "/parkinglogs/by-vehicle/" + vehicleId.trim(), l);
    }

    public static void getLatestExitEventByPlate(String plateNumber, OnDataReceivedListener l) {
        if (plateNumber == null || plateNumber.trim().isEmpty()) { l.onError("Plate number is empty"); return; }
        makeGetRequest(BASE_URL + "/events/latest-exit-by-plate/" + plateNumber.trim().replace(" ", "%20"), l);
    }

    public static void getExitEventByEnterId(String enterEventId, OnDataReceivedListener l) {
        if (enterEventId == null || enterEventId.trim().isEmpty()) { l.onError("Enter event ID is empty"); return; }
        makeGetRequest(BASE_URL + "/events/exit-by-enter/" + enterEventId.trim(), l);
    }

    public static void createParkingLog(String parkingLogData, String unused, OnResponseListener l) {
        if (parkingLogData == null || parkingLogData.trim().isEmpty()) { l.onError("Parking log data is empty"); return; }
        makePostRequest(BASE_URL + "/parkinglogs/create", parkingLogData, l);
    }

    public static void deleteParkingLog(String parkingLogId, OnResponseListener l) {
        if (parkingLogId == null || parkingLogId.trim().isEmpty()) { l.onError("Parking log ID is empty"); return; }
        makeDeleteRequest(BASE_URL + "/parkinglogs/" + parkingLogId.trim(), l);
    }

    public static void processEvents(String activityId, String updateData, OnResponseListener l) {
        if (activityId == null || activityId.trim().isEmpty()) { l.onError("Activity ID is empty"); return; }
        if (updateData == null || updateData.trim().isEmpty()) { l.onError("Update data is empty"); return; }
        makePutRequestWithRetry(BASE_URL + "/parkinglogs/" + activityId.trim(), updateData, l, MAX_RETRY_ATTEMPTS);
    }

    public static void sendPrintCommand(String jsonData, OnResponseListener l) {
        if (jsonData == null || jsonData.trim().isEmpty()) { l.onError("Dữ liệu in không được để trống"); return; }
        try {
            JSONObject printObj = new JSONObject(jsonData);
            if (!printObj.has("bienSoXe") || printObj.getString("bienSoXe").trim().isEmpty()) { l.onError("Thiếu thông tin biển số xe để in"); return; }
        } catch (JSONException e) { l.onError("Dữ liệu in không hợp lệ: " + e.getMessage()); return; }
        makePostRequestWithRetry(BASE_URL + "/print/invoice", jsonData, l, 2);
    }

    public static void testConnection(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/test", l); }

    public static void makeGetRequest(String urlString, OnDataReceivedListener l) {
        new AsyncTask<Void, Void, ApiResponse>() {
            protected ApiResponse doInBackground(Void... voids) { return executeGetRequest(urlString); }
            protected void onPostExecute(ApiResponse r) { if (r.isSuccess) l.onDataReceived(r.data); else l.onError(r.errorMessage); }
        }.execute();
    }

    private static void makePostRequest(String urlString, String jsonData, OnResponseListener l) { makePostRequestWithRetry(urlString, jsonData, l, MAX_RETRY_ATTEMPTS); }
    private static void makePostRequestWithRetry(String urlString, String jsonData, OnResponseListener l, int maxRetries) {
        new AsyncTask<Void, Void, ApiResponse>() {
            protected ApiResponse doInBackground(Void... voids) { return executePostRequestWithRetry(urlString, jsonData, maxRetries); }
            protected void onPostExecute(ApiResponse r) { if (r.isSuccess) l.onSuccess(r.data); else l.onError(r.errorMessage); }
        }.execute();
    }

    private static void makePutRequestWithRetry(String urlString, String jsonData, OnResponseListener l, int maxRetries) {
        new AsyncTask<Void, Void, ApiResponse>() {
            protected ApiResponse doInBackground(Void... voids) { return executePutRequestWithRetry(urlString, jsonData, maxRetries); }
            protected void onPostExecute(ApiResponse r) { if (r.isSuccess) l.onSuccess(r.data); else l.onError(r.errorMessage); }
        }.execute();
    }

    private static void makeDeleteRequest(String urlString, OnResponseListener l) {
        new AsyncTask<Void, Void, ApiResponse>() {
            protected ApiResponse doInBackground(Void... voids) { return executeDeleteRequest(urlString); }
            protected void onPostExecute(ApiResponse r) { if (r.isSuccess) l.onSuccess(r.data); else l.onError(r.errorMessage); }
        }.execute();
    }

    private static ApiResponse executeGetRequest(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "BaiDoXeAndroidApp/1.0");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setUseCaches(false);
            return handleResponse(connection, connection.getResponseCode());
        } catch (Exception e) { return new ApiResponse(false, null, getErrorMessage(e)); }
        finally { if (connection != null) connection.disconnect(); }
    }

    private static ApiResponse executePostRequestWithRetry(String urlString, String jsonData, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            ApiResponse response = executePostRequest(urlString, jsonData);
            if (response.isSuccess || !shouldRetry(response.errorMessage) || attempt == maxRetries) return response;
            try { Thread.sleep(1000 * attempt); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return new ApiResponse(false, null, "Request interrupted"); }
        }
        return new ApiResponse(false, null, "Max retries exceeded");
    }

    private static ApiResponse executePutRequestWithRetry(String urlString, String jsonData, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            ApiResponse response = executePutRequest(urlString, jsonData);
            if (response.isSuccess || !shouldRetry(response.errorMessage) || attempt == maxRetries) return response;
            try { Thread.sleep(1000 * attempt); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return new ApiResponse(false, null, "Request interrupted"); }
        }
        return new ApiResponse(false, null, "Max retries exceeded");
    }

    private static ApiResponse executePostRequest(String urlString, String jsonData) { return executeRequest(urlString, "POST", jsonData); }
    private static ApiResponse executePutRequest(String urlString, String jsonData) { return executeRequest(urlString, "PUT", jsonData); }
    private static ApiResponse executeDeleteRequest(String urlString) { return executeRequest(urlString, "DELETE", null); }

    private static ApiResponse executeRequest(String urlString, String method, String jsonData) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "BaiDoXeAndroidApp/1.0");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setUseCaches(false);

            if (jsonData != null && !jsonData.isEmpty() && ("POST".equals(method) || "PUT".equals(method))) {
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonData.getBytes("UTF-8");
                    os.write(input, 0, input.length);
                    os.flush();
                }
            }

            return handleResponse(connection, connection.getResponseCode());
        } catch (Exception e) { return new ApiResponse(false, null, getErrorMessage(e)); }
        finally { if (connection != null) connection.disconnect(); }
    }

    private static ApiResponse handleResponse(HttpURLConnection connection, int responseCode) throws IOException {
        StringBuilder response = new StringBuilder();
        BufferedReader reader = null;
        try {
            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                String line; while ((line = reader.readLine()) != null) response.append(line);
                return new ApiResponse(true, response.toString(), null);
            } else {
                if (connection.getErrorStream() != null) {
                    reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "UTF-8"));
                    String line; while ((line = reader.readLine()) != null) response.append(line);
                }
                return new ApiResponse(false, null, parseErrorMessage(responseCode, response.toString()));
            }
        } finally { if (reader != null) try { reader.close(); } catch (IOException e) {} }
    }

    private static String parseErrorMessage(int responseCode, String errorBody) {
        try {
            if (errorBody != null && !errorBody.trim().isEmpty()) {
                JSONObject errorJson = new JSONObject(errorBody);
                if (errorJson.has("message")) return "HTTP " + responseCode + ": " + errorJson.getString("message");
                if (errorJson.has("error")) return "HTTP " + responseCode + ": " + errorJson.getString("error");
            }
        } catch (JSONException e) {}

        switch (responseCode) {
            case 400: return "Yêu cầu không hợp lệ (400)"; case 401: return "Chưa xác thực (401)"; case 403: return "Không có quyền truy cập (403)";
            case 404: return "Không tìm thấy tài nguyên (404)"; case 405: return "Phương thức không được hỗ trợ (405)"; case 409: return "Xung đột dữ liệu (409)";
            case 422: return "Dữ liệu không thể xử lý (422)"; case 429: return "Quá nhiều yêu cầu (429)"; case 500: return "Lỗi máy chủ nội bộ (500)";
            case 502: return "Gateway lỗi (502)"; case 503: return "Dịch vụ không khả dụng (503)"; case 504: return "Gateway timeout (504)";
            default: return "Lỗi HTTP " + responseCode + (errorBody.isEmpty() ? "" : ": " + errorBody);
        }
    }

    private static String getErrorMessage(Exception e) {
        if (e instanceof SocketTimeoutException) return "Timeout: Kết nối tới server quá chậm";
        if (e instanceof ConnectException) return "Không thể kết nối tới server. Kiểm tra mạng và địa chỉ IP.";
        if (e instanceof UnknownHostException) return "Không tìm thấy server. Kiểm tra địa chỉ IP: " + BASE_URL;
        if (e instanceof IOException) return "Lỗi kết nối mạng: " + e.getMessage();
        return "Lỗi không xác định: " + e.getMessage();
    }

    private static boolean shouldRetry(String errorMessage) {
        if (errorMessage == null) return false;
        return errorMessage.contains("timeout") || errorMessage.contains("connection") || errorMessage.contains("500") || errorMessage.contains("502") || errorMessage.contains("503") || errorMessage.contains("504");
    }

    private static class ApiResponse {
        final boolean isSuccess; final String data; final String errorMessage;
        ApiResponse(boolean isSuccess, String data, String errorMessage) { this.isSuccess = isSuccess; this.data = data; this.errorMessage = errorMessage; }
    }

    // Utility methods
    public static boolean isValidUrl(String url) { try { new URL(url); return true; } catch (Exception e) { return false; } }
    public static void pingServer(OnResponseListener l) { testConnection(new OnDataReceivedListener() { public void onDataReceived(String jsonData) { l.onSuccess("Server connection successful: " + jsonData); } public void onError(String errorMessage) { l.onError("Server connection failed: " + errorMessage); } }); }
    public static void getDashboardData(OnDataReceivedListener statsListener, OnDataReceivedListener activitiesListener) { getDashboardStats(statsListener); getRecentActivitiesWithLimit(10, activitiesListener); }
    public static void getPaymentManagementData(OnDataReceivedListener l) { getVehiclesNeedPayment(l); }
    public static void getParkingEvents(OnDataReceivedListener l) { getParkingLogs(l); }
    public static void getTransactions(OnDataReceivedListener l) { makeGetRequest(BASE_URL + "/transactions", l); }

    public static void searchParkingLogs(String plateNumber, String status, String paymentStatus, OnDataReceivedListener l) {
        StringBuilder urlBuilder = new StringBuilder(BASE_URL + "/parkinglogs?");
        if (plateNumber != null && !plateNumber.trim().isEmpty()) urlBuilder.append("plate=").append(plateNumber.trim().replace(" ", "%20")).append("&");
        if (status != null && !status.trim().isEmpty()) urlBuilder.append("status=").append(status.trim()).append("&");
        if (paymentStatus != null && !paymentStatus.trim().isEmpty()) urlBuilder.append("paymentStatus=").append(paymentStatus.trim()).append("&");
        String url = urlBuilder.toString(); if (url.endsWith("&")) url = url.substring(0, url.length() - 1);
        makeGetRequest(url, l);
    }

    public static void bulkUpdateParkingLogs(String jsonData, OnResponseListener l) {
        if (jsonData == null || jsonData.trim().isEmpty()) { l.onError("Bulk update data is empty"); return; }
        makePostRequest(BASE_URL + "/parkinglogs/bulk-update", jsonData, l);
    }

    public static void exportParkingLogs(String startDate, String endDate, String format, OnDataReceivedListener l) {
        StringBuilder urlBuilder = new StringBuilder(BASE_URL + "/parkinglogs/export?");
        if (startDate != null && !startDate.trim().isEmpty()) urlBuilder.append("startDate=").append(startDate.trim()).append("&");
        if (endDate != null && !endDate.trim().isEmpty()) urlBuilder.append("endDate=").append(endDate.trim()).append("&");
        if (format != null && !format.trim().isEmpty()) urlBuilder.append("format=").append(format.trim()).append("&");
        String url = urlBuilder.toString(); if (url.endsWith("&")) url = url.substring(0, url.length() - 1);
        makeGetRequest(url, l);
    }
}