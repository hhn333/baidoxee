package com.example.baidoxee;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

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
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;
import java.util.TimeZone;

import android.content.Intent;

public class XeVaoActivity extends AppCompatActivity {
    private static final String TAG = "XeVaoActivity";
    private RecyclerView recyclerView;
    private List<xevao> carList;
    private List<xevao> originalCarList;
    private XevaoAdapter adapter;
    private final String URL = "https://baidoxe.onrender.com/api/events"; // Lấy tất cả events
    private RequestQueue requestQueue;
    private BottomNavigationView bottomNavigationView;
    private EditText etSearchPlate;
    private TextView tvTotalCars;

    // Cache tĩnh để tái sử dụng
    private static final SimpleDateFormat UTC_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat UTC_FORMAT_MILLIS = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());
    private static final SimpleDateFormat VN_TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.getDefault());

    static {
        UTC_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        UTC_FORMAT_MILLIS.setTimeZone(TimeZone.getTimeZone("UTC"));
        VN_TIME_FORMAT.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    // Class để lưu thông tin event
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

    // Class đơn giản để sort
    private static class CarWithTime {
        xevao car;
        Date timeIn;

        CarWithTime(xevao car, Date timeIn) {
            this.car = car;
            this.timeIn = timeIn;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.xevao);

        // Khởi tạo views ngay lập tức
        initializeViews();
        initializeNetwork();
        setupBottomNavigation();
        setupSearch();

        // Fetch data ngay
        fetchParkedCars();
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recyclerView);
        bottomNavigationView = findViewById(R.id.bottomNavigation);
        etSearchPlate = findViewById(R.id.etSearchPlate);
        tvTotalCars = findViewById(R.id.tvTotalCars);

        if (recyclerView == null) {
            Log.e(TAG, "RecyclerView not found");
            return;
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        carList = new ArrayList<>();
        originalCarList = new ArrayList<>();
        adapter = new XevaoAdapter(carList);
        recyclerView.setAdapter(adapter);

        // Ẩn text tổng số xe ban đầu
        if (tvTotalCars != null) {
            tvTotalCars.setText("Đang tải...");
        }
    }

    private void initializeNetwork() {
        requestQueue = Volley.newRequestQueue(this);
    }

    private void fetchParkedCars() {
        if (isFinishing()) return;

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, URL, null,
                response -> {
                    if (response != null) parseParkedCars(response);
                },
                this::handleVolleyError
        );

        // Giảm timeout để phản hồi nhanh hơn
        request.setRetryPolicy(new DefaultRetryPolicy(5000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(request);
    }

    private void parseParkedCars(JSONArray response) {
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

            // Tìm xe đang đỗ
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

            // Tạo danh sách xe đang đỗ
            List<CarWithTime> carsWithTime = new ArrayList<>();
            for (VehicleEvent event : vehicleStatus.values()) {
                String timeIn = formatToVietnamTime(extractTimeFromDate(event.originalObject, "timestamp"));

                // Tạo data URI cho base64 image
                String imageDataUri = null;
                if (!event.plateImage.isEmpty() && !event.plateImage.equals("null")) {
                    if (event.plateImage.startsWith("/9j") || event.plateImage.startsWith("iVBORw")) {
                        imageDataUri = "data:image/jpeg;base64," + event.plateImage;
                    } else if (event.plateImage.startsWith("data:image")) {
                        imageDataUri = event.plateImage;
                    }
                }

                xevao car = new xevao(event.plateText, timeIn, imageDataUri);
                carsWithTime.add(new CarWithTime(car, event.timestamp));
            }

            // Sort theo thời gian vào (mới nhất trước)
            Collections.sort(carsWithTime, (a, b) -> {
                if (a.timeIn == null && b.timeIn == null) return 0;
                if (a.timeIn == null) return 1;
                if (b.timeIn == null) return -1;
                return b.timeIn.compareTo(a.timeIn);
            });

            // Chuyển sang temp list
            List<xevao> tempCarList = new ArrayList<>();
            List<xevao> tempOriginalCarList = new ArrayList<>();
            for (CarWithTime carWithTime : carsWithTime) {
                tempCarList.add(carWithTime.car);
                tempOriginalCarList.add(carWithTime.car);
            }

            // Update UI trên main thread
            runOnUiThread(() -> {
                carList.clear();
                originalCarList.clear();
                carList.addAll(tempCarList);
                originalCarList.addAll(tempOriginalCarList);

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                if (tvTotalCars != null) {
                    tvTotalCars.setText("Tổng số xe đang đỗ: " + carList.size());
                }
            });

            Log.d(TAG, "Processed " + allEvents.size() + " events, found " + tempCarList.size() + " parked cars");

        } catch (Exception e) {
            Log.e(TAG, "Error parsing parked cars", e);
            runOnUiThread(() -> {
                if (tvTotalCars != null) {
                    tvTotalCars.setText("Lỗi tải dữ liệu");
                }
            });
        }
    }

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

    private String formatToVietnamTime(String isoTime) {
        if (!isValidTimeString(isoTime)) return "--:--";

        try {
            Date utcDate = parseTimeString(isoTime);
            if (utcDate == null) return "--:--";

            return VN_TIME_FORMAT.format(utcDate);
        } catch (Exception e) {
            return "--:--";
        }
    }

    private void handleVolleyError(VolleyError error) {
        String errorMessage = getErrorMessage(error);
        Log.e(TAG, "Volley error: " + errorMessage, error);

        // Hiển thị lỗi trên UI
        runOnUiThread(() -> {
            if (tvTotalCars != null) {
                tvTotalCars.setText("Không thể tải dữ liệu");
            }
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

    private void setupBottomNavigation() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_parking);
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    startActivity(new Intent(this, TrangChuActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                    finish();
                    return true;
                } else if (id == R.id.nav_parking) {
                    // Chỉ refresh khi thực sự cần
                    return true;
                } else if (id == R.id.nav_exit) {
                    startActivity(new Intent(this, XeraActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    finish();
                    return true;
                } else if (id == R.id.nav_payment) {
                    startActivity(new Intent(this, ThanhToanActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    finish();
                    return true;
                }
                return false;
            });
        }
    }

    private void setupSearch() {
        if (etSearchPlate != null) {
            etSearchPlate.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterCars(s.toString().trim());
                }
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void filterCars(String searchText) {
        if (adapter == null || originalCarList == null) return;

        List<xevao> filteredList = new ArrayList<>();
        if (searchText.isEmpty()) {
            filteredList.addAll(originalCarList);
        } else {
            String lowerSearchText = searchText.toLowerCase();
            for (xevao car : originalCarList) {
                if (car != null && car.getVehicle() != null &&
                        car.getVehicle().toLowerCase().contains(lowerSearchText)) {
                    filteredList.add(car);
                }
            }
        }

        carList.clear();
        carList.addAll(filteredList);
        adapter.notifyDataSetChanged();

        if (tvTotalCars != null) {
            tvTotalCars.setText("Tổng số xe đang đỗ: " + filteredList.size());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (requestQueue != null) requestQueue.cancelAll(this);
        if (carList != null) carList.clear();
        if (originalCarList != null) originalCarList.clear();
    }
}