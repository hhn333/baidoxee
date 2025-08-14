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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TrangChuActivity extends AppCompatActivity {
    private static final String TAG = "TrangChuActivity";
    private static final int TOTAL_PARKING_SPOTS = 13;
    private static final long AUTO_REFRESH_INTERVAL = 30000;
    private static final long TIME_UPDATE_INTERVAL = 1000;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.trangchu);

        initViews();
        initData();
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
        loadDashboardStatsFromAPI();
    }

    private void loadDashboardStatsFromAPI() {
        ApiHelper.getDashboardStats(new ApiHelper.OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                try {
                    JSONObject response = new JSONObject(jsonData);
                    if (response.has("success") && response.getBoolean("success")) {
                        JSONObject data = response.getJSONObject("data");
                        updateDashboardStatsFromAPI(data);
                    } else {
                        loadDashboardStatsFromActivities();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing dashboard stats from API", e);
                    loadDashboardStatsFromActivities();
                }
            }

            @Override
            public void onError(String errorMessage) {
                loadDashboardStatsFromActivities();
            }
        });
    }

    private void loadDashboardStatsFromActivities() {
        ApiHelper.getActivities(new ApiHelper.OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                try {
                    JSONObject calculatedStats = calculateStatsFromActivities(jsonData);
                    updateDashboardStatsFromAPI(calculatedStats);
                } catch (Exception e) {
                    Log.e(TAG, "Error calculating stats from activities", e);
                    showStatsError("Lỗi tính toán thống kê từ dữ liệu hoạt động");
                } finally {
                    runOnUiThread(() -> isLoading = false);
                }
            }

            @Override
            public void onError(String errorMessage) {
                showStatsError("Không thể tải dữ liệu thống kê");
                runOnUiThread(() -> isLoading = false);
            }
        });
    }

    private JSONObject calculateStatsFromActivities(String jsonData) throws JSONException {
        JSONArray activitiesArray;

        if (jsonData.trim().startsWith("[")) {
            activitiesArray = new JSONArray(jsonData);
        } else {
            JSONObject response = new JSONObject(jsonData);
            if (response.has("success") && response.getBoolean("success")) {
                activitiesArray = response.getJSONArray("data");
            } else if (response.has("data")) {
                activitiesArray = response.getJSONArray("data");
            } else {
                activitiesArray = new JSONArray();
            }
        }

        Set<String> uniqueVehicles = new HashSet<>();
        int inProgressCount = 0;
        int totalRevenue = 0;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        for (int i = 0; i < activitiesArray.length(); i++) {
            JSONObject activityObject = activitiesArray.getJSONObject(i);

            String plateNumber = getFieldValue(activityObject, new String[]{"plateNumber", "plate_number", "licensePlate", "bien_so_xe", "plate_text"});
            String status = getFieldValue(activityObject, new String[]{"status", "trangThai", "trang_thai"});
            int fee = getIntFieldValue(activityObject, new String[]{"fee", "phiGuiXe", "phi_gui_xe", "amount"});
            String timeIn = getTimeInField(activityObject);

            boolean isToday = isDateToday(timeIn, today);

            if (isToday) {
                uniqueVehicles.add(plateNumber);

                if ("IN_PROGRESS".equals(status)) {
                    inProgressCount++;
                }

                if (fee > 0) {
                    totalRevenue += fee;
                }
            }
        }

        int availableSpots = Math.max(0, TOTAL_PARKING_SPOTS - inProgressCount);

        JSONObject stats = new JSONObject();
        stats.put("availableSpots", availableSpots);
        stats.put("carsParked", inProgressCount);
        stats.put("todayCars", uniqueVehicles.size());
        stats.put("todayRevenue", totalRevenue);

        return stats;
    }

    private String getFieldValue(JSONObject obj, String[] fieldNames) {
        for (String field : fieldNames) {
            String value = obj.optString(field);
            if (!value.isEmpty()) return value;
        }

        // Handle nested vehicle object
        JSONObject vehicle = obj.optJSONObject("vehicle");
        if (vehicle != null) {
            for (String field : fieldNames) {
                String value = vehicle.optString(field);
                if (!value.isEmpty()) return value;
            }
        }

        return fieldNames.length > 2 ? "N/A" : "IN_PROGRESS";
    }

    private int getIntFieldValue(JSONObject obj, String[] fieldNames) {
        for (String field : fieldNames) {
            int value = obj.optInt(field, -1);
            if (value != -1) return value;
        }
        return 0;
    }

    private String getTimeInField(JSONObject activityObject) {
        String timeIn = getFieldValue(activityObject, new String[]{"timeIn", "time_in", "thoiGianVao", "thoi_gian_vao", "createdAt"});

        if (timeIn.isEmpty()) {
            JSONObject timeInObj = activityObject.optJSONObject("timeIn");
            if (timeInObj != null) {
                timeIn = timeInObj.optString("$date", "");
            }
        }
        return timeIn;
    }

    private boolean isDateToday(String timeIn, String today) {
        if (timeIn == null || timeIn.isEmpty()) return false;

        try {
            if (timeIn.contains("T")) {
                String dateOnly = timeIn.substring(0, 10);
                return today.equals(dateOnly);
            }
            return timeIn.startsWith(today);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing date: " + timeIn, e);
            return false;
        }
    }

    private void updateDashboardStatsFromAPI(JSONObject data) {
        runOnUiThread(() -> {
            try {
                int availableSpots = data.optInt("availableSpots", 0);
                int carsParked = data.optInt("carsParked", 0);
                int todayCars = data.optInt("todayCars", 0);
                int todayRevenue = data.optInt("todayRevenue", 0);

                if (availableSpots + carsParked > TOTAL_PARKING_SPOTS) {
                    availableSpots = Math.max(0, TOTAL_PARKING_SPOTS - carsParked);
                }

                this.currentAvailableSpots = availableSpots;
                this.currentParkedCount = carsParked;
                this.totalTodayCars = todayCars;
                this.totalTodayRevenue = todayRevenue;

                setTextSafe(tvParkingSpots, String.valueOf(availableSpots));
                setTextSafe(tvParkedCars, String.valueOf(carsParked));
                setTextSafe(tvTodayCars, String.valueOf(todayCars));

                updatePieChart(availableSpots, carsParked);

            } catch (Exception e) {
                Log.e(TAG, "Error updating dashboard stats UI", e);
                showStatsError("Lỗi cập nhật giao diện thống kê");
            } finally {
                isLoading = false;
            }
        });
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