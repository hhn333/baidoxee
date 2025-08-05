package com.example.baidoxee;

import android.os.Handler;
import android.util.Log;
import android.view.View;
import com.google.android.material.snackbar.Snackbar;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class NotificationService {
    private static final String TAG = "NotificationService";
    private static final long CHECK_INTERVAL = 10000; // 10 giây kiểm tra 1 lần

    private static NotificationService instance;
    private Handler checkHandler;
    private Runnable checkRunnable;
    private boolean isRunning = false;
    private Set<String> processedActivities; // Lưu ID các hoạt động đã xử lý
    private NotificationListener currentListener;

    // Interface để activity đăng ký nhận thông báo
    public interface NotificationListener {
        View getRootView(); // Activity cung cấp root view để hiển thị Snackbar
        void onVehicleEntered(String plateNumber, String time); // Callback khi có xe vào
    }

    private NotificationService() {
        checkHandler = new Handler();
        processedActivities = new HashSet<>();
        initCheckRunnable();
    }

    public static synchronized NotificationService getInstance() {
        if (instance == null) {
            instance = new NotificationService();
        }
        return instance;
    }

    private void initCheckRunnable() {
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && currentListener != null) {
                    checkForNewEntries();
                    checkHandler.postDelayed(this, CHECK_INTERVAL);
                }
            }
        };
    }

    // Activity đăng ký để nhận thông báo
    public void registerListener(NotificationListener listener) {
        this.currentListener = listener;
        if (!isRunning) {
            startChecking();
        }
        Log.d(TAG, "Listener registered: " + listener.getClass().getSimpleName());
    }

    // Activity hủy đăng ký
    public void unregisterListener() {
        this.currentListener = null;
        Log.d(TAG, "Listener unregistered");
    }

    // Bắt đầu kiểm tra
    private void startChecking() {
        if (!isRunning) {
            isRunning = true;
            checkHandler.post(checkRunnable);
            Log.d(TAG, "Notification checking started");
        }
    }

    // Dừng kiểm tra
    public void stopChecking() {
        if (isRunning) {
            isRunning = false;
            checkHandler.removeCallbacks(checkRunnable);
            Log.d(TAG, "Notification checking stopped");
        }
    }

    // Kiểm tra hoạt động mới
    private void checkForNewEntries() {
        Log.d(TAG, "Checking for new vehicle entries...");

        ApiHelper.getActivities(new ApiHelper.OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                try {
                    processNewActivities(jsonData);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing activities", e);
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error fetching activities: " + errorMessage);
            }
        });
    }

    private void processNewActivities(String jsonData) throws Exception {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return;
        }

        JSONArray activitiesArray = new JSONArray(jsonData);
        boolean hasNewEntry = false;

        // Kiểm tra từng hoạt động
        for (int i = 0; i < activitiesArray.length(); i++) {
            JSONObject activityObject = activitiesArray.getJSONObject(i);

            String activityId = activityObject.optString("_id", "");
            String action = activityObject.optString("action", "");
            String plateNumber = activityObject.optString("plateNumber", "");
            String time = activityObject.optString("time", "");

            // Chỉ xử lý hoạt động vào (IN) và chưa được xử lý
            if ("IN".equals(action) && !processedActivities.contains(activityId)) {
                processedActivities.add(activityId);

                if (currentListener != null && !plateNumber.isEmpty()) {
                    // Hiển thị thông báo
                    showVehicleEntryNotification(plateNumber, time);

                    // Gọi callback
                    currentListener.onVehicleEntered(plateNumber, time);
                    hasNewEntry = true;

                    Log.d(TAG, "New vehicle entry detected: " + plateNumber + " at " + time);
                }
            }
        }

        // Giới hạn số lượng ID được lưu để tránh memory leak
        if (processedActivities.size() > 1000) {
            Set<String> recentIds = new HashSet<>();
            int count = 0;
            // Giữ lại 500 ID gần nhất
            for (String id : processedActivities) {
                if (count++ < 500) {
                    recentIds.add(id);
                } else {
                    break;
                }
            }
            processedActivities = recentIds;
        }
    }

    private void showVehicleEntryNotification(String plateNumber, String time) {
        if (currentListener == null) return;

        View rootView = currentListener.getRootView();
        if (rootView == null) return;

        // Format thời gian hiển thị
        String displayTime = formatTimeForDisplay(time);

        // Tạo Snackbar với thông tin xe vào
        Snackbar snackbar = Snackbar.make(
                rootView,
                "🚗 Xe vào: " + plateNumber + " - " + displayTime,
                Snackbar.LENGTH_LONG
        );

        // Tùy chỉnh màu sắc
        snackbar.setBackgroundTint(rootView.getContext().getColor(android.R.color.holo_green_dark));
        snackbar.setTextColor(rootView.getContext().getColor(android.R.color.white));
        snackbar.setActionTextColor(rootView.getContext().getColor(android.R.color.white));

        // Thêm action để đi đến màn hình xe vào
        snackbar.setAction("Xem", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Có thể thêm logic chuyển đến màn hình xe vào nếu cần
                Log.d(TAG, "User clicked to view vehicle entry");
            }
        });

        // Hiển thị thông báo
        snackbar.show();

        Log.d(TAG, "Snackbar notification shown for: " + plateNumber);
    }

    private String formatTimeForDisplay(String timeString) {
        try {
            // Thử parse theo format ISO
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date date = inputFormat.parse(timeString);
            if (date != null) {
                return outputFormat.format(date);
            }
        } catch (Exception e) {
            // Nếu lỗi, thử format đơn giản
            if (timeString.contains(":")) {
                return timeString.substring(0, Math.min(timeString.length(), 5));
            }
        }

        // Fallback: trả về thời gian hiện tại
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }

    // Method để reset processed activities (có thể gọi khi cần)
    public void resetProcessedActivities() {
        processedActivities.clear();
        Log.d(TAG, "Processed activities reset");
    }

    // Method để kiểm tra trạng thái
    public boolean isRunning() {
        return isRunning;
    }

    public int getProcessedActivitiesCount() {
        return processedActivities.size();
    }
}