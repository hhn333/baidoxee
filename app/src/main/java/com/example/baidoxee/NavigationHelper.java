package com.example.baidoxee;

import android.app.Activity;
import android.content.Intent;
import androidx.annotation.NonNull;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import android.view.MenuItem;

public class NavigationHelper {

    public static void setupBottomNavigation(final Activity activity, BottomNavigationView bottomNavigationView, int selectedItemId) {

        // Đánh dấu item đang được chọn
        bottomNavigationView.setSelectedItemId(selectedItemId);

        bottomNavigationView.setOnItemSelectedListener(new BottomNavigationView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();

                if (id == selectedItemId) {
                    // Nếu chọn đúng Activity hiện tại thì không làm gì
                    return true;
                }

                Intent intent = null;
                if (id == R.id.nav_home) {
                    intent = new Intent(activity, TrangChuActivity.class);
                } else if (id == R.id.nav_parking) {
                    intent = new Intent(activity, XeVaoActivity.class);
                } else if (id == R.id.nav_exit) {
                    intent = new Intent(activity, XeraActivity.class);
                } else if (id == R.id.nav_payment) {
                    intent = new Intent(activity, ThanhToanActivity.class);
                }

                if (intent != null) {
                    // Bật FLAG_ACTIVITY_SINGLE_TOP hoặc FLAG_ACTIVITY_CLEAR_TOP nếu cần tránh stack nhiều Activity giống nhau
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    activity.startActivity(intent);
                    activity.overridePendingTransition(0, 0);  // Tắt hiệu ứng chuyển động (tuỳ chọn)
                    return true;
                }
                return false;

            }
        });
    }
}
