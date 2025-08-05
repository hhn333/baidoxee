package com.example.baidoxee;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TrangChuActivity extends BaseActivity {

    // UI Components
    BottomNavigationView bottomNavigationView;
    TextView tvParkingSpots, tvParkedCars, tvTodayCars, tvRevenue;
    LinearLayout recentActivityContainer;
    TextView tvCurrentTime, tvCurrentDate;

    // Constants
    private static final int TOTAL_PARKING_SPOTS = 13;
    private static final String TAG = "TrangChuActivity";
    private static final long AUTO_REFRESH_INTERVAL = 30000; // 30 seconds

    // Data
    private List<ParkedCar> currentParkedCars;
    private List<ActivityRecord> todayActivities;

    // Handlers
    private Handler uiUpdateHandler;
    private Handler autoRefreshHandler;
    private Runnable timeUpdateRunnable;
    private Runnable autoRefreshRunnable;

    // Loading state
    private boolean isLoading = false;

    // Data Classes
    public static class ParkedCar {
        String plateNumber;
        String timeIn;
        long entryTimestamp;
        String _id; // MongoDB ID

        public ParkedCar(String plateNumber, String timeIn) {
            this.plateNumber = plateNumber;
            this.timeIn = timeIn;
            this.entryTimestamp = System.currentTimeMillis();
        }

        public ParkedCar(String plateNumber, String timeIn, String _id, long timestamp) {
            this.plateNumber = plateNumber;
            this.timeIn = timeIn;
            this._id = _id;
            this.entryTimestamp = timestamp;
        }
    }

    public static class ActivityRecord {
        String plateNumber;
        String time;
        String action;
        int fee;
        long timestamp;
        String _id; // MongoDB ID

        public ActivityRecord(String plateNumber, String time, String action, int fee) {
            this.plateNumber = plateNumber;
            this.time = time;
            this.action = action;
            this.fee = fee;
            this.timestamp = System.currentTimeMillis();
        }

        public ActivityRecord(String plateNumber, String time, String action, int fee, String _id, long timestamp) {
            this.plateNumber = plateNumber;
            this.time = time;
            this.action = action;
            this.fee = fee;
            this._id = _id;
            this.timestamp = timestamp;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.trangchu);

        initViews();
        initData();
        setupRealTimeUpdates();
        setupAutoRefresh();
        setupBottomNavigation();

        // Load initial data
        loadDataFromApi();
    }

    private void initViews() {
        try {
            // Main UI components
            bottomNavigationView = findViewById(R.id.bottomNavigation);
            tvParkingSpots = findViewById(R.id.tvParkingSpots);
            tvParkedCars = findViewById(R.id.tvParkedCars);
            tvTodayCars = findViewById(R.id.tvTodayCars);
            tvRevenue = findViewById(R.id.tvRevenue);
            recentActivityContainer = findViewById(R.id.recentActivityContainer);

            // Time components with proper ID
            tvCurrentTime = findViewById(R.id.tvCurrentTime);
            tvCurrentDate = findViewById(R.id.tvCurrentDate);

            Log.d(TAG, "Views initialized - tvCurrentTime: " + (tvCurrentTime != null) +
                    ", tvCurrentDate: " + (tvCurrentDate != null));
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
        }
    }

    private void initData() {
        currentParkedCars = new ArrayList<>();
        todayActivities = new ArrayList<>();
        uiUpdateHandler = new Handler();
        autoRefreshHandler = new Handler();
    }

    private void setupRealTimeUpdates() {
        timeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateCurrentTime();
                if (uiUpdateHandler != null) {
                    uiUpdateHandler.postDelayed(this, 1000);
                }
            }
        };
        uiUpdateHandler.post(timeUpdateRunnable);
    }

    private void setupAutoRefresh() {
        autoRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isLoading) {
                    Log.d(TAG, "Auto refreshing data...");
                    loadDataFromApi();
                }
                if (autoRefreshHandler != null) {
                    autoRefreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL);
                }
            }
        };
        autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL);
    }

    private void setupBottomNavigation() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
            bottomNavigationView.setOnItemSelectedListener(new BottomNavigationView.OnItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    int id = item.getItemId();
                    if (id == R.id.nav_parking) {
                        startActivity(new Intent(TrangChuActivity.this, XeVaoActivity.class));
                        return true;
                    } else if (id == R.id.nav_exit) {
                        startActivity(new Intent(TrangChuActivity.this, XeraActivity.class));
                        return true;
                    } else if (id == R.id.nav_payment) {
                        startActivity(new Intent(TrangChuActivity.this, ThanhToanActivity.class));
                        return true;
                    } else if (id == R.id.nav_home) {
                        // Already on home, refresh data
                        if (!isLoading) {
                            loadDataFromApi();
                        }
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    private void loadDataFromApi() {
        if (isLoading) {
            Log.d(TAG, "Already loading, skipping...");
            return;
        }

        Log.d(TAG, "Loading data from API...");
        isLoading = true;

        ApiHelper.getActivities(new ApiHelper.OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                Log.d(TAG, "API Response received: " + (jsonData != null ? jsonData.length() + " characters" : "null"));

                try {
                    parseApiResponse(jsonData);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            updateAllDisplays();
                            Toast.makeText(TrangChuActivity.this, "Dữ liệu đã được cập nhật", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing JSON", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            Toast.makeText(TrangChuActivity.this, "Lỗi xử lý dữ liệu: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "API Error: " + errorMessage);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        isLoading = false;
                        String userFriendlyMessage = getUserFriendlyErrorMessage(errorMessage);
                        Toast.makeText(TrangChuActivity.this, userFriendlyMessage, Toast.LENGTH_LONG).show();

                        // Show retry option
                        showRetryOption();
                    }
                });
            }
        });
    }

    private void parseApiResponse(String jsonData) throws Exception {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            Log.w(TAG, "Empty JSON data received");
            return;
        }

        todayActivities.clear();
        currentParkedCars.clear();

        JSONArray activitiesArray = new JSONArray(jsonData);

        // Sắp xếp theo timestamp để xử lý đúng thứ tự
        List<ActivityRecord> tempActivities = new ArrayList<>();

        for (int i = 0; i < activitiesArray.length(); i++) {
            JSONObject activityObject = activitiesArray.getJSONObject(i);

            String _id = activityObject.optString("_id", "");
            String plateNumber = activityObject.optString("plateNumber", "N/A");
            String time = activityObject.optString("time", getCurrentTime());
            String action = activityObject.optString("action", "IN");
            int fee = activityObject.optInt("fee", 0); // Lấy giá vé từ server
            long timestamp = activityObject.optLong("timestamp", System.currentTimeMillis());

            ActivityRecord activity = new ActivityRecord(plateNumber, time, action, fee, _id, timestamp);
            tempActivities.add(activity);
        }

        // Sắp xếp theo timestamp
        tempActivities.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        todayActivities.addAll(tempActivities);

        // Tính toán xe đang đỗ dựa trên activity cuối cùng của mỗi xe
        Map<String, ActivityRecord> latestActivityByPlate = new HashMap<>();
        for (ActivityRecord activity : todayActivities) {
            latestActivityByPlate.put(activity.plateNumber, activity);
        }

        // Xe đang đỗ là những xe có activity cuối cùng là "IN"
        for (ActivityRecord latestActivity : latestActivityByPlate.values()) {
            if ("IN".equals(latestActivity.action)) {
                ParkedCar parkedCar = new ParkedCar(
                        latestActivity.plateNumber,
                        latestActivity.time,
                        latestActivity._id,
                        latestActivity.timestamp
                );
                currentParkedCars.add(parkedCar);
            }
        }

        Log.d(TAG, "Parsed " + todayActivities.size() + " activities, " + currentParkedCars.size() + " currently parked");
    }

    private String getUserFriendlyErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return "Lỗi không xác định";
        }

        if (errorMessage.contains("Network Error")) {
            return "Kiểm tra kết nối mạng và thử lại";
        } else if (errorMessage.contains("HTTP Error 404")) {
            return "Không tìm thấy dữ liệu từ server";
        } else if (errorMessage.contains("HTTP Error 500")) {
            return "Lỗi server, vui lòng thử lại sau";
        } else if (errorMessage.contains("timeout")) {
            return "Kết nối quá chậm, vui lòng thử lại";
        } else {
            return "Lỗi kết nối: " + errorMessage;
        }
    }

    private void showRetryOption() {
        // Simple retry mechanism
        Log.d(TAG, "Showing retry option to user");
        Toast.makeText(this, "Nhấn vào biểu tượng Home để thử lại", Toast.LENGTH_LONG).show();
    }

    private void updateCurrentTime() {
        try {
            if (tvCurrentTime != null) {
                String currentTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
                tvCurrentTime.setText(currentTime);
            }

            if (tvCurrentDate != null) {
                String currentDate = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
                tvCurrentDate.setText(currentDate);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating time", e);
        }
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }

    private void updateAllDisplays() {
        updateParkingStats();
        updateRevenueStats();
        updateRecentActivity();
    }

    private void updateParkingStats() {
        try {
            int parkedCount = currentParkedCars.size();
            int availableSpots = TOTAL_PARKING_SPOTS - parkedCount;

            if (tvParkingSpots != null) {
                tvParkingSpots.setText(String.valueOf(Math.max(0, availableSpots)));
            }
            if (tvParkedCars != null) {
                tvParkedCars.setText(String.valueOf(parkedCount));
            }

            Log.d(TAG, "Parking stats - Available: " + availableSpots + ", Parked: " + parkedCount);
        } catch (Exception e) {
            Log.e(TAG, "Error updating parking stats", e);
        }
    }

    private void updateRevenueStats() {
        try {
            int totalActivities = todayActivities.size();
            int totalRevenue = 0;
            int outCount = 0;

            for (ActivityRecord activity : todayActivities) {
                if ("OUT".equals(activity.action)) {
                    totalRevenue += activity.fee;
                    outCount++;
                }
            }

            if (tvTodayCars != null) {
                tvTodayCars.setText(String.valueOf(totalActivities));
            }
            if (tvRevenue != null) {
                tvRevenue.setText(NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(totalRevenue) + " đ");
            }

            Log.d(TAG, "Revenue stats - Total activities: " + totalActivities + ", Revenue: " + totalRevenue + ", Out count: " + outCount);
        } catch (Exception e) {
            Log.e(TAG, "Error updating revenue stats", e);
        }
    }

    private void updateRecentActivity() {
        if (recentActivityContainer == null) {
            Log.w(TAG, "recentActivityContainer not found");
            return;
        }

        try {
            // Clear existing dynamic content
            recentActivityContainer.removeAllViews();

            if (todayActivities.isEmpty()) {
                TextView noDataText = new TextView(this);
                noDataText.setText("Chưa có hoạt động hôm nay");
                noDataText.setTextSize(16);
                noDataText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                noDataText.setPadding(16, 32, 16, 32);
                noDataText.setGravity(android.view.Gravity.CENTER);
                recentActivityContainer.addView(noDataText);
                return;
            }

            // Sort activities by timestamp (newest first)
            todayActivities.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));

            // Show only the most recent 3 activities
            int displayCount = Math.min(3, todayActivities.size());
            for (int i = 0; i < displayCount; i++) {
                LinearLayout activityRow = createActivityRow(todayActivities.get(i));
                recentActivityContainer.addView(activityRow);

                // Add divider except for last item
                if (i < displayCount - 1) {
                    View divider = new View(this);
                    divider.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1));
                    divider.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                    divider.setAlpha(0.3f);
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) divider.getLayoutParams();
                    params.setMargins(0, 8, 0, 8);
                    recentActivityContainer.addView(divider);
                }
            }

            Log.d(TAG, "Updated recent activity - showing " + displayCount + " out of " + todayActivities.size() + " activities");
        } catch (Exception e) {
            Log.e(TAG, "Error updating recent activity", e);
        }
    }

    private LinearLayout createActivityRow(ActivityRecord activity) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(convertDpToPx(4), convertDpToPx(8), convertDpToPx(4), convertDpToPx(8));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Action arrow - weight 0.5
        TextView arrow = new TextView(this);
        arrow.setTextSize(20);
        arrow.setGravity(android.view.Gravity.CENTER);
        arrow.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.5f));

        // Plate number - weight 1.5
        TextView plateText = new TextView(this);
        plateText.setText(activity.plateNumber);
        plateText.setTextSize(16);
        plateText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f));

        // Time - weight 1
        TextView timeText = new TextView(this);
        timeText.setText(activity.time);
        timeText.setTextSize(16);
        timeText.setGravity(android.view.Gravity.CENTER);
        timeText.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        timeText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Fee - weight 1
        TextView feeText = new TextView(this);
        feeText.setTextSize(16);
        feeText.setGravity(android.view.Gravity.END);
        feeText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Set colors and content based on action
        if ("IN".equals(activity.action)) {
            // Xe vào - màu đỏ, không hiển thị giá vé
            arrow.setText("→");
            arrow.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            plateText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            feeText.setText(""); // Để trống cho xe vào - không hiển thị giá vé từ server
            feeText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        } else if ("OUT".equals(activity.action)) {
            // Xe ra - màu xanh, hiển thị giá vé từ server
            arrow.setText("←");
            arrow.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            plateText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));

            // Hiển thị giá vé từ server cho xe ra
            feeText.setText(NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(activity.fee) + "đ");
            feeText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        }

        row.addView(arrow);
        row.addView(plateText);
        row.addView(timeText);
        row.addView(feeText);

        return row;
    }

    private int convertDpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            // Clean up handlers
            if (uiUpdateHandler != null && timeUpdateRunnable != null) {
                uiUpdateHandler.removeCallbacks(timeUpdateRunnable);
            }

            if (autoRefreshHandler != null && autoRefreshRunnable != null) {
                autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
            }

            Log.d(TAG, "TrangChuActivity destroyed, handlers cleaned up");
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        try {
            // Refresh data when returning to activity
            if (!isLoading) {
                loadDataFromApi();
            }
            updateCurrentTime();

            // Resume time updates
            if (uiUpdateHandler != null && timeUpdateRunnable != null) {
                uiUpdateHandler.post(timeUpdateRunnable);
            }

            // Resume auto refresh
            if (autoRefreshHandler != null && autoRefreshRunnable != null) {
                autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL);
            }

            Log.d(TAG, "TrangChuActivity resumed");
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        try {
            // Pause updates to save battery
            if (uiUpdateHandler != null && timeUpdateRunnable != null) {
                uiUpdateHandler.removeCallbacks(timeUpdateRunnable);
            }

            if (autoRefreshHandler != null && autoRefreshRunnable != null) {
                autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
            }

            Log.d(TAG, "TrangChuActivity paused");
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause", e);
        }
    }
}