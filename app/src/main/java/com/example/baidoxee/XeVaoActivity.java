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

public class XeVaoActivity extends AppCompatActivity {

    private static final String TAG = "XeVaoActivity";

    private RecyclerView recyclerView;
    private List<xevao> carList;
    private List<xevao> originalCarList;
    private XevaoAdapter adapter;
    private final String URL = "https://baidoxe.onrender.com/api/parking_logs";
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
        setContentView(R.layout.xevao);

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

        carList = new ArrayList<>();
        originalCarList = new ArrayList<>();
        adapter = new XevaoAdapter(carList);
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
                        fetchParkedCars(); // Sau khi cache xong thì mới lấy parking logs
                    } else {
                        Log.e(TAG, "Vehicle response is null");
                        fetchParkedCars(); // Vẫn tiếp tục lấy logs dù không cache được
                    }
                },
                error -> {
                    Log.e(TAG, "Error fetching vehicles", error);
                    showError("Lỗi tải thông tin xe: " + getErrorMessage(error));
                    fetchParkedCars(); // Vẫn tiếp tục lấy logs dù có lỗi
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

            logCacheStatus();

        } catch (Exception e) {
            Log.e(TAG, "Error caching vehicle info", e);
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

    private void fetchParkedCars() {
        if (this.isDestroyed() || this.isFinishing()) {
            Log.w(TAG, "Activity is destroyed/finishing, skipping fetchParkedCars");
            return;
        }

        logCacheStatus();

        showLoading("Đang tải danh sách xe đang đỗ...");
        Log.d(TAG, "Starting to fetch parked cars from: " + URL);

        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                URL,
                null,
                response -> {
                    hideLoading();
                    Log.d(TAG, "Parking logs response received, length: " + (response != null ? response.length() : "null"));
                    if (response != null) {
                        parseParkedCars(response);
                    } else {
                        Log.e(TAG, "Response is null");
                        showError("Dữ liệu trả về trống");
                    }
                },
                error -> {
                    hideLoading();
                    Log.e(TAG, "Error fetching parking logs", error);
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

    private void parseParkedCars(JSONArray response) {
        try {
            if (carList == null) {
                carList = new ArrayList<>();
            }
            if (originalCarList == null) {
                originalCarList = new ArrayList<>();
            }

            carList.clear();
            originalCarList.clear();

            Log.d(TAG, "Response length: " + response.length());

            int parkedVehiclesCount = 0;

            for (int i = 0; i < response.length(); i++) {
                JSONObject obj = response.getJSONObject(i);

                if (obj == null) {
                    Log.w(TAG, "Null object at index " + i);
                    continue;
                }

                Log.d(TAG, "Processing item " + i + ": " + obj.toString());

                // Chỉ xử lý những log KHÔNG có timeOut (xe đang đỗ)
                String rawTimeOut = extractTimeFromDate(obj, "timeOut");
                if (isValidTimeString(rawTimeOut)) {
                    Log.d(TAG, "Skipping item " + i + " - has timeOut (xe đã ra)");
                    continue; // Bỏ qua những xe đã ra
                }

                // Extract vehicle license plate
                String vehiclePlate = extractVehiclePlate(obj);

                // Extract time data
                String rawTimeIn = extractTimeFromDate(obj, "timeIn");

                Log.d(TAG, "Item " + i + " - Vehicle: " + vehiclePlate);
                Log.d(TAG, "Raw timeIn: " + rawTimeIn);

                // Chuyển đổi thời gian theo timezone Việt Nam
                String timeIn = formatToVietnamTime(rawTimeIn);

                Log.d(TAG, "Formatted timeIn: " + timeIn);

                // Extract image URL if available
                String imageUrl = extractImageUrl(obj);

                xevao car = new xevao(vehiclePlate, timeIn, imageUrl);
                carList.add(car);
                originalCarList.add(car);
                parkedVehiclesCount++;
            }

            if (adapter != null) {
                adapter.notifyDataSetChanged();
                String successMessage = "Tải thành công " + parkedVehiclesCount + " xe đang đỗ";
                showSuccess(successMessage);
                Log.i(TAG, successMessage);

                if (tvTotalCars != null) {
                    tvTotalCars.setText("Tổng số xe đang đỗ: " + parkedVehiclesCount);
                }
            } else {
                Log.e(TAG, "Adapter is null");
            }

        } catch (Exception e) {
            String errorMsg = "Lỗi xử lý dữ liệu: " + e.getMessage();
            showError(errorMsg);
            Log.e("ParseParkedCars", errorMsg, e);
        }
    }

    /**
     * Extract vehicle license plate from the response (same as XeraActivity)
     */
    private String extractVehiclePlate(JSONObject obj) {
        try {
            // Case 1: Vehicle object với thông tin đầy đủ từ populate
            if (obj.has("vehicle")) {
                Object vehicleObj = obj.get("vehicle");

                if (vehicleObj instanceof JSONObject) {
                    JSONObject vehicleRef = (JSONObject) vehicleObj;

                    // Sub-case 1a: Có plateNumber trực tiếp (từ populate)
                    if (vehicleRef.has("plateNumber")) {
                        String plateNumber = vehicleRef.getString("plateNumber");
                        if (!plateNumber.isEmpty() && !plateNumber.equals("null")) {
                            Log.d(TAG, "Found plateNumber from populated data: " + plateNumber);
                            return plateNumber;
                        }
                    }

                    // Sub-case 1b: Có licensePlate field
                    if (vehicleRef.has("licensePlate")) {
                        String licensePlate = vehicleRef.getString("licensePlate");
                        if (!licensePlate.isEmpty() && !licensePlate.equals("null")) {
                            Log.d(TAG, "Found licensePlate from populated data: " + licensePlate);
                            return licensePlate;
                        }
                    }

                    // Sub-case 1c: Vehicle reference với $id hoặc $oid (cần tra cache)
                    String vehicleId = null;

                    if (vehicleRef.has("$id")) {
                        Object idObj = vehicleRef.get("$id");
                        if (idObj instanceof JSONObject) {
                            vehicleId = extractObjectId((JSONObject) idObj);
                        } else if (idObj instanceof String) {
                            vehicleId = (String) idObj;
                        }
                    } else if (vehicleRef.has("_id")) {
                        Object idObj = vehicleRef.get("_id");
                        if (idObj instanceof JSONObject) {
                            vehicleId = extractObjectId((JSONObject) idObj);
                        } else if (idObj instanceof String) {
                            vehicleId = (String) idObj;
                        }
                    } else if (vehicleRef.has("$oid")) {
                        vehicleId = vehicleRef.getString("$oid");
                    }

                    // Tra cache nếu có vehicle ID
                    if (vehicleId != null) {
                        if (vehicleCache.containsKey(vehicleId)) {
                            String cachedPlate = vehicleCache.get(vehicleId);
                            Log.d(TAG, "Found plate from cache: " + cachedPlate + " for ID: " + vehicleId);
                            return cachedPlate;
                        } else {
                            Log.w(TAG, "Vehicle ID not found in cache: " + vehicleId);
                            Log.d(TAG, "Available cache keys: " + vehicleCache.keySet().toString());
                            return "ID: " + vehicleId.substring(0, Math.min(8, vehicleId.length())) + "...";
                        }
                    }
                }
                // Case 2: Vehicle là string trực tiếp
                else if (vehicleObj instanceof String) {
                    String vehicleStr = (String) vehicleObj;
                    if (!vehicleStr.isEmpty() && !vehicleStr.equals("null")) {
                        Log.d(TAG, "Vehicle as string: " + vehicleStr);
                        return vehicleStr;
                    }
                }
            }

            // Case 3: Các field trực tiếp trên object chính
            if (obj.has("plateNumber")) {
                String plateNumber = obj.getString("plateNumber");
                if (!plateNumber.isEmpty() && !plateNumber.equals("null")) {
                    Log.d(TAG, "Found plateNumber on main object: " + plateNumber);
                    return plateNumber;
                }
            }

            if (obj.has("licensePlate")) {
                String licensePlate = obj.getString("licensePlate");
                if (!licensePlate.isEmpty() && !licensePlate.equals("null")) {
                    Log.d(TAG, "Found licensePlate on main object: " + licensePlate);
                    return licensePlate;
                }
            }

            // Case 4: Không tìm thấy thông tin nào
            Log.w(TAG, "No vehicle plate information found in object: " + obj.toString());
            return "Không rõ biển số";

        } catch (Exception e) {
            Log.e(TAG, "Error extracting vehicle plate from: " + obj.toString(), e);
            return "Lỗi biển số";
        }
    }

    /**
     * Extract image URL from parking log object
     */
    private String extractImageUrl(JSONObject obj) {
        try {
            // Tìm các field có thể chứa URL ảnh
            String[] imageFields = {"imageUrl", "image", "photo", "entryImage", "plateImage"};

            for (String field : imageFields) {
                if (obj.has(field)) {
                    String imageUrl = obj.getString(field);
                    if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.equals("null")) {
                        return imageUrl;
                    }
                }
            }

            return null; // Không có ảnh

        } catch (Exception e) {
            Log.e(TAG, "Error extracting image URL", e);
            return null;
        }
    }

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
            bottomNavigationView.setSelectedItemId(R.id.nav_parking);

            bottomNavigationView.setOnItemSelectedListener(new BottomNavigationView.OnItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    int id = item.getItemId();

                    if (id == R.id.nav_home) {
                        Intent intent = new Intent(XeVaoActivity.this, TrangChuActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                        return true;

                    } else if (id == R.id.nav_parking) {
                        refreshData();
                        Toast.makeText(XeVaoActivity.this, "Đang cập nhật danh sách xe đang đỗ...", Toast.LENGTH_SHORT).show();
                        return true;

                    } else if (id == R.id.nav_exit) {
                        Intent intent = new Intent(XeVaoActivity.this, XeraActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                        return true;

                    } else if (id == R.id.nav_payment) {
                        Intent intent = new Intent(XeVaoActivity.this, ThanhToanActivity.class);
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
                    filterCars(s.toString().trim());
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

    private void filterCars(String searchText) {
        if (adapter == null || originalCarList == null) {
            Log.w(TAG, "Cannot filter - adapter or originalCarList is null");
            return;
        }

        List<xevao> filteredList = new ArrayList<>();

        if (searchText.isEmpty()) {
            filteredList.addAll(originalCarList);
            Log.d(TAG, "Filter cleared, showing all " + filteredList.size() + " cars");
        } else {
            for (xevao car : originalCarList) {
                if (car != null && car.getVehicle() != null &&
                        car.getVehicle().toLowerCase().contains(searchText.toLowerCase())) {
                    filteredList.add(car);
                }
            }
            Log.d(TAG, "Filtered by '" + searchText + "', found " + filteredList.size() + " matching cars");
        }

        carList.clear();
        carList.addAll(filteredList);
        adapter.notifyDataSetChanged();

        if (tvTotalCars != null) {
            tvTotalCars.setText("Tổng số xe đang đỗ: " + filteredList.size());
        }
    }

    public void refreshData() {
        Log.d(TAG, "Refreshing data...");
        fetchVehicles(); // Refresh cả thông tin xe và parking logs
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "XeVaoActivity resumed - refreshing data");
        fetchVehicles(); // Refresh khi activity được resume
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "XeVaoActivity paused");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "XeVaoActivity stopped");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "XeVaoActivity destroying - cleaning up resources");

        // Cancel all network requests
        if (requestQueue != null) {
            requestQueue.cancelAll(this);
            Log.d(TAG, "Cancelled all network requests");
        }

        // Clear data structures
        if (carList != null) {
            carList.clear();
            carList = null;
        }
        if (originalCarList != null) {
            originalCarList.clear();
            originalCarList = null;
        }
        if (vehicleCache != null) {
            vehicleCache.clear();
            vehicleCache = null;
        }

        // Clear adapter reference
        adapter = null;

        Log.d(TAG, "XeVaoActivity destroyed - resources cleaned up");
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
        Log.d(TAG, "CarList size: " + (carList != null ? carList.size() : "null"));
        Log.d(TAG, "OriginalCarList size: " + (originalCarList != null ? originalCarList.size() : "null"));
        Log.d(TAG, "Adapter: " + (adapter != null ? "exists" : "null"));
        Log.d(TAG, "RecyclerView: " + (recyclerView != null ? "exists" : "null"));
        Log.d(TAG, "RequestQueue: " + (requestQueue != null ? "exists" : "null"));
        Log.d(TAG, "========================");
    }
}