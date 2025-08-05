package com.example.baidoxee;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import android.content.Intent;
import android.view.MenuItem;

public class XeraActivity extends AppCompatActivity {

    private static final String TAG = "XeraActivity";

    private RecyclerView recyclerView;
    private List<xera> logList;
    private List<xera> originalLogList;
    private XeraAdapter adapter;
    private final String URL = "https://baidoxe.onrender.com/api/transactions";
    private final String VEHICLES_URL = "https://baidoxe.onrender.com/api/vehicles";
    private RequestQueue requestQueue;
    private BottomNavigationView bottomNavigationView;
    private EditText etSearchPlate;
    private TextView tvTotalCars;

    // Cache để lưu thông tin xe theo ID
    private Map<String, String> vehicleCache = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.xera);

        initializeViews();
        initializeNetwork();
        setupBottomNavigation();
        setupSearch();
        fetchVehicles();
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recyclerView);
        bottomNavigationView = findViewById(R.id.bottomNavigation);
        etSearchPlate = findViewById(R.id.etSearchPlate);
        tvTotalCars = findViewById(R.id.tvTotalCars);

        if (recyclerView == null) {
            Log.e(TAG, "RecyclerView not found in layout");
            Toast.makeText(this, "Lỗi: Không tìm thấy RecyclerView", Toast.LENGTH_SHORT).show();
            return;
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        logList = new ArrayList<>();
        originalLogList = new ArrayList<>();
        adapter = new XeraAdapter(logList);
        recyclerView.setAdapter(adapter);

        Log.d(TAG, "Views initialized successfully");
    }

    private void initializeNetwork() {
        requestQueue = Volley.newRequestQueue(this);
        Log.d(TAG, "Network initialized");
    }

    /**
     * Lấy thông tin tất cả xe để cache biển số theo ID
     */
    private void fetchVehicles() {
        if (this.isDestroyed() || this.isFinishing()) {
            Log.w(TAG, "Activity is destroyed/finishing, skipping fetchVehicles");
            return;
        }

        showLoading("Đang tải thông tin xe...");
        Log.d(TAG, "Starting to fetch vehicles from: " + VEHICLES_URL);

        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                VEHICLES_URL,
                null,
                response -> {
                    Log.d(TAG, "Vehicles response received, length: " + (response != null ? response.length() : "null"));
                    if (response != null) {
                        cacheVehicleInfo(response);
                        fetchLogs(); // Sau khi cache xong thì mới lấy parking logs
                    } else {
                        Log.e(TAG, "Vehicle response is null");
                        fetchLogs(); // Vẫn tiếp tục lấy logs dù không cache được
                    }
                },
                error -> {
                    Log.e(TAG, "Error fetching vehicles", error);
                    showError("Lỗi tải thông tin xe: " + getErrorMessage(error));
                    fetchLogs(); // Vẫn tiếp tục lấy logs dù có lỗi
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Accept", "application/json");
                headers.put("User-Agent", "Android-App");
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                15000, // 15 seconds timeout
                2,     // 2 retries
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }

    /**
     * Cache thông tin xe theo ID để tra cứu nhanh
     * Improved version with better error handling and logging
     */
    private void cacheVehicleInfo(JSONArray vehiclesResponse) {
        try {
            vehicleCache.clear();
            int successCount = 0;
            int totalCount = vehiclesResponse.length();

            Log.d(TAG, "Starting to cache " + totalCount + " vehicles");

            for (int i = 0; i < totalCount; i++) {
                try {
                    JSONObject vehicle = vehiclesResponse.getJSONObject(i);

                    // Extract vehicle ID
                    String vehicleId = null;
                    if (vehicle.has("_id")) {
                        Object idObj = vehicle.get("_id");
                        if (idObj instanceof JSONObject) {
                            vehicleId = extractObjectId((JSONObject) idObj);
                        } else if (idObj instanceof String) {
                            vehicleId = (String) idObj;
                        }
                    }

                    // Extract plate number
                    String plateNumber = null;
                    if (vehicle.has("plateNumber")) {
                        plateNumber = vehicle.getString("plateNumber");
                    } else if (vehicle.has("licensePlate")) {
                        plateNumber = vehicle.getString("licensePlate");
                    }

                    // Cache if both ID and plate number are available
                    if (vehicleId != null && plateNumber != null &&
                            !plateNumber.isEmpty() && !plateNumber.equals("null")) {

                        vehicleCache.put(vehicleId, plateNumber);
                        successCount++;

                        Log.d("VehicleCache", String.format("Cached [%d/%d]: ID=%s, Plate=%s",
                                i + 1, totalCount, vehicleId, plateNumber));
                    } else {
                        Log.w("VehicleCache", String.format("Skipped [%d/%d]: ID=%s, Plate=%s",
                                i + 1, totalCount, vehicleId, plateNumber));
                    }

                } catch (Exception e) {
                    Log.e("VehicleCache", "Error processing vehicle at index " + i, e);
                }
            }

            Log.i(TAG, String.format("Vehicle caching completed: %d/%d successful",
                    successCount, totalCount));

            // Log cache content for debugging
            logCacheStatus();

        } catch (Exception e) {
            Log.e(TAG, "Error caching vehicle info", e);
            // Don't clear cache on error, keep what we have
        }
    }

    /**
     * Log cache status for debugging
     */
    private void logCacheStatus() {
        Log.i(TAG, "=== CACHE STATUS ===");
        Log.i(TAG, "Cache size: " + vehicleCache.size());

        if (vehicleCache.isEmpty()) {
            Log.w(TAG, "Cache is empty! This might cause 'Không rõ biển số' issues");
        } else {
            Log.i(TAG, "Cache contains " + vehicleCache.size() + " vehicle entries");

            // Show some examples
            int count = 0;
            for (Map.Entry<String, String> entry : vehicleCache.entrySet()) {
                Log.d(TAG, "  Cache[" + count + "]: " + entry.getKey() + " -> " + entry.getValue());
                if (++count >= 3) break; // Show first 3 entries
            }
        }
        Log.i(TAG, "==================");
    }

    private void fetchLogs() {
        if (this.isDestroyed() || this.isFinishing()) {
            Log.w(TAG, "Activity is destroyed/finishing, skipping fetchLogs");
            return;
        }

        // Log cache status before fetching logs
        logCacheStatus();

        showLoading("Đang tải dữ liệu xe đã thanh toán...");
        Log.d(TAG, "Starting to fetch paid transactions from: " + URL);

        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                URL,
                null,
                response -> {
                    hideLoading();
                    Log.d(TAG, "Paid transactions response received, length: " + (response != null ? response.length() : "null"));
                    if (response != null) {
                        parseTransactions(response);
                    } else {
                        Log.e(TAG, "Response is null");
                        showError("Dữ liệu trả về trống");
                    }
                },
                error -> {
                    hideLoading();
                    Log.e(TAG, "Error fetching paid transactions", error);
                    handleVolleyError(error);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Accept", "application/json");
                headers.put("User-Agent", "Android-App");
                return headers;
            }

            @Override
            protected VolleyError parseNetworkError(VolleyError volleyError) {
                if (volleyError.networkResponse != null && volleyError.networkResponse.data != null) {
                    try {
                        String errorString = new String(volleyError.networkResponse.data, HttpHeaderParser.parseCharset(volleyError.networkResponse.headers));
                        Log.e("VolleyError", "Error response: " + errorString);
                    } catch (UnsupportedEncodingException e) {
                        Log.e("VolleyError", "Error parsing error response", e);
                    }
                }
                return super.parseNetworkError(volleyError);
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                15000,
                2,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }

    /**
     * Parse transactions thay vì parking logs
     */
    private void parseTransactions(JSONArray response) {
        try {
            if (logList == null) {
                logList = new ArrayList<>();
            }
            if (originalLogList == null) {
                originalLogList = new ArrayList<>();
            }

            logList.clear();
            originalLogList.clear();

            Log.d(TAG, "Response length: " + response.length());
            Log.d(TAG, "Raw response preview: " + response.toString().substring(0, Math.min(500, response.toString().length())) + "...");

            int paidTransactionsCount = 0;

            for (int i = 0; i < response.length(); i++) {
                JSONObject obj = response.getJSONObject(i);

                if (obj == null) {
                    Log.w(TAG, "Null object at index " + i);
                    continue;
                }

                Log.d(TAG, "Processing transaction " + i + ": " + obj.toString());

                // Kiểm tra status = PAID
                String status = obj.optString("status", "");
                if (!"PAID".equals(status)) {
                    Log.d(TAG, "Skipping transaction " + i + " - status is not PAID: " + status);
                    continue;
                }

                // Extract vehicle license plate
                String vehiclePlate = extractVehiclePlateFromTransactionObject(obj);

                // Extract thời gian từ transaction fields
                String rawTimeIn = extractTimeFromDate(obj, "timeIn");
                String rawTimeOut = extractTimeFromDate(obj, "timeOut");

                Log.d(TAG, "Transaction " + i + " - Vehicle: " + vehiclePlate);
                Log.d(TAG, "Raw timeIn: " + rawTimeIn);
                Log.d(TAG, "Raw timeOut: " + rawTimeOut);

                // Chuyển đổi thời gian theo timezone Việt Nam
                String timeIn = formatToVietnamTime(rawTimeIn);
                String timeOut = formatToVietnamTime(rawTimeOut);

                Log.d(TAG, "Formatted timeIn: " + timeIn);
                Log.d(TAG, "Formatted timeOut: " + timeOut);

                xera log = new xera(vehiclePlate, timeIn, timeOut);
                logList.add(log);
                originalLogList.add(log);
                paidTransactionsCount++;
            }

            if (adapter != null) {
                adapter.notifyDataSetChanged();
                String successMessage = "Tải thành công " + paidTransactionsCount + " giao dịch đã thanh toán";
                showSuccess(successMessage);
                Log.i(TAG, successMessage);

                if (tvTotalCars != null) {
                    tvTotalCars.setText("Tổng số giao dịch đã thanh toán: " + paidTransactionsCount);
                }
            } else {
                Log.e(TAG, "Adapter is null");
            }

        } catch (Exception e) {
            String errorMsg = "Lỗi xử lý dữ liệu transactions: " + e.getMessage();
            showError(errorMsg);
            Log.e("ParseTransactions", errorMsg, e);
        }
    }

    /**
     * Extract vehicle plate từ transaction object - phiên bản sửa lỗi
     */
    private String extractVehiclePlateFromTransactionObject(JSONObject obj) {
        try {
            // Method 1: Transaction object có thể có trực tiếp bienSoXe field
            if (obj.has("bienSoXe")) {
                String bienSoXe = obj.getString("bienSoXe");
                if (!bienSoXe.isEmpty() && !bienSoXe.equals("null") && !bienSoXe.equals("N/A")) {
                    Log.d(TAG, "Found bienSoXe from transaction: " + bienSoXe);
                    return bienSoXe;
                }
            }

            // Method 2: Kiểm tra vehicle object trong transaction
            if (obj.has("vehicle")) {
                Object vehicleObj = obj.get("vehicle");

                if (vehicleObj instanceof String) {
                    // vehicle là string ID, tra cứu từ cache
                    String vehicleId = (String) vehicleObj;
                    if (vehicleCache.containsKey(vehicleId)) {
                        String cachedPlate = vehicleCache.get(vehicleId);
                        Log.d(TAG, "Found vehicle from cache: " + vehicleId + " -> " + cachedPlate);
                        return cachedPlate;
                    } else {
                        Log.w(TAG, "Vehicle ID not found in cache: " + vehicleId);
                    }
                } else if (vehicleObj instanceof JSONObject) {
                    // vehicle là object, extract trực tiếp
                    JSONObject vehicle = (JSONObject) vehicleObj;
                    if (vehicle.has("plateNumber")) {
                        String plateNumber = vehicle.getString("plateNumber");
                        if (!plateNumber.isEmpty() && !plateNumber.equals("null")) {
                            Log.d(TAG, "Found plateNumber from vehicle object: " + plateNumber);
                            return plateNumber;
                        }
                    }
                    if (vehicle.has("licensePlate")) {
                        String licensePlate = vehicle.getString("licensePlate");
                        if (!licensePlate.isEmpty() && !licensePlate.equals("null")) {
                            Log.d(TAG, "Found licensePlate from vehicle object: " + licensePlate);
                            return licensePlate;
                        }
                    }
                }
            }

            // Method 3: Kiểm tra vehicleId field
            if (obj.has("vehicleId")) {
                String vehicleId = obj.getString("vehicleId");
                if (vehicleCache.containsKey(vehicleId)) {
                    String cachedPlate = vehicleCache.get(vehicleId);
                    Log.d(TAG, "Found vehicle from cache via vehicleId: " + vehicleId + " -> " + cachedPlate);
                    return cachedPlate;
                }
            }

            Log.w(TAG, "Could not extract vehicle plate from transaction: " + obj.toString());
            return "Không rõ biển số";

        } catch (Exception e) {
            Log.e(TAG, "Error extracting vehicle plate from transaction: " + obj.toString(), e);
            return "Lỗi biển số";
        }
    }

    /**
     * Improved ObjectId extraction with better error handling
     */
    private String extractObjectId(JSONObject idObj) {
        if (idObj == null) {
            Log.w(TAG, "ID object is null");
            return null;
        }

        try {
            // MongoDB ObjectId format: {"$oid": "string"}
            if (idObj.has("$oid")) {
                String oid = idObj.getString("$oid");
                if (oid != null && !oid.isEmpty() && !oid.equals("null")) {
                    return oid;
                }
            }

            // Alternative format: {"_id": "string"}
            if (idObj.has("_id")) {
                String id = idObj.getString("_id");
                if (id != null && !id.isEmpty() && !id.equals("null")) {
                    return id;
                }
            }

            // Log the actual structure for debugging
            Log.w(TAG, "Could not extract ObjectId from: " + idObj.toString());
            return null;

        } catch (Exception e) {
            Log.e(TAG, "Error extracting ObjectId from: " + idObj.toString(), e);
            return null;
        }
    }

    /**
     * Extract time string from MongoDB date object
     */
    private String extractTimeFromDate(JSONObject obj, String timeField) {
        try {
            if (obj.has(timeField)) {
                Object timeObj = obj.get(timeField);

                if (timeObj instanceof String) {
                    return (String) timeObj;
                } else if (timeObj instanceof JSONObject) {
                    JSONObject timeJson = (JSONObject) timeObj;
                    if (timeJson.has("$date")) {
                        return timeJson.getString("$date");
                    }
                }
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting time from date", e);
            return null;
        }
    }

    private boolean isValidTimeString(String timeString) {
        return timeString != null &&
                !timeString.isEmpty() &&
                !timeString.equals("null") &&
                !timeString.trim().isEmpty();
    }

    /**
     * Format thời gian theo timezone Việt Nam (UTC+7)
     * Từ dữ liệu MongoDB: "2025-07-30T06:32:21.887Z" -> "13:32" (VN time)
     */
    private String formatToVietnamTime(String isoTime) {
        if (!isValidTimeString(isoTime)) {
            return "--:--";
        }

        try {
            String cleaned = isoTime.trim();
            Log.d("TimeFormat", "Processing time: " + cleaned);

            // Loại bỏ timezone indicator nếu có
            if (cleaned.endsWith("Z")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            } else if (cleaned.contains("+") || cleaned.lastIndexOf("-") > 10) {
                int tzIndex = Math.max(cleaned.lastIndexOf("+"), cleaned.lastIndexOf("-"));
                if (tzIndex > 10) {
                    cleaned = cleaned.substring(0, tzIndex);
                }
            }

            SimpleDateFormat inputFormat;

            // Xử lý milliseconds
            if (cleaned.contains(".")) {
                String[] parts = cleaned.split("\\.");
                if (parts.length == 2) {
                    String millisPart = parts[1];
                    // Chuẩn hóa milliseconds về 3 chữ số
                    if (millisPart.length() > 3) {
                        millisPart = millisPart.substring(0, 3);
                    } else {
                        while (millisPart.length() < 3) {
                            millisPart += "0";
                        }
                    }
                    cleaned = parts[0] + "." + millisPart;
                }
                inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());
            } else {
                inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            }

            // Parse như UTC time
            inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date utcDate = inputFormat.parse(cleaned);

            if (utcDate == null) {
                Log.e("TimeFormat", "Parsed date is null for: " + isoTime);
                return "--:--";
            }

            // Format thành giờ Việt Nam (UTC+7)
            SimpleDateFormat vietnamTimeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            vietnamTimeFormat.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh")); // UTC+7

            String vietnamTime = vietnamTimeFormat.format(utcDate);
            Log.d("TimeFormat", "Converted " + isoTime + " -> " + vietnamTime);

            return vietnamTime;

        } catch (Exception e) {
            Log.e("TimeFormat", "Lỗi định dạng thời gian: " + isoTime, e);
            return "--:--";
        }
    }

    private String getErrorMessage(VolleyError error) {
        if (error instanceof TimeoutError) {
            return "Kết nối quá chậm";
        } else if (error instanceof NoConnectionError) {
            return "Không có kết nối internet";
        } else if (error instanceof NetworkError) {
            return "Mạng không ổn định";
        } else if (error instanceof ServerError) {
            if (error.networkResponse != null) {
                int statusCode = error.networkResponse.statusCode;
                switch (statusCode) {
                    case 404: return "API không tìm thấy (404)";
                    case 500: return "Server nội bộ (500)";
                    case 503: return "Server đang bảo trì (503)";
                    default: return "Lỗi server (" + statusCode + ")";
                }
            }
            return "Server không phản hồi";
        }
        return "Lỗi không xác định";
    }

    private void handleVolleyError(VolleyError error) {
        String errorMessage = getErrorMessage(error);

        if (error.networkResponse != null) {
            int statusCode = error.networkResponse.statusCode;
            Log.e("NetworkResponse", "Status code: " + statusCode);

            try {
                String responseBody = new String(error.networkResponse.data, HttpHeaderParser.parseCharset(error.networkResponse.headers));
                Log.e("NetworkResponse", "Response body: " + responseBody);
            } catch (Exception e) {
                Log.e("NetworkResponse", "Error parsing response body", e);
            }
        }

        Log.e("VolleyError", "Error details: " + error.toString(), error);
        showError("Lỗi: " + errorMessage);

        if (error.getCause() != null) {
            Log.e("VolleyError", "Cause: " + error.getCause().getMessage());
        }
    }

    private void showLoading(String message) {
        if (!this.isDestroyed() && !this.isFinishing()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Loading: " + message);
        }
    }

    private void hideLoading() {
        // Có thể implement ProgressBar ở đây nếu cần
        Log.d(TAG, "Loading hidden");
    }

    private void showError(String message) {
        if (!this.isDestroyed() && !this.isFinishing()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error: " + message);
        }
    }

    private void showSuccess(String message) {
        if (!this.isDestroyed() && !this.isFinishing()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Success: " + message);
        }
    }

    private void setupBottomNavigation() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_exit);

            bottomNavigationView.setOnItemSelectedListener(new BottomNavigationView.OnItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    int id = item.getItemId();

                    if (id == R.id.nav_home) {
                        Intent intent = new Intent(XeraActivity.this, TrangChuActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                        return true;

                    } else if (id == R.id.nav_parking) {
                        Intent intent = new Intent(XeraActivity.this, XeVaoActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                        return true;

                    } else if (id == R.id.nav_exit) {
                        refreshData();
                        Toast.makeText(XeraActivity.this, "Đang cập nhật dữ liệu xe ra...", Toast.LENGTH_SHORT).show();
                        return true;

                    } else if (id == R.id.nav_payment) {
                        Intent intent = new Intent(XeraActivity.this, ThanhToanActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                        return true;
                    }

                    return false;
                }
            });

            Log.d(TAG, "Bottom navigation setup completed");
        } else {
            Log.e(TAG, "BottomNavigationView not found in layout");
        }
    }

    private void setupSearch() {
        if (etSearchPlate != null) {
            etSearchPlate.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    // Not needed
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterLogs(s.toString().trim());
                }

                @Override
                public void afterTextChanged(Editable s) {
                    // Not needed
                }
            });

            Log.d(TAG, "Search setup completed");
        } else {
            Log.e(TAG, "Search EditText not found in layout");
        }
    }

    private void filterLogs(String searchText) {
        if (adapter == null || originalLogList == null) {
            Log.w(TAG, "Cannot filter - adapter or originalLogList is null");
            return;
        }

        List<xera> filteredList = new ArrayList<>();

        if (searchText.isEmpty()) {
            filteredList.addAll(originalLogList);
            Log.d(TAG, "Filter cleared, showing all " + filteredList.size() + " logs");
        } else {
            for (xera log : originalLogList) {
                if (log != null && log.getVehicle() != null &&
                        log.getVehicle().toLowerCase().contains(searchText.toLowerCase())) {
                    filteredList.add(log);
                }
            }
            Log.d(TAG, "Filtered by '" + searchText + "', found " + filteredList.size() + " matching logs");
        }

        logList.clear();
        logList.addAll(filteredList);
        adapter.notifyDataSetChanged();

        if (tvTotalCars != null) {
            tvTotalCars.setText("Tổng số giao dịch đã thanh toán: " + filteredList.size());
        }
    }

    public void refreshData() {
        Log.d(TAG, "Refreshing paid transactions data...");
        fetchVehicles(); // Refresh cả thông tin xe và transactions
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "XeraActivity resumed - refreshing paid transactions data");
        fetchVehicles(); // Refresh khi activity được resume
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "XeraActivity paused");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "XeraActivity stopped");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "XeraActivity destroying - cleaning up resources");

        // Cancel all network requests
        if (requestQueue != null) {
            requestQueue.cancelAll(this);
            Log.d(TAG, "Cancelled all network requests");
        }

        // Clear data structures
        if (logList != null) {
            logList.clear();
            logList = null;
        }
        if (originalLogList != null) {
            originalLogList.clear();
            originalLogList = null;
        }
        if (vehicleCache != null) {
            vehicleCache.clear();
            vehicleCache = null;
        }

        // Clear adapter reference
        adapter = null;

        Log.d(TAG, "XeraActivity destroyed - resources cleaned up");
    }

    /**
     * Helper method to validate activity state before operations
     */
    private boolean isActivityValid() {
        return !this.isDestroyed() && !this.isFinishing();
    }

    /**
     * Debug method to print current state
     */
    private void debugCurrentState() {
        Log.d(TAG, "=== CURRENT STATE DEBUG ===");
        Log.d(TAG, "Activity valid: " + isActivityValid());
        Log.d(TAG, "Cache size: " + (vehicleCache != null ? vehicleCache.size() : "null"));
        Log.d(TAG, "LogList size: " + (logList != null ? logList.size() : "null"));
        Log.d(TAG, "OriginalLogList size: " + (originalLogList != null ? originalLogList.size() : "null"));
        Log.d(TAG, "Adapter: " + (adapter != null ? "exists" : "null"));
        Log.d(TAG, "RecyclerView: " + (recyclerView != null ? "exists" : "null"));
        Log.d(TAG, "RequestQueue: " + (requestQueue != null ? "exists" : "null"));
        Log.d(TAG, "========================");
    }
}