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
import java.util.TimeZone;
import java.util.Collections;
import java.util.Comparator;
import android.content.Intent;

public class XeraActivity extends AppCompatActivity {
    private static final String TAG = "XeraActivity", EVENTS_URL = "https://baidoxe.onrender.com/api/events";

    private RecyclerView recyclerView;
    private BottomNavigationView bottomNavigationView;
    private EditText etSearchPlate;
    private TextView tvTotalCars;
    private List<xera> logList, originalLogList;
    private XeraAdapter adapter;
    private RequestQueue requestQueue;

    // Cache tĩnh để tái sử dụng - tối ưu hiệu suất
    private static final SimpleDateFormat UTC_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat UTC_FORMAT_MILLIS = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());
    private static final SimpleDateFormat VN_TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    static {
        UTC_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        UTC_FORMAT_MILLIS.setTimeZone(TimeZone.getTimeZone("UTC"));
        VN_TIME_FORMAT.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    // Class đơn giản để sort
    private static class TripWithTime {
        xera trip;
        Date exitTime;

        TripWithTime(xera trip, Date exitTime) {
            this.trip = trip;
            this.exitTime = exitTime;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.xera);

        // Khởi tạo views ngay lập tức
        initializeViews();
        initializeNetwork();
        setupBottomNavigation();
        setupSearch();

        // Fetch data ngay
        fetchEvents();
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
        logList = new ArrayList<>();
        originalLogList = new ArrayList<>();
        adapter = new XeraAdapter(logList);
        recyclerView.setAdapter(adapter);

        // Hiển thị loading
        if (tvTotalCars != null) {
            tvTotalCars.setText("Đang tải...");
        }

        // Tối ưu EditText - tắt hoàn toàn suggestions và autocomplete
        if (etSearchPlate != null) {
            // Tắt tất cả các loại suggestions
            etSearchPlate.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    | android.text.InputType.TYPE_TEXT_VARIATION_FILTER);

            etSearchPlate.setSingleLine(true);
            etSearchPlate.setMaxLines(1);

            // Tắt autofill
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                etSearchPlate.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
            }

            // Tắt spell check và text prediction
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                etSearchPlate.setPrivateImeOptions("nm,com.google.android.inputmethod.latin.noMicrophoneKey");
            }
        }
    }

    private void initializeNetwork() {
        requestQueue = Volley.newRequestQueue(this);
    }

    private void fetchEvents() {
        if (isFinishing()) return;

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, EVENTS_URL, null,
                response -> {
                    if (response != null && response.length() > 0) parseEvents(response);
                    else updateUIWithData(new ArrayList<>(), 0);
                },
                this::handleVolleyError
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

        // Giảm timeout để phản hồi nhanh hơn
        request.setRetryPolicy(new DefaultRetryPolicy(5000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(request);
    }

    private void parseEvents(JSONArray response) {
        try {
            // Sử dụng HashMap để tìm kiếm nhanh
            Map<String, EventData> enterEvents = new HashMap<>();
            Map<String, EventData> exitEvents = new HashMap<>();

            // Thu thập events nhanh
            for (int i = 0; i < response.length(); i++) {
                JSONObject event = response.getJSONObject(i);
                if (event == null) continue;

                String eventType = event.optString("event_type", "");
                String plateText = event.optString("plate_text", "");
                if (!isValidPlateText(plateText)) continue;

                String timestamp = extractTimestampFromEvent(event);
                if (!isValidTimeString(timestamp)) continue;

                EventData eventData = new EventData(plateText, timestamp);
                if ("enter".equals(eventType)) {
                    enterEvents.put(plateText, eventData);
                } else if ("exit".equals(eventType)) {
                    exitEvents.put(plateText, eventData);
                }
            }

            // Tạo completed trips nhanh
            createCompletedTripsOptimized(enterEvents, exitEvents);

        } catch (Exception e) {
            Log.e(TAG, "Error parsing events", e);
            updateUIWithData(new ArrayList<>(), 0);
        }
    }

    private void createCompletedTripsOptimized(Map<String, EventData> enterEvents, Map<String, EventData> exitEvents) {
        List<TripWithTime> tripsWithTime = new ArrayList<>();
        String todayDateString = getTodayDateString();
        int totalCompleted = 0;

        for (Map.Entry<String, EventData> exitEntry : exitEvents.entrySet()) {
            String plateText = exitEntry.getKey();
            EventData exitData = exitEntry.getValue();
            EventData enterData = enterEvents.get(plateText);

            if (enterData != null) {
                totalCompleted++;

                // Chỉ xử lý trips hôm nay
                if (isTimestampToday(exitData.timestamp, todayDateString)) {
                    String timeIn = formatToVietnamTime(enterData.timestamp);
                    String timeOut = formatToVietnamTime(exitData.timestamp);

                    xera trip = new xera(plateText, timeIn, timeOut);
                    trip.setExitTimestamp(exitData.timestamp);

                    Date exitTime = parseTimeString(exitData.timestamp);
                    tripsWithTime.add(new TripWithTime(trip, exitTime));
                }
            }
        }

        // Sort nhanh - mới nhất trước
        Collections.sort(tripsWithTime, (a, b) -> {
            if (a.exitTime == null && b.exitTime == null) return 0;
            if (a.exitTime == null) return 1;
            if (b.exitTime == null) return -1;
            return b.exitTime.compareTo(a.exitTime);
        });

        // Chuyển sang list cuối
        List<xera> finalTripList = new ArrayList<>();
        for (TripWithTime tripWithTime : tripsWithTime) {
            finalTripList.add(tripWithTime.trip);
        }

        // Update UI với data đã sẵn sàng
        updateUIWithData(finalTripList, finalTripList.size());
    }

    private void updateUIWithData(List<xera> tripList, int todayTrips) {
        runOnUiThread(() -> {
            logList.clear();
            originalLogList.clear();
            logList.addAll(tripList);
            originalLogList.addAll(tripList);

            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            if (tvTotalCars != null) {
                tvTotalCars.setText("Tổng số xe đã ra: " + todayTrips);
            }
        });
    }

    private Date parseTimeString(String timeString) {
        if (!isValidTimeString(timeString)) return null;

        try {
            String cleaned = cleanTimestamp(timeString);

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

    private String cleanTimestamp(String timestamp) {
        String cleaned = timestamp.trim();
        if (cleaned.endsWith("Z")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        else if (cleaned.contains("+") || cleaned.lastIndexOf("-") > 10) {
            int tzIndex = Math.max(cleaned.lastIndexOf("+"), cleaned.lastIndexOf("-"));
            if (tzIndex > 10) cleaned = cleaned.substring(0, tzIndex);
        }
        if (cleaned.contains(".")) {
            String[] parts = cleaned.split("\\.");
            if (parts.length == 2) {
                String millisPart = parts[1];
                if (millisPart.length() > 3) millisPart = millisPart.substring(0, 3);
                else while (millisPart.length() < 3) millisPart += "0";
                cleaned = parts[0] + "." + millisPart;
            }
        }
        return cleaned;
    }

    private boolean isValidPlateText(String plateText) {
        return plateText != null && !plateText.trim().isEmpty() && !plateText.equals("null");
    }

    private String getTodayDateString() {
        try {
            return DATE_FORMAT.format(new Date());
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isTimestampToday(String timestamp, String todayDateString) {
        if (!isValidTimeString(timestamp) || todayDateString == null || todayDateString.isEmpty()) return false;
        try {
            Date timestampDate = parseTimeString(timestamp);
            if (timestampDate == null) return false;
            return todayDateString.equals(DATE_FORMAT.format(timestampDate));
        } catch (Exception e) {
            return false;
        }
    }

    private String extractTimestampFromEvent(JSONObject event) {
        try {
            if (event.has("timestamp")) {
                String timestamp = extractStringFromTimestampObject(event.get("timestamp"));
                if (timestamp != null) return timestamp;
            }
            if (event.has("created_at")) return extractStringFromTimestampObject(event.get("created_at"));
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractStringFromTimestampObject(Object timestampObj) {
        try {
            if (timestampObj instanceof String) return (String) timestampObj;
            else if (timestampObj instanceof JSONObject) {
                JSONObject timestampJson = (JSONObject) timestampObj;
                if (timestampJson.has("$date")) return timestampJson.getString("$date");
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isValidTimeString(String timeString) {
        return timeString != null && !timeString.trim().isEmpty() && !timeString.equals("null");
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

    private void filterLogs(String searchText) {
        if (adapter == null || originalLogList == null) return;

        List<xera> filteredList = new ArrayList<>();
        if (searchText.isEmpty()) {
            filteredList.addAll(originalLogList);
        } else {
            String lowerSearchText = searchText.toLowerCase();
            for (xera log : originalLogList) {
                if (log != null && log.getPlateText() != null &&
                        log.getPlateText().toLowerCase().contains(lowerSearchText)) {
                    filteredList.add(log);
                }
            }
        }

        logList.clear();
        logList.addAll(filteredList);
        adapter.notifyDataSetChanged();

        // Sửa để đồng bộ với XeVaoActivity - luôn hiện "Tổng số xe đã ra: x"
        if (tvTotalCars != null) {
            tvTotalCars.setText("Tổng số xe đã ra: " + filteredList.size());
        }
    }
    private void setupBottomNavigation() {
        if (bottomNavigationView == null) return;
        bottomNavigationView.setSelectedItemId(R.id.nav_exit);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                startActivity(new Intent(this, TrangChuActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
                return true;
            } else if (itemId == R.id.nav_parking) {
                startActivity(new Intent(this, XeVaoActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                finish();
                return true;
            } else if (itemId == R.id.nav_exit) {
                return true;
            } else if (itemId == R.id.nav_payment) {
                startActivity(new Intent(this, ThanhToanActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                finish();
                return true;
            }
            return false;
        });
    }

    private void setupSearch() {
        if (etSearchPlate != null) {
            // Đặt lại input type để tắt hoàn toàn suggestions
            etSearchPlate.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    | android.text.InputType.TYPE_TEXT_VARIATION_FILTER);

            // Chỉ set threshold nếu là AutoCompleteTextView
            try {
                if (etSearchPlate instanceof android.widget.AutoCompleteTextView) {
                    ((android.widget.AutoCompleteTextView) etSearchPlate).setThreshold(Integer.MAX_VALUE);
                    ((android.widget.AutoCompleteTextView) etSearchPlate).setAdapter(null);
                }
            } catch (Exception e) {
                // Ignore - không phải AutoCompleteTextView
            }

            etSearchPlate.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterLogs(s.toString().trim());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void handleVolleyError(VolleyError error) {
        String errorMessage = getErrorMessage(error);
        Log.e(TAG, "Volley error: " + errorMessage, error);

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

    public void refreshData() {
        fetchEvents();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchEvents();
        // Tắt focus cho EditText khi resume và clear suggestions
        if (etSearchPlate != null) {
            etSearchPlate.clearFocus();
            // Chỉ dismiss dropdown nếu là AutoCompleteTextView
            try {
                if (etSearchPlate instanceof android.widget.AutoCompleteTextView) {
                    ((android.widget.AutoCompleteTextView) etSearchPlate).dismissDropDown();
                }
            } catch (Exception e) {
                // Ignore - không phải AutoCompleteTextView
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (requestQueue != null) {
            requestQueue.cancelAll(this);
        }
        if (logList != null) logList.clear();
        if (originalLogList != null) originalLogList.clear();
    }

    // EventData class đơn giản
    private static class EventData {
        String plateText, timestamp;
        EventData(String plateText, String timestamp) {
            this.plateText = plateText;
            this.timestamp = timestamp;
        }
    }
}