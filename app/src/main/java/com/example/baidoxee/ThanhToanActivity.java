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

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

    private String bienSoXe, thoiGianVao, thoiGianRa, activityId, vehicleId, vehicleType = "CAR_UNDER_9";
    private long giaVe;
    private String hinhThucThanhToan = "tien_mat";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.thanhtoan);

        initHelpers();
        initViews();

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("bien_so_xe")) {
            bienSoXe = intent.getStringExtra("bien_so_xe");
            layThongTinThanhToanTheoBienSo(bienSoXe);
        } else {
            kiemTraXeCanThanhToan();
        }

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

        // Mặc định chọn tiền mặt
        rbTienMat.setChecked(true);
    }

    private void hideInvoiceInfo() {
        if (invoiceLayout != null) invoiceLayout.setVisibility(View.GONE);
        if (btnInHoaDon != null) btnInHoaDon.setVisibility(View.GONE);
        if (tvThongBao != null) {
            tvThongBao.setVisibility(View.VISIBLE);
            tvThongBao.setText("Đang kiểm tra thông tin xe...");
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
            tvThongBao.setText("❌ Không có xe nào cần thanh toán");
            tvThongBao.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            tvThongBao.setTextSize(16);
            tvThongBao.setGravity(Gravity.CENTER);
        }
    }

    public void resetActivityData() {
        activityId = null;
        bienSoXe = null;
        thoiGianVao = null;
        thoiGianRa = null;
        giaVe = 0;
        hinhThucThanhToan = "tien_mat";
        vehicleId = null;
        vehicleType = "CAR_UNDER_9";

        if (rbTienMat != null && rbChuyenKhoan != null) {
            rbTienMat.setChecked(true);
            rbChuyenKhoan.setChecked(false);
        }
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
                } else if (id == R.id.nav_parking) {
                    startActivity(new Intent(ThanhToanActivity.this, XeVaoActivity.class));
                } else if (id == R.id.nav_exit) {
                    startActivity(new Intent(ThanhToanActivity.this, XeraActivity.class));
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
                    paymentHelper.processPaymentAndPrint(
                            activityId, bienSoXe, thoiGianVao, thoiGianRa,
                            giaVe, hinhThucThanhToan, vehicleId, vehicleType
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
                    paymentHelper.updatePaymentMethod(activityId, vehicleId, vehicleType, "tien_mat");
                } else if (checkedId == R.id.rbChuyenKhoan) {
                    hinhThucThanhToan = "chuyen_khoan";
                    paymentHelper.updatePaymentMethod(activityId, vehicleId, vehicleType, "chuyen_khoan");
                }
            }
        });
    }

    private boolean kiemTraThongTinHopLe() {
        if (activityId == null || activityId.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy thông tin hoạt động", Toast.LENGTH_SHORT).show();
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

    private void kiemTraXeCanThanhToan() {
        showLoadingState("Đang tìm kiếm xe cần thanh toán...");

        ApiHelper.getVehiclesNeedPayment(new ApiHelper.OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                try {
                    JSONObject response = new JSONObject(jsonData);
                    if (response.has("success") && response.getBoolean("success")) {
                        JSONArray dataArray = response.getJSONArray("data");
                        if (dataArray.length() > 0) {
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
                    runOnUiThread(() -> showErrorMessage("Lỗi xử lý dữ liệu từ server"));
                }
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> showErrorMessage("Lỗi kết nối: " + errorMessage));
            }
        });
    }

    private void layThongTinThanhToanTheoBienSo(String bienSo) {
        showLoadingState("Đang tải thông tin xe " + bienSo + "...");

        ApiHelper.getVehicleByLicensePlate(bienSo, new ApiHelper.OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                try {
                    JSONObject response = new JSONObject(jsonData);
                    if (response.has("success") && response.getBoolean("success")) {
                        JSONObject data = response.getJSONObject("data");
                        xuLyDuLieuThanhToan(data);
                    } else if (response.has("_id")) {
                        xuLyDuLieuThanhToan(response);
                    } else {
                        String message = response.optString("message", "Không tìm thấy thông tin xe");
                        runOnUiThread(() -> showErrorMessage(message));
                    }
                } catch (JSONException e) {
                    runOnUiThread(() -> showErrorMessage("Lỗi xử lý dữ liệu từ server"));
                }
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> showErrorMessage("Lỗi kết nối: " + errorMessage));
            }
        });
    }

    private void xuLyDuLieuThanhToan(JSONObject data) throws JSONException {
        String vehicleId = null;
        if (data.has("vehicle_id") && !data.isNull("vehicle_id")) {
            vehicleId = data.getString("vehicle_id");
        }

        boolean hasPlateNumber = data.has("plateNumber") && !data.isNull("plateNumber") &&
                !data.getString("plateNumber").trim().isEmpty();

        if (vehicleId != null && !hasPlateNumber) {
            fetchVehicleInfoThenProcess(data, vehicleId);
            return;
        }

        if (hasPlateNumber) {
            processPaymentDataDirectly(data);
        } else {
            runOnUiThread(() -> showErrorMessage("Dữ liệu xe không đầy đủ - không tìm thấy biển số"));
        }
    }

    private void fetchVehicleInfoThenProcess(JSONObject parkingLogData, String vehicleId) {
        showLoadingState("Đang tải thông tin biển số xe...");

        String vehicleApiUrl = "http://192.168.1.191:3000/api/vehicles/" + vehicleId;

        ApiHelper.makeGetRequest(vehicleApiUrl, new ApiHelper.OnDataReceivedListener() {
            @Override
            public void onDataReceived(String vehicleJsonData) {
                try {
                    JSONObject vehicleResponse = new JSONObject(vehicleJsonData);
                    JSONObject vehicleData = vehicleResponse.has("success") && vehicleResponse.getBoolean("success")
                            ? vehicleResponse.getJSONObject("data") : vehicleResponse;

                    if (vehicleData.has("plateNumber")) {
                        parkingLogData.put("plateNumber", vehicleData.getString("plateNumber"));
                    }
                    if (vehicleData.has("vehicleType")) {
                        parkingLogData.put("vehicleType", vehicleData.getString("vehicleType"));
                    }

                    processPaymentDataDirectly(parkingLogData);

                } catch (JSONException e) {
                    runOnUiThread(() -> showErrorMessage("Lỗi xử lý thông tin xe: " + e.getMessage()));
                }
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> showErrorMessage("Không thể tải thông tin biển số xe: " + errorMessage));
            }
        });
    }

    private void processPaymentDataDirectly(JSONObject data) {
        try {
            PaymentHelper.PaymentData paymentData = paymentHelper.processPaymentData(data);

            activityId = paymentData.activityId;
            bienSoXe = paymentData.bienSoXe;
            thoiGianVao = paymentData.thoiGianVao;
            thoiGianRa = paymentData.thoiGianRa;
            giaVe = paymentData.giaVe;
            vehicleId = paymentData.vehicleId;
            vehicleType = paymentData.vehicleType;
            hinhThucThanhToan = paymentData.hinhThucThanhToan;

            if (bienSoXe == null || bienSoXe.isEmpty()) {
                runOnUiThread(() -> showErrorMessage("Không tìm thấy biển số xe sau khi xử lý dữ liệu"));
                return;
            }

            runOnUiThread(() -> {
                hienThiThongTinHoaDon();
                showInvoiceInfo();

                // Luôn mặc định chọn tiền mặt, bỏ qua dữ liệu từ database
                rbTienMat.setChecked(true);
                rbChuyenKhoan.setChecked(false);
                hinhThucThanhToan = "tien_mat";
            });

            if (paymentData.needsCheckoutUpdate) {
                paymentHelper.updateVehicleCheckout(activityId, thoiGianRa, giaVe, vehicleId, vehicleType);
            }

        } catch (JSONException e) {
            runOnUiThread(() -> showErrorMessage("Lỗi xử lý dữ liệu thanh toán: " + e.getMessage()));
        }
    }

    private void hienThiThongTinHoaDon() {
        invoiceHelper.displayInvoiceInfo(
                tvBienSoXe, tvThoiGianVao, tvThoiGianRa, tvGiaVe,
                bienSoXe, vehicleType, thoiGianVao, thoiGianRa, giaVe
        );
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
            tvThongBao.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    public void resetButton() {
        if (btnInHoaDon != null) {
            btnInHoaDon.setEnabled(true);
            btnInHoaDon.setText("💰 Thanh toán & In hóa đơn");
        }
    }

    public void setButtonProcessing() {
        if (btnInHoaDon != null) {
            btnInHoaDon.setEnabled(false);
            btnInHoaDon.setText("Đang xử lý...");
        }
    }

    public void showSuccessDialog(String bienSoXe, String thoiGianVao, String thoiGianRa,
                                  long giaVe, String paymentMethodText) {
        invoiceHelper.showSuccessDialog(bienSoXe, thoiGianVao, thoiGianRa, giaVe, paymentMethodText);
    }
}