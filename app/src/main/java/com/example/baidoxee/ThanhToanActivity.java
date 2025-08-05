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
    private TextView tvBienSoXe, tvThoiGianVao, tvThoiGianRa, tvGiaVe;
    private Button btnInHoaDon;
    private TextView tvThongBao;
    private RadioGroup rgPaymentMethod;
    private RadioButton rbTienMat, rbChuyenKhoan;
    private LinearLayout invoiceLayout;

    // Helper classes
    private PaymentHelper paymentHelper;
    private InvoiceHelper invoiceHelper;

    // Dữ liệu hóa đơn - CẬP NHẬT: sử dụng event IDs thay vì vehicle IDs
    private String bienSoXe;
    private String thoiGianVao;
    private String thoiGianRa;
    private long giaVe;
    private String activityId;
    private String hinhThucThanhToan = "tien_mat"; // Mặc định là tiền mặt
    private String eventEnterId; // ID của event vào từ events collection
    private String eventExitId;  // ID của event ra từ events collection
    private String vehicleType = "CAR_UNDER_9"; // Loại xe

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.thanhtoan);

        // Khởi tạo helper classes
        initHelpers();

        // Khởi tạo views
        initViews();

        // Kiểm tra xem có dữ liệu từ intent không (trường hợp xe ra)
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("bien_so_xe")) {
            bienSoXe = intent.getStringExtra("bien_so_xe");
            Log.d(TAG, "Received license plate: " + bienSoXe);
            layThongTinThanhToanTheoBienSo(bienSoXe);
        } else {
            // Trường hợp click vào tab Thanh toán - kiểm tra xe cần thanh toán
            kiemTraXeCanThanhToan();
        }

        // Setup bottom navigation
        setupBottomNavigation();

        // Setup button in hóa đơn
        setupPrintButton();

        // Setup payment method selection
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

        // Disable button initially
        btnInHoaDon.setEnabled(false);

        // Ẩn thông tin hóa đơn ban đầu
        hideInvoiceInfo();
    }

    private void hideInvoiceInfo() {
        if (invoiceLayout != null) {
            invoiceLayout.setVisibility(View.GONE);
        }
        if (btnInHoaDon != null) {
            btnInHoaDon.setVisibility(View.GONE);
        }
        if (tvThongBao != null) {
            tvThongBao.setVisibility(View.VISIBLE);
            tvThongBao.setText("Đang kiểm tra thông tin xe...");
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
        activityId = null;
        bienSoXe = null;
        thoiGianVao = null;
        thoiGianRa = null;
        giaVe = 0;
        hinhThucThanhToan = "tien_mat";
        eventEnterId = null;  // Reset event enter ID
        eventExitId = null;   // Reset event exit ID
        vehicleType = "CAR_UNDER_9";

        // Reset radio button về mặc định
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
                    // CẬP NHẬT: truyền event IDs thay vì vehicle IDs
                    paymentHelper.processPaymentAndPrint(
                            activityId, bienSoXe, thoiGianVao, thoiGianRa,
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
                    // CẬP NHẬT: truyền event enter ID thay vì vehicle ID
                    paymentHelper.updatePaymentMethod(activityId, eventEnterId, vehicleType, "tien_mat");
                } else if (checkedId == R.id.rbChuyenKhoan) {
                    hinhThucThanhToan = "chuyen_khoan";
                    Log.d(TAG, "Payment method selected: Chuyển khoản");
                    paymentHelper.updatePaymentMethod(activityId, eventEnterId, vehicleType, "chuyen_khoan");
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
        Log.d(TAG, "Checking for vehicles needing payment from events collection");

        showLoadingState("Đang tìm kiếm xe cần thanh toán...");

        // Sử dụng API endpoint mới
        ApiHelper.getVehiclesNeedPayment(new ApiHelper.OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                Log.d(TAG, "Raw JSON received: " + jsonData);
                try {
                    JSONObject response = new JSONObject(jsonData);

                    if (response.has("success") && response.getBoolean("success")) {
                        JSONArray dataArray = response.getJSONArray("data");

                        if (dataArray.length() > 0) {
                            // Lấy xe đầu tiên cần thanh toán
                            JSONObject vehicleData = dataArray.getJSONObject(0);
                            xuLyDuLieuThanhToan(vehicleData);
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    showNoVehicleMessage();
                                }
                            });
                        }
                    } else {
                        String message = response.optString("message", "Không có xe cần thanh toán");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showErrorMessage(message);
                            }
                        });
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "JSON parsing error", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showErrorMessage("Lỗi xử lý dữ liệu từ server");
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
                        showErrorMessage("Lỗi kết nối: " + errorMessage);
                    }
                });
            }
        });
    }

    private void layThongTinThanhToanTheoBienSo(String bienSo) {
        Log.d(TAG, "Getting vehicle info for plate from events: " + bienSo);

        showLoadingState("Đang tải thông tin xe " + bienSo + "...");

        ApiHelper.getVehicleByLicensePlate(bienSo, new ApiHelper.OnDataReceivedListener() {
            @Override
            public void onDataReceived(String jsonData) {
                try {
                    Log.d(TAG, "Received data: " + jsonData);
                    JSONObject response = new JSONObject(jsonData);

                    if (response.has("success") && response.getBoolean("success")) {
                        JSONObject data = response.getJSONObject("data");
                        xuLyDuLieuThanhToan(data);
                    } else if (response.has("_id")) {
                        xuLyDuLieuThanhToan(response);
                    } else {
                        String message = response.optString("message", "Không tìm thấy thông tin xe");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showErrorMessage(message);
                            }
                        });
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "JSON parsing error", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showErrorMessage("Lỗi xử lý dữ liệu từ server");
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
                        showErrorMessage("Lỗi kết nối: " + errorMessage);
                    }
                });
            }
        });
    }

    private void xuLyDuLieuThanhToan(JSONObject data) throws JSONException {
        // Sử dụng PaymentHelper để xử lý dữ liệu
        PaymentHelper.PaymentData paymentData = paymentHelper.processPaymentData(data);

        // Cập nhật dữ liệu activity - CẬP NHẬT: sử dụng event IDs
        activityId = paymentData.activityId;
        bienSoXe = paymentData.bienSoXe;
        thoiGianVao = paymentData.thoiGianVao;
        thoiGianRa = paymentData.thoiGianRa;
        giaVe = paymentData.giaVe;
        eventEnterId = paymentData.eventEnterId;  // Event enter ID từ events collection
        eventExitId = paymentData.eventExitId;    // Event exit ID từ events collection
        vehicleType = paymentData.vehicleType;
        hinhThucThanhToan = paymentData.hinhThucThanhToan;

        Log.d(TAG, "Processing payment data from events collection:");
        Log.d(TAG, "Activity ID: " + activityId);
        Log.d(TAG, "Event Enter ID: " + eventEnterId);
        Log.d(TAG, "Event Exit ID: " + eventExitId);
        Log.d(TAG, "Vehicle Type: " + vehicleType);
        Log.d(TAG, "License plate: " + bienSoXe);
        Log.d(TAG, "Time in: " + thoiGianVao);
        Log.d(TAG, "Time out: " + thoiGianRa);
        Log.d(TAG, "Fee: " + giaVe);
        Log.d(TAG, "Payment method: " + hinhThucThanhToan);

        // Hiển thị thông tin lên UI
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hienThiThongTinHoaDon();
                showInvoiceInfo();

                // Set payment method radio button based on saved data
                if (hinhThucThanhToan.equals("chuyen_khoan")) {
                    rbChuyenKhoan.setChecked(true);
                } else {
                    rbTienMat.setChecked(true);
                }
            }
        });

        // Cập nhật trạng thái xe ra trong database nếu cần
        if (paymentData.needsCheckoutUpdate) {
            // CẬP NHẬT: truyền event enter ID thay vì vehicle ID
            paymentHelper.updateVehicleCheckout(activityId, thoiGianRa, giaVe, eventEnterId, vehicleType);
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
            tvThongBao.setText(message);
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