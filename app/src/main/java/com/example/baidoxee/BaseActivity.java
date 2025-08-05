package com.example.baidoxee;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity extends AppCompatActivity implements NotificationService.NotificationListener {

    private static final String TAG = "BaseActivity";
    private NotificationService notificationService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Khởi tạo notification service
        notificationService = NotificationService.getInstance();

        Log.d(TAG, "BaseActivity created: " + this.getClass().getSimpleName());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Đăng ký để nhận thông báo khi activity active
        if (notificationService != null) {
            notificationService.registerListener(this);
            Log.d(TAG, "Registered for notifications: " + this.getClass().getSimpleName());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Hủy đăng ký khi activity không còn active
        if (notificationService != null) {
            notificationService.unregisterListener();
            Log.d(TAG, "Unregistered from notifications: " + this.getClass().getSimpleName());
        }
    }

    @Override
    public View getRootView() {
        // Trả về root view của activity để hiển thị Snackbar
        return findViewById(android.R.id.content);
    }

    @Override
    public void onVehicleEntered(String plateNumber, String time) {
        // Callback khi có xe vào - các activity con có thể override để xử lý thêm
        Log.d(TAG, "Vehicle entered: " + plateNumber + " at " + time +
                " (Activity: " + this.getClass().getSimpleName() + ")");

        // Có thể thêm logic chung ở đây như vibration, sound, etc.
        onVehicleEnteredCustom(plateNumber, time);
    }

    // Abstract method cho các activity con tùy chỉnh xử lý
    protected void onVehicleEnteredCustom(String plateNumber, String time) {
        // Activity con có thể override method này để thực hiện hành động riêng
        // Ví dụ: refresh data, highlight specific UI elements, etc.
    }

    // Method để get notification service instance (cho activity con nếu cần)
    protected NotificationService getNotificationService() {
        return notificationService;
    }

    // Method để manual trigger refresh notification checking
    protected void refreshNotificationCheck() {
        if (notificationService != null) {
            // Force check ngay lập tức
            notificationService.unregisterListener();
            notificationService.registerListener(this);
        }
    }
}