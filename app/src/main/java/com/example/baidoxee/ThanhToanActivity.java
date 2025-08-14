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
    private TextView tvBienSoXe, tvThoiGianVao, tvThoiGianRa, tvGiaVe, tvThongBao;
    private Button btnInHoaDon;
    private RadioGroup rgPaymentMethod;
    private RadioButton rbTienMat, rbChuyenKhoan;
    private LinearLayout invoiceLayout;

    private PaymentHelper paymentHelper;
    private InvoiceHelper invoiceHelper;

    // Dữ liệu từ events
    private String bienSoXe, thoiGianVao, thoiGianRa, activityId, hinhThucThanhToan = "tien_mat";
    private String eventEnterId, eventExitId, vehicleType = "CAR_UNDER_9", parkingLogId;
    private String cameraId, spotName, locationName, enterEventTimestamp, exitEventTimestamp;
    private long giaVe;
    private double plateConfidence;
    private boolean hasParkingLog = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.thanhtoan);

        initHelpers();
        initViews();
        handleIntent();
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

        btnInHoaDon.setEnabled(false);
        hideInvoiceInfo();
    }

    private void handleIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra("bien_so_xe")) {
                bienSoXe = intent.getStringExtra("bien_so_xe");
                layThongTinThanhToanTheoBienSoFromEvents(bienSoXe);
            } else if (intent.hasExtra("event_enter_id")) {
                layThongTinThanhToanTheoEventId(intent.getStringExtra("event_enter_id"));
            } else {
                kiemTraXeCanThanhToanFromEvents();
            }
        } else {
            kiemTraXeCanThanhToanFromEvents();
        }
    }

    private void hideInvoiceInfo() {
        if (invoiceLayout != null) invoiceLayout.setVisibility(View.GONE);
        if (btnInHoaDon != null) btnInHoaDon.setVisibility(View.GONE);
        if (tvThongBao != null) {
            tvThongBao.setVisibility(View.VISIBLE);
            tvThongBao.setText("🔄 Đang kiểm tra thông tin xe từ Events Collection...");
            tvThongBao.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        }
    }

    private void showInvoiceInfo() {
        if (invoiceLayout != null) invoiceLayout.setVisibility(View.VISIBLE);
        if (btnInHoaDon != null) {
            btnInHoaDon.setVisibility(View.VISIBLE);
            btnInHoaDon.setEnabled(true);
        }
        if (tvThongBao != null) tvThongBao.setVisibility(View.GONE);
    }

    private void showNoVehicleMessage() {
        hideInvoiceInfo();
        if (tvThongBao != null) {
            tvThongBao.setVisibility(View.VISIBLE);
            tvThongBao.setText("❌ Không có xe nào cần thanh toán\n📊 Dữ liệu từ Events Collection");
            tvThongBao.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        }
    }

    public void resetActivityData() {
        activityId = bienSoXe = thoiGianVao = thoiGianRa = eventEnterId = eventExitId = null;
        parkingLogId = cameraId = spotName = locationName = enterEventTimestamp = exitEventTimestamp = null;
        giaVe = (long) (plateConfidence = 0);
        vehicleType = "CAR_UNDER_9";
        hinhThucThanhToan = "tien_mat";
        hasParkingLog = false;

        if (rbTienMat != null) rbTienMat.setChecked(true);
        if (rbChuyenKhoan != null) rbChuyenKhoan.setChecked(false);
        showNoVehicleMessage();
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setSelectedItemId(R.id.nav_payment);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, TrangChuActivity.class));
                return true;
            } else if (id == R.id.nav_parking) {
                startActivity(new Intent(this, XeVaoActivity.class));
                return true;
            } else if (id == R.id.nav_exit) {
                startActivity(new Intent(this, XeraActivity.class));
                return true;
            } else if (id == R.id.nav_payment) {
                return true;
            }
            return false;
        });
    }

    private void setupPrintButton() {
        btnInHoaDon.setOnClickListener(v -> {
            if (kiemTraThongTinHopLe()) {
                paymentHelper.processPaymentAndPrintFromEvents(
                        activityId, bienSoXe, thoiGianVao, thoiGianRa,
                        giaVe, hinhThucThanhToan, eventEnterId, eventExitId,
                        vehicleType, parkingLogId, hasParkingLog);
            }
        });
    }

    private void setupPaymentMethodSelection() {
        rgPaymentMethod.setOnCheckedChangeListener((group, checkedId) -> {
            hinhThucThanhToan = (checkedId == R.id.rbTienMat) ? "tien_mat" : "chuyen_khoan";
            paymentHelper.updatePaymentMethodFromEvents(
                    activityId, eventEnterId, eventExitId, vehicleType,
                    parkingLogId, hinhThucThanhToan, hasParkingLog);
        });
    }

    private boolean kiemTraThongTinHopLe() {
        if (eventEnterId == null || eventEnterId.isEmpty()) {
            Toast.makeText(this, "❌ Không tìm thấy thông tin event", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (bienSoXe == null || bienSoXe.isEmpty()) {
            Toast.makeText(this, "❌ Không có thông tin biển số xe", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (rgPaymentMethod.getCheckedRadioButtonId() == -1) {
            Toast.makeText(this, "⚠️ Vui lòng chọn hình thức thanh toán", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void kiemTraXeCanThanhToanFromEvents() {
        showLoadingState("🔍 Đang tìm kiếm xe cần thanh toán từ Events Collection...");

        ApiHelper.getVehiclesNeedPayment(new ApiHelper.OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                try {
                    JSONObject response = new JSONObject(jsonData);
                    if (response.has("success") && response.getBoolean("success")) {
                        JSONArray dataArray = response.getJSONArray("data");
                        if (dataArray.length() > 0) {
                            xuLyDuLieuThanhToanFromEvents(dataArray.getJSONObject(0));
                        } else {
                            runOnUiThread(() -> showNoVehicleMessage());
                        }
                    } else {
                        runOnUiThread(() -> showErrorMessage("📊 " + response.optString("message", "Không có xe cần thanh toán")));
                    }
                } catch (JSONException e) {
                    runOnUiThread(() -> showErrorMessage("❌ Lỗi xử lý dữ liệu từ Events Collection"));
                }
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> showErrorMessage("🌐 Lỗi kết nối Events API: " + errorMessage));
            }
        });
    }

    private void layThongTinThanhToanTheoBienSoFromEvents(String bienSo) {
        showLoadingState("🔍 Đang tải thông tin xe " + bienSo + " từ Events Collection...");

        ApiHelper.getVehicleByLicensePlate(bienSo, new ApiHelper.OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                try {
                    JSONObject response = new JSONObject(jsonData);
                    JSONObject data = response.has("success") && response.getBoolean("success") ?
                            response.getJSONObject("data") : response;
                    xuLyDuLieuThanhToanFromEvents(data);
                } catch (JSONException e) {
                    runOnUiThread(() -> showErrorMessage("❌ Lỗi xử lý dữ liệu"));
                }
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> showErrorMessage("🌐 Lỗi kết nối: " + errorMessage));
            }
        });
    }

    private void layThongTinThanhToanTheoEventId(String enterEventId) {
        showLoadingState("🔍 Đang tải thông tin từ Event ID: " + enterEventId);
        layThongTinThanhToanTheoBienSoFromEvents(bienSoXe);
    }

    private void xuLyDuLieuThanhToanFromEvents(JSONObject data) throws JSONException {
        PaymentHelper.EventsPaymentData paymentData = paymentHelper.processEventsPaymentData(data);

        // Cập nhật tất cả dữ liệu
        activityId = paymentData.activityId;
        bienSoXe = paymentData.bienSoXe;
        thoiGianVao = paymentData.thoiGianVao;
        thoiGianRa = paymentData.thoiGianRa;
        giaVe = paymentData.giaVe;
        eventEnterId = paymentData.eventEnterId;
        eventExitId = paymentData.eventExitId;
        vehicleType = paymentData.vehicleType;
        hinhThucThanhToan = paymentData.hinhThucThanhToan;
        parkingLogId = paymentData.parkingLogId;
        hasParkingLog = paymentData.hasParkingLog;
        cameraId = paymentData.cameraId;
        spotName = paymentData.spotName;
        locationName = paymentData.locationName;
        plateConfidence = paymentData.plateConfidence;
        enterEventTimestamp = paymentData.enterEventTimestamp;
        exitEventTimestamp = paymentData.exitEventTimestamp;

        runOnUiThread(() -> {
            hienThiThongTinHoaDonFromEvents();
            showInvoiceInfo();
            (hinhThucThanhToan.equals("chuyen_khoan") ? rbChuyenKhoan : rbTienMat).setChecked(true);
        });

        if (paymentData.needsUpdateParkingLog && eventEnterId != null) {
            paymentHelper.updateOrCreateParkingLogFromEvents(
                    eventEnterId, eventExitId, thoiGianVao, thoiGianRa,
                    giaVe, vehicleType, parkingLogId, hasParkingLog);
        }
    }

    private void hienThiThongTinHoaDonFromEvents() {
        invoiceHelper.displayInvoiceInfoFromEvents(
                tvBienSoXe, tvThoiGianVao, tvThoiGianRa, tvGiaVe,
                bienSoXe, vehicleType, thoiGianVao, thoiGianRa, giaVe,
                eventEnterId, eventExitId, hasParkingLog);

        if (btnInHoaDon != null) btnInHoaDon.setText("💰 Thanh toán & In hóa đơn");
    }

    private void showLoadingState(String message) {
        if (tvThongBao != null) {
            tvThongBao.setVisibility(View.VISIBLE);
            tvThongBao.setText(message);
            tvThongBao.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        }
        hideInvoiceInfo();
    }

    private void showErrorMessage(String message) {
        hideInvoiceInfo();
        if (tvThongBao != null) {
            tvThongBao.setVisibility(View.VISIBLE);
            tvThongBao.setText(message);
            tvThongBao.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    public void resetButton() {
        if (btnInHoaDon != null) {
            btnInHoaDon.setEnabled(true);
            btnInHoaDon.setText("💰 Thanh toán & In hóa đơn (Events Data)");
        }
    }

    public void setButtonProcessing() {
        if (btnInHoaDon != null) {
            btnInHoaDon.setEnabled(false);
            btnInHoaDon.setText("⏳ Đang xử lý thanh toán từ Events...");
        }
    }

    public void showSuccessDialogFromEvents(String bienSoXe, String thoiGianVao, String thoiGianRa,
                                            long giaVe, String paymentMethodText, String eventEnterId, String eventExitId) {
        invoiceHelper.showSuccessDialogFromEvents(bienSoXe, thoiGianVao, thoiGianRa,
                giaVe, paymentMethodText, eventEnterId, eventExitId);
        resetActivityDataAfterPayment();
    }

    public void showSuccessDialog(String bienSoXe, String thoiGianVao, String thoiGianRa,
                                  long giaVe, String paymentMethodText) {
        invoiceHelper.showSuccessDialog(bienSoXe, thoiGianVao, thoiGianRa, giaVe, paymentMethodText);
        resetActivityDataAfterPayment();
    }

    private void resetActivityDataAfterPayment() {
        new android.os.Handler().postDelayed(() -> {
            resetActivityData();
            kiemTraXeCanThanhToanFromEvents();
        }, 3000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (eventEnterId == null || eventEnterId.isEmpty() || !checkEventsDataIntegrity()) {
            refreshEventsData();
        }
    }

    private boolean checkEventsDataIntegrity() {
        return (eventEnterId != null && !eventEnterId.isEmpty() &&
                enterEventTimestamp != null && !enterEventTimestamp.isEmpty() &&
                bienSoXe != null && !bienSoXe.isEmpty() &&
                thoiGianVao != null && !thoiGianVao.isEmpty());
    }

    public void refreshEventsData() {
        resetActivityData();
        kiemTraXeCanThanhToanFromEvents();
    }

    public String getCurrentEventsStatus() {
        if (eventEnterId == null || eventEnterId.isEmpty()) return "NO_EVENTS_DATA";
        if (eventExitId == null || eventExitId.isEmpty()) return "VEHICLE_IN_PROGRESS";
        if (hasParkingLog) return "HAS_PARKING_LOG";
        return "READY_FOR_PAYMENT";
    }
}