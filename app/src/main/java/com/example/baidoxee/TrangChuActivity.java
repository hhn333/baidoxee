package com.example.baidoxee;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public class TrangChuActivity extends AppCompatActivity {
    private static final String TAG = "TrangChuActivity";
    private static final int TOTAL_PARKING_SPOTS = 13;
    private static final long AUTO_REFRESH_INTERVAL = 30000;
    private static final long TIME_UPDATE_INTERVAL = 1000;
    private static final String EVENTS_URL = "https://baidoxe.onrender.com/api/events";

    // UI Components
    private TextView tvParkingSpots, tvParkedCars, tvTodayCars, tvCurrentTime, tvCurrentDate;
    private TextView tvTotalSpots, tvEmptyPercentage, tvOccupiedPercentage, tvLastUpdate;
    private PieChart pieChart;
    private BottomNavigationView bottomNavigationView;

    // Data
    private List<ParkedCar> currentParkedCars;
    private int totalTodayCars = 0, totalTodayRevenue = 0, currentAvailableSpots = 0, currentParkedCount = 0;
    private Handler uiUpdateHandler, autoRefreshHandler;
    private Runnable timeUpdateRunnable, autoRefreshRunnable;
    private boolean isLoading = false;
    private RequestQueue requestQueue;

    // Cache tĩnh để tái sử dụng (giống XeVaoActivity)
    private static final SimpleDateFormat UTC_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat UTC_FORMAT_MILLIS = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());
    private static final SimpleDateFormat VN_TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.getDefault());

    static {
        UTC_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        UTC_FORMAT_MILLIS.setTimeZone(TimeZone.getTimeZone("UTC"));
        VN_TIME_FORMAT.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    // Class để lưu thông tin event (giống XeVaoActivity)
    private static class VehicleEvent {
        String plateText;
        String eventType;
        Date timestamp;
        String plateImage;
        JSONObject originalObject;

        VehicleEvent(String plateText, String eventType, Date timestamp, String plateImage, JSONObject originalObject) {
            this.plateText = plateText;
            this.eventType = eventType;
            this.timestamp = timestamp;
            this.plateImage = plateImage;
            this.originalObject = originalObject;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.trangchu);

        initViews();
        initData();
        initializeNetwork();
        setupPieChart();
        setupBottomNavigation();
        setupRealTimeUpdates();
        setupAutoRefresh();
        loadDashboardData();
    }

    private void initViews() {
        try {
            // Dashboard stats
            tvParkingSpots = findViewById(R.id.tvParkingSpots);
            tvParkedCars = findViewById(R.id.tvParkedCars);
            tvTodayCars = findViewById(R.id.tvTodayCars);

            // Time display
            tvCurrentTime = findViewById(R.id.tvCurrentTime);
            tvCurrentDate = findViewById(R.id.tvCurrentDate);

            // Pie Chart components
            pieChart = findViewById(R.id.pieChart);
            tvTotalSpots = findViewById(R.id.tvTotalSpots);
            tvEmptyPercentage = findViewById(R.id.tvEmptyPercentage);
            tvOccupiedPercentage = findViewById(R.id.tvOccupiedPercentage);
            tvLastUpdate = findViewById(R.id.tvLastUpdate);

            // Navigation
            bottomNavigationView = findViewById(R.id.bottomNavigation);

            setInitialValues();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
            Toast.makeText(this, "Lỗi khởi tạo giao diện", Toast.LENGTH_SHORT).show();
        }
    }

    private void setInitialValues() {
        setTextSafe(tvParkingSpots, "--");
        setTextSafe(tvParkedCars, "--");
        setTextSafe(tvTodayCars, "--");
        setTextSafe(tvTotalSpots, String.valueOf(TOTAL_PARKING_SPOTS));
        setTextSafe(tvEmptyPercentage, "(--)");
        setTextSafe(tvOccupiedPercentage, "(--)");
    }

    private void setTextSafe(TextView textView, String text) {
        if (textView != null) textView.setText(text);
    }

    private void initData() {
        currentParkedCars = new ArrayList<>();
        uiUpdateHandler = new Handler(getMainLooper());
        autoRefreshHandler = new Handler(getMainLooper());
        currentAvailableSpots = TOTAL_PARKING_SPOTS;
    }

    private void initializeNetwork() {
        requestQueue = Volley.newRequestQueue(this);
    }

    private void setupPieChart() {
        if (pieChart == null) return;

        try {
            pieChart.setUsePercentValues(true);
            pieChart.getDescription().setEnabled(false);
            pieChart.setExtraOffsets(5, 10, 5, 5);
            pieChart.setDragDecelerationFrictionCoef(0.95f);
            pieChart.setRotationAngle(0);
            pieChart.setRotationEnabled(true);
            pieChart.setHighlightPerTapEnabled(true);
            pieChart.setDrawHoleEnabled(true);
            pieChart.setHoleColor(Color.TRANSPARENT);
            pieChart.setHoleRadius(55f);
            pieChart.setTransparentCircleRadius(55f);
            pieChart.setTransparentCircleColor(Color.WHITE);
            pieChart.setTransparentCircleAlpha(110);
            pieChart.setDrawCenterText(false);
            pieChart.getLegend().setEnabled(false);
            pieChart.setEntryLabelColor(Color.WHITE);
            pieChart.setEntryLabelTextSize(12f);
            pieChart.setDrawEntryLabels(false);

            setupEmptyPieChart();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up pie chart", e);
        }
    }

    private void setupEmptyPieChart() {
        if (pieChart == null) return;

        try {
            ArrayList<PieEntry> entries = new ArrayList<>();
            entries.add(new PieEntry(100f, "Không có dữ liệu"));

            PieDataSet dataSet = new PieDataSet(entries, "");
            dataSet.setSliceSpace(0f);
            dataSet.setSelectionShift(5f);
            dataSet.setColors(new int[]{Color.GRAY});
            dataSet.setValueTextSize(0f);

            PieData data = new PieData(dataSet);
            pieChart.setData(data);
            pieChart.invalidate();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up empty pie chart", e);
        }
    }

    private void setupBottomNavigation() {
        if (bottomNavigationView == null) return;

        bottomNavigationView.setSelectedItemId(R.id.nav_home);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_parking) {
                startActivity(new Intent(this, XeVaoActivity.class));
            } else if (id == R.id.nav_exit) {
                startActivity(new Intent(this, XeraActivity.class));
            } else if (id == R.id.nav_payment) {
                startActivity(new Intent(this, ThanhToanActivity.class));
            } else if (id == R.id.nav_home && !isLoading) {
                loadDashboardData();
            }
            return true;
        });
    }

    private void setupRealTimeUpdates() {
        timeUpdateRunnable = () -> {
            updateCurrentTime();
            updateLastUpdateTime();
            if (uiUpdateHandler != null) {
                uiUpdateHandler.postDelayed(timeUpdateRunnable, TIME_UPDATE_INTERVAL);
            }
        };
        uiUpdateHandler.post(timeUpdateRunnable);
    }

    private void setupAutoRefresh() {
        autoRefreshRunnable = () -> {
            if (!isLoading) {
                loadDashboardData();
            }
            if (autoRefreshHandler != null) {
                autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL);
            }
        };
        autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL);
    }

    private void loadDashboardData() {
        if (isLoading) return;

        isLoading = true;
        fetchEventsData();
    }

    // Sử dụng logic từ XeVaoActivity để lấy số xe đang đỗ
    private void fetchEventsData() {
        if (isFinishing()) return;

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, EVENTS_URL, null,
                response -> {
                    if (response != null) parseEventsData(response);
                },
                this::handleVolleyError
        );

        request.setRetryPolicy(new DefaultRetryPolicy(5000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(request);
    }

    // Logic parse đã được sửa để tính xe hôm nay theo yêu cầu mới
    private void parseEventsData(JSONArray response) {
        try {
            List<VehicleEvent> allEvents = new ArrayList<>();

            // Parse tất cả events
            for (int i = 0; i < response.length(); i++) {
                JSONObject obj = response.getJSONObject(i);
                if (obj == null) continue;

                String plateText = extractVehiclePlate(obj);
                String eventType = obj.optString("event_type", "");
                String rawTimestamp = extractTimeFromDate(obj, "timestamp");
                Date timestamp = parseTimeString(rawTimestamp);
                String plateImage = obj.optString("plate_image", "");

                // Chỉ xử lý events có thông tin đầy đủ
                if (!plateText.isEmpty() && !eventType.isEmpty() && timestamp != null) {
                    allEvents.add(new VehicleEvent(plateText, eventType, timestamp, plateImage, obj));
                }
            }

            // Sort events theo thời gian (cũ nhất trước)
            Collections.sort(allEvents, (a, b) -> a.timestamp.compareTo(b.timestamp));

            // Tìm xe đang đỗ (logic cũ giữ nguyên)
            Map<String, VehicleEvent> vehicleStatus = new HashMap<>();

            for (VehicleEvent event : allEvents) {
                String plateKey = event.plateText.toLowerCase().trim();

                if ("enter".equals(event.eventType)) {
                    // Xe vào - cập nhật status
                    vehicleStatus.put(plateKey, event);
                } else if ("exit".equals(event.eventType)) {
                    // Xe ra - xóa khỏi danh sách đang đỗ
                    vehicleStatus.remove(plateKey);
                }
            }

            // Tính toán số xe đang đỗ
            int currentParkedCars = vehicleStatus.size();

            // THAY ĐỔI LOGIC XE HÔM NAY: Chỉ đếm sự kiện "enter" trong ngày hôm nay
            int todayEnterEvents;
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            // Chỉ đếm sự kiện "enter"
            todayEnterEvents = (int) allEvents.stream().filter(event -> "enter".equals(event.eventType)).map(event -> extractDateFromTimestamp(event.timestamp)).filter(today::equals).count();

            // Update UI trên main thread
            runOnUiThread(() -> {
                updateDashboardUI(currentParkedCars, todayEnterEvents);
            });

            Log.d(TAG, "Processed " + allEvents.size() + " events, found " + currentParkedCars + " parked cars, " + todayEnterEvents + " enter events today");

        } catch (Exception e) {
            Log.e(TAG, "Error parsing events data", e);
            runOnUiThread(() -> {
                showStatsError("Lỗi xử lý dữ liệu");
            });
        }
    }

    private void updateDashboardUI(int parkedCars, int todayEnterEvents) {
        try {
            int availableSpots = Math.max(0, TOTAL_PARKING_SPOTS - parkedCars);

            this.currentAvailableSpots = availableSpots;
            this.currentParkedCount = parkedCars;
            this.totalTodayCars = todayEnterEvents; // Đây là số sự kiện "enter" hôm nay

            setTextSafe(tvParkingSpots, String.valueOf(availableSpots));
            setTextSafe(tvParkedCars, String.valueOf(parkedCars));
            setTextSafe(tvTodayCars, String.valueOf(todayEnterEvents));

            updatePieChart(availableSpots, parkedCars);

        } catch (Exception e) {
            Log.e(TAG, "Error updating dashboard UI", e);
            showStatsError("Lỗi cập nhật giao diện");
        } finally {
            isLoading = false;
        }
    }

    // Các method helper từ XeVaoActivity (giữ nguyên)
    private Date parseTimeString(String timeString) {
        if (!isValidTimeString(timeString)) return null;

        try {
            String cleaned = timeString.trim();

            // Loại bỏ timezone suffix nhanh
            if (cleaned.endsWith("Z")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            } else if (cleaned.contains("+") || cleaned.lastIndexOf("-") > 10) {
                int tzIndex = Math.max(cleaned.lastIndexOf("+"), cleaned.lastIndexOf("-"));
                if (tzIndex > 10) {
                    cleaned = cleaned.substring(0, tzIndex);
                }
            }

            // Parse với format phù hợp
            if (cleaned.contains(".")) {
                return UTC_FORMAT_MILLIS.parse(cleaned);
            } else {
                return UTC_FORMAT.parse(cleaned);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String extractVehiclePlate(JSONObject obj) {
        try {
            // Lấy từ field plate_text trước (trong collection events)
            String plateText = obj.optString("plate_text", "");
            if (!plateText.isEmpty() && !plateText.equals("null")) return plateText;

            // Kiểm tra trường đơn giản khác
            String plateNumber = obj.optString("plateNumber", "");
            if (!plateNumber.isEmpty() && !plateNumber.equals("null")) return plateNumber;

            String licensePlate = obj.optString("licensePlate", "");
            if (!licensePlate.isEmpty() && !licensePlate.equals("null")) return licensePlate;

            // Kiểm tra vehicle object
            if (obj.has("vehicle")) {
                Object vehicleObj = obj.get("vehicle");
                if (vehicleObj instanceof JSONObject) {
                    JSONObject vehicleRef = (JSONObject) vehicleObj;

                    plateNumber = vehicleRef.optString("plateNumber", "");
                    if (!plateNumber.isEmpty() && !plateNumber.equals("null")) return plateNumber;

                    licensePlate = vehicleRef.optString("licensePlate", "");
                    if (!licensePlate.isEmpty() && !licensePlate.equals("null")) return licensePlate;

                    // Nếu có ID thì hiển thị ID rút gọn
                    String vehicleId = extractObjectId(vehicleRef);
                    if (vehicleId != null) {
                        return "ID: " + vehicleId.substring(0, Math.min(8, vehicleId.length())) + "...";
                    }
                } else if (vehicleObj instanceof String) {
                    String vehicleStr = (String) vehicleObj;
                    if (!vehicleStr.isEmpty() && !vehicleStr.equals("null")) {
                        return vehicleStr;
                    }
                }
            }

            return "Không rõ biển số";
        } catch (Exception e) {
            Log.e(TAG, "Error extracting vehicle plate", e);
            return "Lỗi biển số";
        }
    }

    private String extractObjectId(JSONObject idObj) {
        if (idObj == null) return null;
        try {
            // Kiểm tra các trường ID phổ biến
            if (idObj.has("$oid")) {
                return idObj.getString("$oid");
            }
            if (idObj.has("_id")) {
                return idObj.getString("_id");
            }
            if (idObj.has("$id")) {
                Object idValue = idObj.get("$id");
                if (idValue instanceof String) return (String) idValue;
                if (idValue instanceof JSONObject) {
                    return extractObjectId((JSONObject) idValue);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractTimeFromDate(JSONObject obj, String timeField) {
        try {
            if (!obj.has(timeField)) return null;

            Object timeObj = obj.get(timeField);
            if (timeObj instanceof String) {
                return (String) timeObj;
            } else if (timeObj instanceof JSONObject) {
                JSONObject timeJson = (JSONObject) timeObj;
                if (timeJson.has("$date")) {
                    return timeJson.getString("$date");
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isValidTimeString(String timeString) {
        return timeString != null && !timeString.isEmpty() && !timeString.equals("null") && !timeString.trim().isEmpty();
    }

    private String extractDateFromTimestamp(Date timestamp) {
        if (timestamp == null) return "";
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            return dateFormat.format(timestamp);
        } catch (Exception e) {
            return "";
        }
    }

    private void handleVolleyError(VolleyError error) {
        String errorMessage = getErrorMessage(error);
        Log.e(TAG, "Volley error: " + errorMessage, error);

        runOnUiThread(() -> {
            showStatsError("Không thể tải dữ liệu");
        });
    }

    private String getErrorMessage(VolleyError error) {
        if (error instanceof TimeoutError) return "Timeout";
        if (error instanceof NoConnectionError) return "No connection";
        if (error instanceof NetworkError) return "Network error";
        if (error instanceof ServerError) {
            if (error.networkResponse != null) {
                return "Server error (" + error.networkResponse.statusCode + ")";
            }
            return "Server error";
        }
        return "Unknown error";
    }

    private void updatePieChart(int availableSpots, int parkedCars) {
        if (pieChart == null) return;

        try {
            ArrayList<PieEntry> entries = new ArrayList<>();

            if (availableSpots > 0) {
                entries.add(new PieEntry((float) availableSpots, "Chỗ trống"));
            }
            if (parkedCars > 0) {
                entries.add(new PieEntry((float) parkedCars, "Đã đỗ"));
            }

            if (entries.isEmpty()) {
                entries.add(new PieEntry(100f, "Không có dữ liệu"));
            }

            PieDataSet dataSet = new PieDataSet(entries, "");
            dataSet.setSliceSpace(3f);
            dataSet.setSelectionShift(5f);

            ArrayList<Integer> colors = new ArrayList<>();
            if (availableSpots > 0) colors.add(Color.parseColor("#10B981"));
            if (parkedCars > 0) colors.add(Color.parseColor("#EF4444"));
            if (entries.size() == 1 && entries.get(0).getLabel().equals("Không có dữ liệu")) {
                colors.clear();
                colors.add(Color.GRAY);
            }

            dataSet.setColors(colors);
            dataSet.setValueTextColor(Color.WHITE);
            dataSet.setValueTextSize(14f);
            dataSet.setValueFormatter(new PercentFormatter(pieChart));

            PieData pieData = new PieData(dataSet);
            pieChart.setData(pieData);
            pieChart.animateY(1000);
            pieChart.invalidate();

            updatePercentageLabels(availableSpots, parkedCars);

        } catch (Exception e) {
            Log.e(TAG, "Error updating pie chart", e);
        }
    }

    private void updatePercentageLabels(int availableSpots, int parkedCars) {
        try {
            int total = availableSpots + parkedCars;

            if (total > 0) {
                float availablePercent = ((float) availableSpots / total) * 100;
                float occupiedPercent = ((float) parkedCars / total) * 100;

                setTextSafe(tvEmptyPercentage, String.format(Locale.getDefault(), "(%.0f%%)", availablePercent));
                setTextSafe(tvOccupiedPercentage, String.format(Locale.getDefault(), "(%.0f%%)", occupiedPercent));
            } else {
                setTextSafe(tvEmptyPercentage, "(0%)");
                setTextSafe(tvOccupiedPercentage, "(0%)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating percentage labels", e);
        }
    }

    private void showStatsError(String message) {
        runOnUiThread(() -> {
            setInitialValues();
            setupEmptyPieChart();
            Toast.makeText(TrangChuActivity.this, message, Toast.LENGTH_SHORT).show();
            isLoading = false;
        });
    }

    private void updateCurrentTime() {
        try {
            setTextSafe(tvCurrentTime, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            setTextSafe(tvCurrentDate, new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date()));
        } catch (Exception e) {
            Log.e(TAG, "Error updating time", e);
        }
    }

    private void updateLastUpdateTime() {
        try {
            String currentTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            setTextSafe(tvLastUpdate, "Cập nhật lúc: " + currentTime);
        } catch (Exception e) {
            Log.e(TAG, "Error updating last update time", e);
        }
    }

    // Public methods
    public void refreshData() { if (!isLoading) loadDashboardData(); }
    public int getTotalParkingSpots() { return TOTAL_PARKING_SPOTS; }
    public int getTotalTodayCars() { return totalTodayCars; }
    public int getTotalTodayRevenue() { return totalTodayRevenue; }
    public int getCurrentAvailableSpots() { return currentAvailableSpots; }
    public int getCurrentParkedCars() { return currentParkedCount; }

    // Menu navigation
    public void openPaymentActivity(View view) { startActivity(new Intent(this, ThanhToanActivity.class)); }
    public void openManagementActivity(View view) { Toast.makeText(this, "Tính năng quản lý sẽ sớm có", Toast.LENGTH_SHORT).show(); }
    public void openStatisticsActivity(View view) { Toast.makeText(this, "Tính năng thống kê sẽ sớm có", Toast.LENGTH_SHORT).show(); }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (!isLoading) loadDashboardData();
            updateCurrentTime();
            updateLastUpdateTime();

            if (uiUpdateHandler != null && timeUpdateRunnable != null) {
                uiUpdateHandler.post(timeUpdateRunnable);
            }
            if (autoRefreshHandler != null && autoRefreshRunnable != null) {
                autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (uiUpdateHandler != null && timeUpdateRunnable != null) {
                uiUpdateHandler.removeCallbacks(timeUpdateRunnable);
            }
            if (autoRefreshHandler != null && autoRefreshRunnable != null) {
                autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (requestQueue != null) requestQueue.cancelAll(this);
            if (uiUpdateHandler != null) {
                uiUpdateHandler.removeCallbacksAndMessages(null);
                uiUpdateHandler = null;
            }
            if (autoRefreshHandler != null) {
                autoRefreshHandler.removeCallbacksAndMessages(null);
                autoRefreshHandler = null;
            }
            if (currentParkedCars != null) {
                currentParkedCars.clear();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }
}

// Data model class
class ParkedCar {
    public String plateNumber, timeIn, timeOut, _id, status;
    public long entryTimestamp, exitTimestamp;
    public int fee;
    public boolean paid;

    public ParkedCar() {
        this.entryTimestamp = System.currentTimeMillis();
        this.status = "IN_PROGRESS";
        this.paid = false;
    }

    public ParkedCar(String plateNumber, String timeIn) {
        this();
        this.plateNumber = plateNumber;
        this.timeIn = timeIn;
    }

    public ParkedCar(String plateNumber, String timeIn, String _id, long timestamp, String status) {
        this();
        this.plateNumber = plateNumber;
        this.timeIn = timeIn;
        this._id = _id;
        this.entryTimestamp = timestamp;
        this.status = status != null ? status : "IN_PROGRESS";
    }

    public boolean isInProgress() { return "IN_PROGRESS".equals(status); }
    public boolean isCompleted() { return "COMPLETED".equals(status); }

    // Getters
    public String getPlateNumber() { return plateNumber; }
    public String getTimeIn() { return timeIn; }
    public String getTimeOut() { return timeOut; }
    public long getEntryTimestamp() { return entryTimestamp; }
    public long getExitTimestamp() { return exitTimestamp; }
    public String get_id() { return _id; }
    public String getStatus() { return status; }
    public int getFee() { return fee; }
    public boolean isPaid() { return paid; }
}