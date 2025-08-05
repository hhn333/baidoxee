package com.example.baidoxee;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiHelper {

    // IP address của bạn
    private static final String BASE_URL = "http://192.168.102.171:3000/api";
    private static final String TAG = "ApiHelper";

    // Interface để handle callback
    public interface OnDataReceivedListener {
        void onDataReceived(String jsonData);
        void onError(String errorMessage);
    }

    public interface OnResponseListener {
        void onSuccess(String response);
        void onError(String errorMessage);
    }

    // ===== ACTIVITIES ENDPOINTS =====

    // GET all activities
    public static void getActivities(OnDataReceivedListener listener) {
        makeGetRequest(BASE_URL + "/activities", listener);
    }

    // GET activity by ID
    public static void getActivityById(String id, OnDataReceivedListener listener) {
        makeGetRequest(BASE_URL + "/activities/" + id, listener);
    }

    // POST - Create new activity
    public static void createActivity(String jsonData, OnResponseListener listener) {
        makePostRequest(BASE_URL + "/activities", jsonData, listener);
    }

    // PUT - Update activity
    public static void updateActivity(String id, String jsonData, OnResponseListener listener) {
        makePutRequest(BASE_URL + "/activities/" + id, jsonData, listener);
    }

    // DELETE - Delete activity
    public static void deleteActivity(String id, OnResponseListener listener) {
        makeDeleteRequest(BASE_URL + "/activities/" + id, listener);
    }

    // ===== VEHICLE ENDPOINTS =====

    // Lấy thông tin xe theo biển số để thanh toán
    public static void getVehicleByLicensePlate(String bienSoXe, OnDataReceivedListener listener) {
        makeGetRequest(BASE_URL + "/activities/license/" + bienSoXe, listener);
    }

    // Cập nhật thời gian ra và trạng thái xe
    public static void updateVehicleCheckout(String activityId, String jsonData, OnResponseListener listener) {
        makePutRequest(BASE_URL + "/activities/" + activityId, jsonData, listener);
    }

    // Xe vào bãi
    public static void vehicleCheckIn(String jsonData, OnResponseListener listener) {
        makePostRequest(BASE_URL + "/vehicle-checkin", jsonData, listener);
    }

    // Xe ra khỏi bãi
    public static void vehicleCheckOut(String plateNumber, OnResponseListener listener) {
        makePutRequest(BASE_URL + "/vehicle-checkout/" + plateNumber, "", listener);
    }

    // ===== PAYMENT ENDPOINTS =====

    // Lấy danh sách xe cần thanh toán
    public static void getVehiclesNeedPayment(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting vehicles needing payment");
        makeGetRequest(BASE_URL + "/vehicles/need-payment", listener);
    }

    // Tạo transaction thanh toán
    public static void createTransaction(String jsonData, OnResponseListener listener) {
        makePostRequest(BASE_URL + "/transactions", jsonData, listener);
    }

    // Lấy danh sách transactions
    public static void getTransactions(OnDataReceivedListener listener) {
        makeGetRequest(BASE_URL + "/transactions", listener);
    }

    // ===== SECTION/ZONE ENDPOINTS =====

    // GET all sections/zones
    public static void getSections(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting all parking sections");
        makeGetRequest(BASE_URL + "/sections", listener);
    }

    // GET section by ID
    public static void getSectionById(String sectionId, OnDataReceivedListener listener) {
        Log.d(TAG, "Getting section by ID: " + sectionId);
        makeGetRequest(BASE_URL + "/sections/" + sectionId, listener);
    }

    // POST - Create new section
    public static void createSection(String jsonData, OnResponseListener listener) {
        Log.d(TAG, "Creating new section");
        makePostRequest(BASE_URL + "/sections", jsonData, listener);
    }

    // PUT - Update section
    public static void updateSection(String sectionId, String jsonData, OnResponseListener listener) {
        Log.d(TAG, "Updating section: " + sectionId);
        makePutRequest(BASE_URL + "/sections/" + sectionId, jsonData, listener);
    }

    // DELETE - Delete section
    public static void deleteSection(String sectionId, OnResponseListener listener) {
        Log.d(TAG, "Deleting section: " + sectionId);
        makeDeleteRequest(BASE_URL + "/sections/" + sectionId, listener);
    }

    // GET parking slots by section
    public static void getSlotsBySection(String sectionId, OnDataReceivedListener listener) {
        Log.d(TAG, "Getting slots for section: " + sectionId);
        makeGetRequest(BASE_URL + "/sections/" + sectionId + "/slots", listener);
    }

    // GET available slots in a section
    public static void getAvailableSlotsBySection(String sectionId, OnDataReceivedListener listener) {
        Log.d(TAG, "Getting available slots for section: " + sectionId);
        makeGetRequest(BASE_URL + "/sections/" + sectionId + "/slots/available", listener);
    }

    // GET occupied slots in a section
    public static void getOccupiedSlotsBySection(String sectionId, OnDataReceivedListener listener) {
        Log.d(TAG, "Getting occupied slots for section: " + sectionId);
        makeGetRequest(BASE_URL + "/sections/" + sectionId + "/slots/occupied", listener);
    }

    // GET section statistics
    public static void getSectionStatistics(String sectionId, OnDataReceivedListener listener) {
        Log.d(TAG, "Getting statistics for section: " + sectionId);
        makeGetRequest(BASE_URL + "/sections/" + sectionId + "/statistics", listener);
    }

    // GET all sections with their statistics
    public static void getAllSectionsWithStats(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting all sections with statistics");
        makeGetRequest(BASE_URL + "/sections/statistics", listener);
    }

    // ===== PARKING SLOT ENDPOINTS =====

    // GET all parking slots
    public static void getParkingSlots(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting all parking slots");
        makeGetRequest(BASE_URL + "/parking-slots", listener);
    }

    // GET parking slot by ID
    public static void getParkingSlotById(String slotId, OnDataReceivedListener listener) {
        Log.d(TAG, "Getting parking slot by ID: " + slotId);
        makeGetRequest(BASE_URL + "/parking-slots/" + slotId, listener);
    }

    // POST - Create new parking slot
    public static void createParkingSlot(String jsonData, OnResponseListener listener) {
        Log.d(TAG, "Creating new parking slot");
        makePostRequest(BASE_URL + "/parking-slots", jsonData, listener);
    }

    // PUT - Update parking slot
    public static void updateParkingSlot(String slotId, String jsonData, OnResponseListener listener) {
        Log.d(TAG, "Updating parking slot: " + slotId);
        makePutRequest(BASE_URL + "/parking-slots/" + slotId, jsonData, listener);
    }

    // DELETE - Delete parking slot
    public static void deleteParkingSlot(String slotId, OnResponseListener listener) {
        Log.d(TAG, "Deleting parking slot: " + slotId);
        makeDeleteRequest(BASE_URL + "/parking-slots/" + slotId, listener);
    }

    // GET available parking slots
    public static void getAvailableParkingSlots(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting available parking slots");
        makeGetRequest(BASE_URL + "/parking-slots/available", listener);
    }

    // GET occupied parking slots
    public static void getOccupiedParkingSlots(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting occupied parking slots");
        makeGetRequest(BASE_URL + "/parking-slots/occupied", listener);
    }

    // ===== POS/PRINT ENDPOINTS =====

    // Gửi lệnh in hóa đơn
    public static void sendPrintCommand(String jsonData, OnResponseListener listener) {
        makePostRequest(BASE_URL + "/pos/print", jsonData, listener);
    }

    // ===== LEGACY METHOD - Deprecated =====

    /**
     * @deprecated Sử dụng getVehiclesNeedPayment() thay thế
     * Lấy danh sách xe cần thanh toán (sử dụng endpoint activities hiện có)
     * Sẽ filter phía client để tìm xe cần thanh toán
     */
    @Deprecated
    public static void getVehiclesNeedPaymentLegacy(OnDataReceivedListener listener) {
        Log.d(TAG, "Getting all activities to filter vehicles needing payment (DEPRECATED)");
        makeGetRequest(BASE_URL + "/activities", new OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                Log.d(TAG, "Raw activities data: " + jsonData);

                // Kiểm tra nếu response là HTML (lỗi 404)
                if (jsonData.trim().startsWith("<!DOCTYPE") || jsonData.trim().startsWith("<html")) {
                    listener.onError("Server trả về trang HTML thay vì JSON - có thể endpoint không tồn tại");
                    return;
                }

                try {
                    // Filter dữ liệu để chỉ lấy xe cần thanh toán
                    String filteredData = filterVehiclesNeedPayment(jsonData);
                    listener.onDataReceived(filteredData);
                } catch (Exception e) {
                    Log.e(TAG, "Error filtering vehicles need payment", e);
                    listener.onError("Lỗi xử lý dữ liệu: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError(errorMessage);
            }
        });
    }

    /**
     * Filter xe cần thanh toán từ tất cả activities
     */
    private static String filterVehiclesNeedPayment(String allActivitiesJson) throws Exception {
        org.json.JSONArray allActivities;

        // Xử lý response - có thể là array hoặc object chứa array
        if (allActivitiesJson.trim().startsWith("[")) {
            allActivities = new org.json.JSONArray(allActivitiesJson);
        } else {
            org.json.JSONObject response = new org.json.JSONObject(allActivitiesJson);
            if (response.has("data")) {
                allActivities = response.getJSONArray("data");
            } else if (response.has("success") && response.getBoolean("success")) {
                allActivities = response.getJSONArray("data");
            } else {
                allActivities = new org.json.JSONArray();
            }
        }

        // Filter để tìm xe cần thanh toán
        org.json.JSONArray vehiclesNeedPayment = new org.json.JSONArray();

        for (int i = 0; i < allActivities.length(); i++) {
            org.json.JSONObject vehicle = allActivities.getJSONObject(i);

            // Điều kiện: xe đã ra (có thoiGianRa) và chưa thanh toán
            boolean hasExitTime = vehicle.has("thoiGianRa") && !vehicle.isNull("thoiGianRa")
                    && !vehicle.getString("thoiGianRa").isEmpty();

            boolean notPaid = true; // Mặc định chưa thanh toán
            if (vehicle.has("daThanhToan")) {
                notPaid = !vehicle.getBoolean("daThanhToan");
            }

            // Kiểm tra trạng thái nếu có
            boolean needPayment = true;
            if (vehicle.has("trangThai")) {
                String status = vehicle.getString("trangThai");
                needPayment = !status.equals("da_thanh_toan");
            }

            if (hasExitTime && notPaid && needPayment) {
                vehiclesNeedPayment.put(vehicle);
            }
        }

        // Tạo response object giống format API
        org.json.JSONObject result = new org.json.JSONObject();
        result.put("success", true);
        result.put("data", vehiclesNeedPayment);
        result.put("message", "Found " + vehiclesNeedPayment.length() + " vehicles needing payment");

        Log.d(TAG, "Filtered result: " + result.toString());
        return result.toString();
    }

    // ===== HTTP REQUEST METHODS =====

    // Method chung cho GET request
    private static void makeGetRequest(String urlString, OnDataReceivedListener listener) {
        new AsyncTask<Void, Void, String>() {
            private String error = null;

            @Override
            protected String doInBackground(Void... voids) {
                try {
                    Log.d(TAG, "Making GET request to: " + urlString);

                    URL url = new URL(urlString);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setConnectTimeout(10000); // 10 seconds timeout
                    connection.setReadTimeout(15000);    // 15 seconds timeout
                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "Response code: " + responseCode);

                    BufferedReader reader;
                    StringBuilder response = new StringBuilder();
                    String line;

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();
                        connection.disconnect();

                        String result = response.toString();
                        Log.d(TAG, "Response: " + result);
                        return result;
                    } else {
                        // Đọc error response
                        if (connection.getErrorStream() != null) {
                            reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                            while ((line = reader.readLine()) != null) {
                                response.append(line);
                            }
                            reader.close();
                        }

                        error = "HTTP Error " + responseCode + ": " + response.toString();
                        Log.e(TAG, error);
                        return null;
                    }
                } catch (Exception e) {
                    error = "Network Error: " + e.getMessage();
                    Log.e(TAG, error, e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (result != null) {
                    listener.onDataReceived(result);
                } else {
                    listener.onError(error != null ? error : "Unknown error occurred");
                }
            }
        }.execute();
    }

    // Method chung cho POST request
    private static void makePostRequest(String urlString, String jsonData, OnResponseListener listener) {
        new AsyncTask<Void, Void, String>() {
            private String error = null;

            @Override
            protected String doInBackground(Void... voids) {
                try {
                    Log.d(TAG, "Making POST request to: " + urlString);
                    Log.d(TAG, "POST data: " + jsonData);

                    URL url = new URL(urlString);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(15000);

                    // Gửi dữ liệu JSON
                    if (jsonData != null && !jsonData.isEmpty()) {
                        OutputStream outputStream = connection.getOutputStream();
                        outputStream.write(jsonData.getBytes("UTF-8"));
                        outputStream.flush();
                        outputStream.close();
                    }

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "POST Response code: " + responseCode);

                    BufferedReader reader;
                    StringBuilder response = new StringBuilder();
                    String line;

                    if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();
                        connection.disconnect();

                        String result = response.toString();
                        Log.d(TAG, "POST Response: " + result);
                        return result;
                    } else {
                        if (connection.getErrorStream() != null) {
                            reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                            while ((line = reader.readLine()) != null) {
                                response.append(line);
                            }
                            reader.close();
                        }

                        error = "HTTP Error " + responseCode + ": " + response.toString();
                        Log.e(TAG, error);
                        return null;
                    }
                } catch (Exception e) {
                    error = "Network Error: " + e.getMessage();
                    Log.e(TAG, error, e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (result != null) {
                    listener.onSuccess(result);
                } else {
                    listener.onError(error != null ? error : "Unknown error occurred");
                }
            }
        }.execute();
    }

    // Method chung cho PUT request
    private static void makePutRequest(String urlString, String jsonData, OnResponseListener listener) {
        new AsyncTask<Void, Void, String>() {
            private String error = null;

            @Override
            protected String doInBackground(Void... voids) {
                try {
                    Log.d(TAG, "Making PUT request to: " + urlString);
                    Log.d(TAG, "PUT data: " + jsonData);

                    URL url = new URL(urlString);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("PUT");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(15000);

                    // Gửi dữ liệu JSON
                    if (jsonData != null && !jsonData.isEmpty()) {
                        OutputStream outputStream = connection.getOutputStream();
                        outputStream.write(jsonData.getBytes("UTF-8"));
                        outputStream.flush();
                        outputStream.close();
                    }

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "PUT Response code: " + responseCode);

                    BufferedReader reader;
                    StringBuilder response = new StringBuilder();
                    String line;

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();
                        connection.disconnect();

                        String result = response.toString();
                        Log.d(TAG, "PUT Response: " + result);
                        return result;
                    } else {
                        if (connection.getErrorStream() != null) {
                            reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                            while ((line = reader.readLine()) != null) {
                                response.append(line);
                            }
                            reader.close();
                        }

                        error = "HTTP Error " + responseCode + ": " + response.toString();
                        Log.e(TAG, error);
                        return null;
                    }
                } catch (Exception e) {
                    error = "Network Error: " + e.getMessage();
                    Log.e(TAG, error, e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (result != null) {
                    listener.onSuccess(result);
                } else {
                    listener.onError(error != null ? error : "Unknown error occurred");
                }
            }
        }.execute();
    }

    // Method chung cho DELETE request
    private static void makeDeleteRequest(String urlString, OnResponseListener listener) {
        new AsyncTask<Void, Void, String>() {
            private String error = null;

            @Override
            protected String doInBackground(Void... voids) {
                try {
                    Log.d(TAG, "Making DELETE request to: " + urlString);

                    URL url = new URL(urlString);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("DELETE");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(15000);
                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "DELETE Response code: " + responseCode);

                    BufferedReader reader;
                    StringBuilder response = new StringBuilder();
                    String line;

                    if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                        if (connection.getInputStream() != null) {
                            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                            while ((line = reader.readLine()) != null) {
                                response.append(line);
                            }
                            reader.close();
                        }
                        connection.disconnect();

                        String result = response.toString();
                        Log.d(TAG, "DELETE Response: " + result);
                        return result.isEmpty() ? "Delete successful" : result;
                    } else {
                        if (connection.getErrorStream() != null) {
                            reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                            while ((line = reader.readLine()) != null) {
                                response.append(line);
                            }
                            reader.close();
                        }

                        error = "HTTP Error " + responseCode + ": " + response.toString();
                        Log.e(TAG, error);
                        return null;
                    }
                } catch (Exception e) {
                    error = "Network Error: " + e.getMessage();
                    Log.e(TAG, error, e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (result != null) {
                    listener.onSuccess(result);
                } else {
                    listener.onError(error != null ? error : "Unknown error occurred");
                }
            }
        }.execute();
    }

    // ===== UTILITY METHODS =====

    // Utility method để check network connectivity
    public static boolean isValidUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Method để test connection
    public static void testConnection(OnResponseListener listener) {
        makeGetRequest(BASE_URL + "/activities", new OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                listener.onSuccess("Connection successful to " + BASE_URL);
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError("Connection failed: " + errorMessage);
            }
        });
    }

    // Test connection to sections endpoint
    public static void testSectionConnection(OnResponseListener listener) {
        makeGetRequest(BASE_URL + "/sections", new OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                listener.onSuccess("Section endpoint connection successful");
            }

            @Override
            public void onError(String errorMessage) {
                listener.onError("Section endpoint connection failed: " + errorMessage);
            }
        });
    }
}