package com.example.baidoxee;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ThanhToanActivity extends BaseActivity {

    private static final String TAG = "ThanhToanActivity";

    private BottomNavigationView bottomNavigationView;
    private TextView tvBienSoXe, tvThoiGianVao, tvThoiGianRa, tvGiaVe;
    private Button btnInHoaDon;
    private TextView tvThongBao;
    private RadioGroup rgPaymentMethod;
    private RadioButton rbTienMat, rbChuyenKhoan;
    private LinearLayout invoiceLayout;

    // Helper classes
    private PaymentHelper paymentHelper;
    private InvoiceHelper invoiceHelper;

    // Dữ liệu thanh toán từ parkinglogs và events
    private String parkingLogId;           // ID từ parkinglogs collection
    private String bienSoXe;              // plate_text từ events collection
    private String thoiGianVao;           // timeIn từ parkinglogs hoặc timestamp từ events
    private String thoiGianRa;            // timeOut từ parkinglogs hoặc timestamp từ events
    private long giaVe;                   // fee từ parkinglogs
    private String eventEnterId;          // ID của enter event từ events collection
    private String eventExitId;           // ID của exit event từ events collection
    private String vehicleId;             // vehicle_id liên kết giữa events và parkinglogs
    private String vehicleType = "CAR_UNDER_9"; // Loại xe
    private String hinhThucThanhToan = "tien_mat"; // Hình thức thanh toán mặc định
    private boolean needsPaymentDisplay = false;   // Flag để hiển thị hóa đơn

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.thanhtoan);

        // Khởi tạo helper classes
        initHelpers();

        // Khởi tạo views
        initViews();

        // Kiểm tra dữ liệu từ intent (trường hợp xe ra)
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("bien_so_xe")) {
            bienSoXe = intent.getStringExtra("bien_so_xe");
            Log.d(TAG, "Received license plate from intent: " + bienSoXe);
            layThongTinThanhToanTheoBienSo(bienSoXe);
        } else {
            // Trường hợp click vào tab Thanh toán - tìm xe cần thanh toán
            kiemTraXeCanThanhToan();
        }

        // Setup components
        setupBottomNavigation();
        setupPrintButton();
        setupPaymentMethodSelection();
    }

    private void initHelpers() {
        paymentHelper = new PaymentHelper(this);
        invoiceHelper = new InvoiceHelper(this);
    }

    private void initViews() {
        bottomNavigationView = findViewById(R.id.bottomNavigation);
        tvBienSoXe = findViewById(R.id.tvBienSoXe);
        tvThoiGianVao = findViewById(R.id.tvThoiGianVao);
        tvThoiGianRa = findViewById(R.id.tvThoiGianRa);
        tvGiaVe = findViewById(R.id.tvGiaVe);
        btnInHoaDon = findViewById(R.id.printButton);
        rgPaymentMethod = findViewById(R.id.rgPaymentMethod);
        rbTienMat = findViewById(R.id.rbTienMat);
        rbChuyenKhoan = findViewById(R.id.rbChuyenKhoan);
        tvThongBao = findViewById(R.id.tvThongBao);
        invoiceLayout = findViewById(R.id.invoiceLayout);

        // Ẩn thông tin hóa đơn ban đầu
        hideInvoiceInfo();
    }

    private void hideInvoiceInfo() {
        if (invoiceLayout != null) {
            invoiceLayout.setVisibility(View.GONE);
        }
        if (btnInHoaDon != null) {
            btnInHoaDon.setVisibility(View.GONE);
            btnInHoaDon.setEnabled(false);
        }
        if (tvThongBao != null) {
            tvThongBao.setVisibility(View.VISIBLE);
            tvThongBao.setText("Đang kiểm tra thông tin xe từ hệ thống...");
        }
    }

    private void showInvoiceInfo() {
        if (invoiceLayout != null) {
            invoiceLayout.setVisibility(View.VISIBLE);
        }
        if (btnInHoaDon != null) {
            btnInHoaDon.setVisibility(View.VISIBLE);
            btnInHoaDon.setEnabled(true);
        }
        if (tvThongBao != null) {
            tvThongBao.setVisibility(View.GONE);
        }
    }

    private void showNoVehicleMessage() {
        hideInvoiceInfo();
        if (tvThongBao != null) {
            tvThongBao.setVisibility(View.VISIBLE);
            tvThongBao.setText("❌ Không có xe nào cần thanh toán");
            tvThongBao.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            tvThongBao.setTextSize(16);
            tvThongBao.setGravity(Gravity.CENTER);
        }
    }

    public void resetActivityData() {
        parkingLogId = null;
        bienSoXe = null;
        thoiGianVao = null;
        thoiGianRa = null;
        giaVe = 0;
        eventEnterId = null;
        eventExitId = null;
        vehicleId = null;
        vehicleType = "CAR_UNDER_9";
        hinhThucThanhToan = "tien_mat";
        needsPaymentDisplay = false;

        // Reset radio buttons
        if (rbTienMat != null && rbChuyenKhoan != null) {
            rbTienMat.setChecked(true);
            rbChuyenKhoan.setChecked(false);
        }

        // Hiển thị thông báo không có xe
        showNoVehicleMessage();
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setSelectedItemId(R.id.nav_payment);
        bottomNavigationView.setOnItemSelectedListener(new BottomNavigationView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();

                if (id == R.id.nav_home) {
                    startActivity(new Intent(ThanhToanActivity.this, TrangChuActivity.class));
                    return true;
                } else if (id == R.id.nav_parking) {
                    startActivity(new Intent(ThanhToanActivity.this, XeVaoActivity.class));
                    return true;
                } else if (id == R.id.nav_exit) {
                    startActivity(new Intent(ThanhToanActivity.this, XeraActivity.class));
                    return true;
                } else if (id == R.id.nav_payment) {
                    return true;
                }
                return false;
            }
        });
    }

    private void setupPrintButton() {
        btnInHoaDon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (kiemTraThongTinHopLe()) {
                    // Xử lý thanh toán và in hóa đơn
                    paymentHelper.processPaymentAndPrint(
                            parkingLogId, bienSoXe, thoiGianVao, thoiGianRa,
                            giaVe, hinhThucThanhToan, eventEnterId, vehicleType
                    );
                }
            }
        });
    }

    private void setupPaymentMethodSelection() {
        rgPaymentMethod.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rbTienMat) {
                    hinhThucThanhToan = "tien_mat";
                    Log.d(TAG, "Payment method selected: Tiền mặt");
                } else if (checkedId == R.id.rbChuyenKhoan) {
                    hinhThucThanhToan = "chuyen_khoan";
                    Log.d(TAG, "Payment method selected: Chuyển khoản");
                }

                // Cập nhật payment method trong parkinglogs
                if (parkingLogId != null && !parkingLogId.isEmpty()) {
                    paymentHelper.updatePaymentMethod(parkingLogId, eventEnterId, vehicleType, hinhThucThanhToan);
                }
            }
        });
    }

    private boolean kiemTraThongTinHopLe() {
        if (parkingLogId == null || parkingLogId.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy thông tin parking log", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (bienSoXe == null || bienSoXe.isEmpty()) {
            Toast.makeText(this, "Không có thông tin biển số xe", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (rgPaymentMethod.getCheckedRadioButtonId() == -1) {
            Toast.makeText(this, "Vui lòng chọn hình thức thanh toán", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    /**
     * Kiểm tra xe cần thanh toán từ parkinglogs với status = COMPLETED
     */
    private void kiemTraXeCanThanhToan() {
        Log.d(TAG, "Checking for vehicles needing payment from parkinglogs (status = COMPLETED)");

        showLoadingState("Đang tìm kiếm xe cần thanh toán...");

        ApiHelper.getVehiclesNeedPaymentFromParkinglogs(new ApiHelper.OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                Log.d(TAG, "Vehicles need payment response: " + jsonData);

                try {
                    JSONObject response = new JSONObject(jsonData);

                    if (response.has("success") && response.getBoolean("success")) {
                        JSONArray dataArray = response.getJSONArray("data");

                        if (dataArray.length() > 0) {
                            // Lấy xe đầu tiên cần thanh toán
                            JSONObject vehicleData = dataArray.getJSONObject(0);
                            xuLyDuLieuThanhToan(vehicleData);
                        } else {
                            runOnUiThread(() -> showNoVehicleMessage());
                        }
                    } else {
                        String message = response.optString("message", "Không có xe cần thanh toán");
                        runOnUiThread(() -> showErrorMessage(message));
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "JSON parsing error for vehicles need payment", e);
                    runOnUiThread(() -> showErrorMessage("Lỗi xử lý dữ liệu từ server"));
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "API Error getting vehicles need payment: " + errorMessage);
                runOnUiThread(() -> showErrorMessage("Lỗi kết nối: " + errorMessage));
            }
        });
    }

    /**
     * Lấy thông tin thanh toán theo biển số xe
     */
    private void layThongTinThanhToanTheoBienSo(String bienSo) {
        Log.d(TAG, "Getting payment info for plate: " + bienSo);

        showLoadingState("Đang tải thông tin xe " + bienSo + "...");

        // Sử dụng method từ PaymentHelper để lấy thông tin đầy đủ
        ApiHelper.getPaymentInfoByPlate(bienSo, new ApiHelper.OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                Log.d(TAG, "Payment info received: " + jsonData);

                try {
                    JSONObject paymentInfo = new JSONObject(jsonData);
                    xuLyDuLieuThanhToanFromPaymentInfo(paymentInfo);
                } catch (JSONException e) {
                    Log.e(TAG, "JSON parsing error for payment info", e);
                    runOnUiThread(() -> showErrorMessage("Lỗi xử lý thông tin thanh toán"));
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "API Error getting payment info: " + errorMessage);

                // Thử tìm từ parkinglogs trực tiếp
                layThongTinFromParkinglogs(bienSo, errorMessage);
            }
        });
    }

    /**
     * Fallback: tìm thông tin từ parkinglogs trực tiếp
     */
    private void layThongTinFromParkinglogs(String bienSo, String originalError) {
        Log.d(TAG, "Trying to get info from parkinglogs directly for plate: " + bienSo);

        ApiHelper.getVehicleByLicensePlateFromParkinglogs(bienSo, new ApiHelper.OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                try {
                    JSONObject response = new JSONObject(jsonData);

                    if (response.has("success") && response.getBoolean("success")) {
                        JSONObject data = response.getJSONObject("data");
                        xuLyDuLieuThanhToan(data);
                    } else if (response.has("_id")) {
                        // Direct parking log data
                        xuLyDuLieuThanhToan(response);
                    } else {
                        String message = response.optString("message", "Không tìm thấy thông tin xe");
                        runOnUiThread(() -> showErrorMessage(message));
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "JSON parsing error from parkinglogs fallback", e);
                    runOnUiThread(() -> showErrorMessage("Lỗi xử lý dữ liệu từ server"));
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Parkinglogs fallback also failed: " + errorMessage);
                runOnUiThread(() -> showErrorMessage("Không tìm thấy thông tin xe: " + originalError));
            }
        });
    }

    /**
     * Xử lý dữ liệu thanh toán từ payment info (combined data)
     */
    private void xuLyDuLieuThanhToanFromPaymentInfo(JSONObject paymentInfo) throws JSONException {
        Log.d(TAG, "Processing payment info data");

        // Extract basic info
        bienSoXe = paymentInfo.optString("plate_text", "");
        vehicleId = paymentInfo.optString("vehicle_id", "");

        // Extract latest events
        if (paymentInfo.has("latest_enter")) {
            JSONObject enterEvent = paymentInfo.getJSONObject("latest_enter");
            eventEnterId = enterEvent.optString("_id", "");
            if (thoiGianVao == null || thoiGianVao.isEmpty()) {
                thoiGianVao = formatDateTime(enterEvent.optString("timestamp", ""));
            }
            if (bienSoXe.isEmpty()) {
                bienSoXe = enterEvent.optString("plate_text", "");
            }
        }

        if (paymentInfo.has("latest_exit")) {
            JSONObject exitEvent = paymentInfo.getJSONObject("latest_exit");
            eventExitId = exitEvent.optString("_id", "");
            if (thoiGianRa == null || thoiGianRa.isEmpty()) {
                thoiGianRa = formatDateTime(exitEvent.optString("timestamp", ""));
            }
        }

        // Extract parking logs info
        if (paymentInfo.has("parking_logs")) {
            JSONObject parkingLogsData = paymentInfo.getJSONObject("parking_logs");

            if (parkingLogsData.has("data") && parkingLogsData.get("data") instanceof JSONArray) {
                JSONArray parkingLogs = parkingLogsData.getJSONArray("data");
                if (parkingLogs.length() > 0) {
                    JSONObject parkingLog = parkingLogs.getJSONObject(0);
                    processParkingLogData(parkingLog);
                }
            }
        }

        // Set defaults and display
        setDefaultsAndDisplay();
    }

    /**
     * Xử lý dữ liệu thanh toán từ parkinglogs (có events populated)
     */
    private void xuLyDuLieuThanhToan(JSONObject data) throws JSONException {
        Log.d(TAG, "Processing parking log data with events");

        // Sử dụng PaymentHelper để xử lý dữ liệu
        PaymentHelper.PaymentData paymentData = paymentHelper.processPaymentDataFromParkinglogs(data);

        // Cập nhật dữ liệu activity
        parkingLogId = paymentData.parkingLogId;
        bienSoXe = paymentData.bienSoXe;
        thoiGianVao = paymentData.thoiGianVao;
        thoiGianRa = paymentData.thoiGianRa;
        giaVe = paymentData.giaVe;
        eventEnterId = paymentData.eventEnterId;
        eventExitId = paymentData.eventExitId;
        vehicleId = paymentData.vehicleId;
        vehicleType = paymentData.vehicleType;
        hinhThucThanhToan = paymentData.hinhThucThanhToan;
        needsPaymentDisplay = paymentData.needsPaymentDisplay;

        Log.d(TAG, "Payment data processed:");
        Log.d(TAG, "- Parking Log ID: " + parkingLogId);
        Log.d(TAG, "- Vehicle ID: " + vehicleId);
        Log.d(TAG, "- Biển số xe (from events): " + bienSoXe);
        Log.d(TAG, "- Event Enter ID: " + eventEnterId);
        Log.d(TAG, "- Event Exit ID: " + eventExitId);
        Log.d(TAG, "- Thời gian vào: " + thoiGianVao);
        Log.d(TAG, "- Thời gian ra: " + thoiGianRa);
        Log.d(TAG, "- Giá vé: " + giaVe);
        Log.d(TAG, "- Vehicle type: " + vehicleType);
        Log.d(TAG, "- Payment method: " + hinhThucThanhToan);
        Log.d(TAG, "- Needs payment display: " + needsPaymentDisplay);

        // Hiển thị thông tin lên UI
        runOnUiThread(() -> {
            hienThiThongTinHoaDon();
            showInvoiceInfo();

            // Set payment method radio button
            if (hinhThucThanhToan.equals("chuyen_khoan")) {
                rbChuyenKhoan.setChecked(true);
            } else {
                rbTienMat.setChecked(true);
            }

            // Hiển thị notification nếu cần thanh toán
            if (needsPaymentDisplay) {
                showPaymentNeededNotification();
            }
        });

        // Cập nhật checkout nếu cần
        if (paymentData.needsCheckoutUpdate) {
            paymentHelper.updateVehicleCheckoutInParkinglogs(parkingLogId, thoiGianRa, giaVe, eventExitId, vehicleType);
        }
    }

    /**
     * Process parking log data specifically
     */
    private void processParkingLogData(JSONObject parkingLog) throws JSONException {
        parkingLogId = parkingLog.optString("_id", "");

        if (giaVe == 0) {
            giaVe = parkingLog.optLong("fee", 0);
        }

        // Override time if available in parking log
        String timeIn = parkingLog.optString("timeIn", "");
        String timeOut = parkingLog.optString("timeOut", "");

        if (!timeIn.isEmpty()) {
            thoiGianVao = formatDateTime(timeIn);
        }
        if (!timeOut.isEmpty()) {
            thoiGianRa = formatDateTime(timeOut);
        }

        // Vehicle type and payment method
        vehicleType = parkingLog.optString("vehicleType", "CAR_UNDER_9");
        String paymentMethod = parkingLog.optString("paymentMethod", "CASH");
        hinhThucThanhToan = "BANK_TRANSFER".equals(paymentMethod) ? "chuyen_khoan" : "tien_mat";

        // Status check
        String status = parkingLog.optString("status", "IN_PROGRESS");
        needsPaymentDisplay = "COMPLETED".equals(status);
    }

    /**
     * Set defaults and display info
     */
    private void setDefaultsAndDisplay() {
        if (vehicleType.isEmpty()) {
            vehicleType = "CAR_UNDER_9";
        }

        if (hinhThucThanhToan.isEmpty()) {
            hinhThucThanhToan = "tien_mat";
        }

        // Calculate fee if not available and we have time info
        if (giaVe == 0 && !thoiGianVao.isEmpty() && !thoiGianRa.isEmpty()) {
            giaVe = PaymentHelper.calculateParkingFee(thoiGianVao, thoiGianRa, vehicleType);
        }

        runOnUiThread(() -> {
            hienThiThongTinHoaDon();
            showInvoiceInfo();

            // Set payment method radio button
            if (hinhThucThanhToan.equals("chuyen_khoan")) {
                rbChuyenKhoan.setChecked(true);
            } else {
                rbTienMat.setChecked(true);
            }

            if (needsPaymentDisplay) {
                showPaymentNeededNotification();
            }
        });
    }

    /**
     * Format datetime string
     */
    private String formatDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.isEmpty()) {
            return "";
        }

        try {
            // Try parsing ISO format
            java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault());
            java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault());

            java.util.Date date = inputFormat.parse(dateTimeString);
            return outputFormat.format(date);
        } catch (Exception e) {
            try {
                // Try alternative format
                java.text.SimpleDateFormat inputFormat2 = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault());
                java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault());

                java.util.Date date = inputFormat2.parse(dateTimeString);
                return outputFormat.format(date);
            } catch (Exception e2) {
                Log.e(TAG, "Error formatting datetime: " + dateTimeString, e2);
                return dateTimeString; // Return original if can't parse
            }
        }
    }

    /**
     * Hiển thị thông tin hóa đơn lên UI
     */
    private void hienThiThongTinHoaDon() {
        if (invoiceHelper != null) {
            invoiceHelper.displayInvoiceInfo(
                    tvBienSoXe, tvThoiGianVao, tvThoiGianRa, tvGiaVe,
                    bienSoXe, vehicleType, thoiGianVao, thoiGianRa, giaVe
            );
        } else {
            // Fallback display if invoiceHelper is null
            if (tvBienSoXe != null) tvBienSoXe.setText(bienSoXe);
            if (tvThoiGianVao != null) tvThoiGianVao.setText(thoiGianVao);
            if (tvThoiGianRa != null) tvThoiGianRa.setText(thoiGianRa);
            if (tvGiaVe != null) {
                java.text.NumberFormat formatter = java.text.NumberFormat.getInstance(java.util.Locale.getDefault());
                tvGiaVe.setText(formatter.format(giaVe) + " VNĐ");
            }
        }
    }

    /**
     * Hiển thị notification khi xe cần thanh toán
     */
    private void showPaymentNeededNotification() {
        Toast.makeText(this, "🔔 Xe " + bienSoXe + " cần thanh toán!", Toast.LENGTH_LONG).show();
    }

    private void showLoadingState(String message) {
        if (tvThongBao != null) {
            tvThongBao.setVisibility(View.VISIBLE);
            tvThongBao.setText(message);
        }
        hideInvoiceInfo();
    }

    private void showErrorMessage(String message) {
        hideInvoiceInfo();
        if (tvThongBao != null) {
            tvThongBao.setVisibility(View.VISIBLE);
            tvThongBao.setText("❌ " + message);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // Methods for PaymentHelper callbacks
    public void resetButton() {
        if (btnInHoaDon != null) {
            btnInHoaDon.setEnabled(true);
            btnInHoaDon.setText("💰 Thanh toán & In hóa đơn");
        }
    }

    public void setButtonProcessing() {
        if (btnInHoaDon != null) {
            btnInHoaDon.setEnabled(false);
            btnInHoaDon.setText("Đang xử lý thanh toán...");
        }
    }

    public void showSuccessDialog(String bienSoXe, String thoiGianVao, String thoiGianRa,
                                  long giaVe, String paymentMethodText) {
        if (invoiceHelper != null) {
            invoiceHelper.showSuccessDialog(bienSoXe, thoiGianVao, thoiGianRa, giaVe, paymentMethodText);
        } else {
            // Fallback success message
            String message = "Thanh toán thành công!\n" +
                    "Xe: " + bienSoXe + "\n" +
                    "Vào: " + thoiGianVao + "\n" +
                    "Ra: " + thoiGianRa + "\n" +
                    "Phí: " + java.text.NumberFormat.getInstance().format(giaVe) + " VNĐ\n" +
                    "Hình thức: " + paymentMethodText;

            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Handle real-time payment notifications
     */
    public void onPaymentNotificationReceived(JSONObject notificationData) {
        try {
            if (notificationData.has("data")) {
                JSONObject vehicleData = notificationData.getJSONObject("data");
                String plateNumber = "";
                String status = vehicleData.optString("status", "");

                // Extract plate number from events (if populated)
                if (vehicleData.has("event_enter_id") && vehicleData.get("event_enter_id") instanceof JSONObject) {
                    JSONObject enterEvent = vehicleData.getJSONObject("event_enter_id");
                    plateNumber = enterEvent.optString("plate_text", "");
                } else {
                    plateNumber = vehicleData.optString("plate_text", "");
                }

                Log.d(TAG, "Received payment notification for vehicle: " + plateNumber + " with status: " + status);

                if ("COMPLETED".equals(status)) {
                    String finalPlateNumber = plateNumber;
                    runOnUiThread(() -> {
                        Toast.makeText(this, "🔔 Xe " + finalPlateNumber + " vừa ra và cần thanh toán!", Toast.LENGTH_LONG).show();

                        // Auto load vehicle info if no vehicle is currently being processed
                        if (parkingLogId == null || parkingLogId.isEmpty()) {
                            try {
                                xuLyDuLieuThanhToan(vehicleData);
                            } catch (JSONException e) {
                                Log.e(TAG, "Error processing payment notification data", e);
                            }
                        }
                    });
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing payment notification", e);
        }
    }
}