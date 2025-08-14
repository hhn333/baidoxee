package com.example.baidoxee;

import android.os.AsyncTask;
import android.util.Log;
import org.json.JSONObject;
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
    private static final String BASE_URL = "http://192.168.1.23:3000/api";
    private static final String TAG = "ApiHelper";

    // Cấu hình timeout
    private static final int CONNECT_TIMEOUT = 15000; // 15 giây
    private static final int READ_TIMEOUT = 20000;    // 20 giây
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static int read_TIMEOUT;

    // Interface để handle callback
    public interface OnDataReceivedListener {
        void onDataReceived(String jsonData);
        void onError(String errorMessage);
    }

    public interface OnResponseListener {
        void onSuccess(String response);
        void onError(String errorMessage);
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

    // ===== ACTIVITIES ENDPOINTS =====
    public static void getActivities(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting all activities from /api/activities");
        makeGetRequest(BASE_URL + "/activities", listener);
    }

    public static void getVehiclesNeedPayment(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting vehicles needing payment from /api/vehicles/need-payment");
        makeGetRequest(BASE_URL + "/vehicles/need-payment", listener);
    }

    public static void getVehicleByLicensePlate(String plateNumber, OnDataReceivedListener listener) {
        if (plateNumber == null || plateNumber.trim().isEmpty()) {
            listener.onError("Biển số xe không được để trống");
            return;
        }

        String encodedPlate = plateNumber.trim().replace(" ", "%20");
        Log.d(TAG, "Getting vehicle by license plate: " + encodedPlate);
        makeGetRequest(BASE_URL + "/activities/license/" + encodedPlate, listener);
    }

    // PUT - Update activity với retry logic
    public static void updateActivity(String id, String jsonData, OnResponseListener listener) {
        updateActivityWithRetry(id, jsonData, listener, MAX_RETRY_ATTEMPTS);
    }

    public static void updateActivityWithRetry(String id, String jsonData, OnResponseListener listener, int maxRetries) {
        if (id == null || id.trim().isEmpty()) {
            listener.onError("ID hoạt động không được để trống");
            return;
        }

        if (jsonData == null || jsonData.trim().isEmpty()) {
            listener.onError("Dữ liệu JSON không được để trống");
            return;
        }

        // Validate JSON format
        try {
            new JSONObject(jsonData);
        } catch (JSONException e) {
            listener.onError("Định dạng JSON không hợp lệ: " + e.getMessage());
            return;
        }

        Log.d(TAG, "Updating activity: " + id + " (attempts left: " + maxRetries + ")");
        Log.d(TAG, "Update data: " + jsonData);

        makePutRequestWithRetry(BASE_URL + "/activities/" + id.trim(), jsonData, listener, maxRetries);
    }

    // ===== EVENTS ENDPOINTS =====
    public static void getEvents(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting all events from /api/events");
        makeGetRequest(BASE_URL + "/events", listener);
    }

    public static void getEnterPlates(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting enter plate numbers from /api/events/enter-plates");
        makeGetRequest(BASE_URL + "/events/enter-plates", listener);
    }

    public static void processEvents(OnResponseListener listener) {
        Log.d(TAG, "Processing events to create parking logs at /api/events/process");
        makePostRequest(BASE_URL + "/events/process", "", listener);
    }

    // ===== PRINT ENDPOINT =====
    public static void sendPrintCommand(String jsonData, OnResponseListener listener) {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            listener.onError("Dữ liệu in không được để trống");
            return;
        }

        // Validate print data
        try {
            JSONObject printObj = new JSONObject(jsonData);
            if (!printObj.has("bienSoXe") || printObj.getString("bienSoXe").trim().isEmpty()) {
                listener.onError("Thiếu thông tin biển số xe để in");
                return;
            }
        } catch (JSONException e) {
            listener.onError("Dữ liệu in không hợp lệ: " + e.getMessage());
            return;
        }

        Log.d(TAG, "Sending print command to /api/print");
        Log.d(TAG, "Print data: " + jsonData);
        makePostRequestWithRetry(BASE_URL + "/print", jsonData, listener, 2); // Chỉ retry 2 lần cho print
    }

    // ===== TEST ENDPOINTS =====
    public static void testConnection(OnDataReceivedListener listener) {
        Log.d(TAG, "Testing server connection at /api/test");
        makeGetRequest(BASE_URL + "/test", listener);
    }

    // ===== HTTP REQUEST METHODS WITH IMPROVED ERROR HANDLING =====

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
            connection.setReadTimeout(read_TIMEOUT);
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

    // ===== UTILITY METHODS =====

    public static boolean isValidUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (Exception e) {
            return false;
        }
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
        getVehiclesNeedPayment(listener);
    }

    // Legacy compatibility
    public static void getParkingEvents(OnDataReceivedListener listener) {
        getActivities(listener);
    }

    // Transaction methods
    public static void getTransactions(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting all transactions from /api/transactions");
        makeGetRequest(BASE_URL + "/transactions", listener);
    }
}