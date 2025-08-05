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
import java.util.concurrent.TimeUnit;

public class ThanhToanActivity extends BaseActivity {

    private static final String TAG = "ThanhToanActivity";

    private BottomNavigationView bottomNavigationView;
    private TextView tvBienSoXe, tvThoiGianVao, tvThoiGianRa, tvGiaVe, tvLoaiXe;
    private Button btnInHoaDon;
    private TextView tvThongBao;
    private RadioGroup rgPaymentMethod;
    private RadioButton rbTienMat, rbChuyenKhoan;
    private LinearLayout invoiceLayout;

    // Dữ liệu hóa đơn
    private String bienSoXe;
    private String thoiGianVao;
    private String thoiGianRa;
    private long giaVe;
    private String activityId;
    private String hinhThucThanhToan = "tien_mat"; // Mặc định là tiền mặt
    private String vehicleId; // ID của xe trong collection vehicles
    private String vehicleType = "CAR_UNDER_9"; // Loại xe

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.thanhtoan);

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

    private void resetActivityData() {
        activityId = null;
        bienSoXe = null;
        thoiGianVao = null;
        thoiGianRa = null;
        giaVe = 0;
        hinhThucThanhToan = "tien_mat";
        vehicleId = null;
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
                    thanhToanVaInHoaDon();
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
                    capNhatHinhThucThanhToan("tien_mat");
                } else if (checkedId == R.id.rbChuyenKhoan) {
                    hinhThucThanhToan = "chuyen_khoan";
                    Log.d(TAG, "Payment method selected: Chuyển khoản");
                    capNhatHinhThucThanhToan("chuyen_khoan");
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

    private void capNhatHinhThucThanhToan(String phuongThuc) {
        if (activityId == null || activityId.isEmpty()) {
            Log.w(TAG, "ActivityId is null, cannot update payment method");
            return;
        }

        try {
            JSONObject updateData = new JSONObject();
            updateData.put("hinhThucThanhToan", phuongThuc);
            updateData.put("thoiGianChonPhuongThuc", getCurrentTime());

            // Thêm vehicle ID và vehicle type nếu có
            if (vehicleId != null && !vehicleId.isEmpty()) {
                updateData.put("vehicle", vehicleId);
            }
            if (vehicleType != null && !vehicleType.isEmpty()) {
                updateData.put("vehicleType", vehicleType);
            }

            Log.d(TAG, "Updating payment method to: " + phuongThuc);

            ApiHelper.updateActivity(activityId, updateData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Payment method updated successfully: " + phuongThuc);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String displayText = phuongThuc.equals("tien_mat") ? "Tiền mặt" : "Chuyển khoản";
                            Toast.makeText(ThanhToanActivity.this,
                                    "Đã chọn hình thức: " + displayText,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Failed to update payment method: " + errorMessage);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ThanhToanActivity.this,
                                    "Lỗi cập nhật hình thức thanh toán: " + errorMessage,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "Error creating payment method update JSON", e);
            Toast.makeText(this, "Lỗi tạo dữ liệu cập nhật", Toast.LENGTH_SHORT).show();
        }
    }

    private void kiemTraXeCanThanhToan() {
        Log.d(TAG, "Checking for vehicles needing payment");

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
        Log.d(TAG, "Getting vehicle info for: " + bienSo);

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
        // Lấy thông tin từ response
        activityId = data.getString("_id");
        bienSoXe = data.getString("bienSoXe");
        thoiGianVao = data.getString("thoiGianVao");

        // Lấy vehicle ID và vehicle type nếu có
        if (data.has("vehicle") && !data.isNull("vehicle")) {
            vehicleId = data.getString("vehicle");
        }
        if (data.has("vehicleType") && !data.isNull("vehicleType")) {
            vehicleType = data.getString("vehicleType");
        }

        // Kiểm tra xem có thời gian ra chưa
        if (data.has("thoiGianRa") && !data.isNull("thoiGianRa")) {
            thoiGianRa = data.getString("thoiGianRa");
        } else {
            // Nếu chưa có thời gian ra, set thời gian hiện tại
            thoiGianRa = getCurrentTime();
        }

        // Kiểm tra xem có hình thức thanh toán đã được chọn chưa
        if (data.has("hinhThucThanhToan") && !data.isNull("hinhThucThanhToan")) {
            hinhThucThanhToan = data.getString("hinhThucThanhToan");
        }

        // Kiểm tra giá vé từ database trước
        if (data.has("giaVe") && !data.isNull("giaVe")) {
            giaVe = data.getLong("giaVe");
        } else {
            // Tính giá vé dựa trên thời gian đậu nếu chưa có
            giaVe = tinhGiaVe(thoiGianVao, thoiGianRa, vehicleType);
        }

        Log.d(TAG, "Processing payment data:");
        Log.d(TAG, "Activity ID: " + activityId);
        Log.d(TAG, "Vehicle ID: " + vehicleId);
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

        // Cập nhật trạng thái xe ra trong database nếu chưa có
        if (!data.has("thoiGianRa") || data.isNull("thoiGianRa")) {
            capNhatXeRa();
        }
    }

    private void capNhatXeRa() {
        try {
            JSONObject updateData = new JSONObject();
            updateData.put("thoiGianRa", thoiGianRa);
            updateData.put("trangThai", "da_ra");
            updateData.put("giaVe", giaVe);

            // Thêm vehicle ID và type nếu có
            if (vehicleId != null && !vehicleId.isEmpty()) {
                updateData.put("vehicle", vehicleId);
            }
            if (vehicleType != null && !vehicleType.isEmpty()) {
                updateData.put("vehicleType", vehicleType);
            }

            ApiHelper.updateActivity(activityId, updateData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Updated vehicle checkout successfully");
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Failed to update vehicle checkout: " + errorMessage);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ThanhToanActivity.this,
                                    "Lỗi cập nhật dữ liệu: " + errorMessage,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "Error creating update JSON", e);
        }
    }

    private void hienThiThongTinHoaDon() {
        // Hiển thị biển số xe với emoji
        if (tvBienSoXe != null) {
            tvBienSoXe.setText("🚗 Biển số xe: " + bienSoXe);
            Log.d(TAG, "Displaying license plate: " + bienSoXe);
        }

        // Hiển thị loại xe
        if (tvLoaiXe != null) {
            String displayVehicleType = getDisplayVehicleType(vehicleType);
            tvLoaiXe.setText("🚙 Loại xe: " + displayVehicleType);
            Log.d(TAG, "Displaying vehicle type: " + displayVehicleType);
        }

        // Hiển thị thời gian vào
        if (tvThoiGianVao != null) {
            String formattedTimeIn = formatDisplayTime(thoiGianVao);
            tvThoiGianVao.setText("⏰ Thời gian vào: " + formattedTimeIn);
            Log.d(TAG, "Displaying time in: " + formattedTimeIn);
        }

        // Hiển thị thời gian ra
        if (tvThoiGianRa != null) {
            String formattedTimeOut = formatDisplayTime(thoiGianRa);
            tvThoiGianRa.setText("🚪 Thời gian ra: " + formattedTimeOut);
            Log.d(TAG, "Displaying time out: " + formattedTimeOut);
        }

        // Hiển thị giá vé với định dạng tiền tệ Việt Nam
        if (tvGiaVe != null) {
            NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
            String formattedPrice = formatter.format(giaVe) + "đ";
            tvGiaVe.setText("💰 Giá vé: " + formattedPrice);
            Log.d(TAG, "Displaying price: " + formattedPrice);
        }
    }

    private String getDisplayVehicleType(String vehicleType) {
        switch (vehicleType) {
            case "CAR_UNDER_9":
                return "Ô tô dưới 9 chỗ";
            case "CAR_9_TO_16":
                return "Ô tô 9-16 chỗ";
            case "MOTORCYCLE":
                return "Xe máy";
            case "TRUCK":
                return "Xe tải";
            case "BUS":
                return "Xe buýt";
            default:
                return "Ô tô dưới 9 chỗ";
        }
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

    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
        return sdf.format(new Date());
    }

    private String formatDisplayTime(String timeString) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
            Date date = inputFormat.parse(timeString);
            return outputFormat.format(date);
        } catch (Exception e) {
            Log.e(TAG, "Error formatting time: " + timeString, e);
            // Thử format khác nếu format đầu không thành công
            try {
                SimpleDateFormat inputFormat2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
                Date date = inputFormat2.parse(timeString.replace("Z", ""));
                return outputFormat.format(date);
            } catch (Exception e2) {
                Log.e(TAG, "Error formatting time with alternative format: " + timeString, e2);
                return timeString;
            }
        }
    }

    private long tinhGiaVe(String thoiGianVao, String thoiGianRa, String vehicleType) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            Date dateVao = sdf.parse(thoiGianVao);
            Date dateRa = sdf.parse(thoiGianRa);

            if (dateVao != null && dateRa != null) {
                long diffInMillis = dateRa.getTime() - dateVao.getTime();
                long diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis);

                // Quy tắc tính giá dựa theo loại xe
                long basePrice = 3000; // Mặc định cho xe dưới 9 chỗ
                long blockPrice = 2000;

                switch (vehicleType) {
                    case "CAR_9_TO_16":
                        basePrice = 5000;
                        blockPrice = 3000;
                        break;
                    case "MOTORCYCLE":
                        basePrice = 2000;
                        blockPrice = 1000;
                        break;
                    case "TRUCK":
                    case "BUS":
                        basePrice = 8000;
                        blockPrice = 5000;
                        break;
                }

                long gia = basePrice; // Giá cơ bản 30 phút đầu

                if (diffInMinutes > 30) {
                    long extraBlocks = (diffInMinutes - 30 + 29) / 30; // Làm tròn lên
                    gia += extraBlocks * blockPrice;
                }

                Log.d(TAG, "Calculated price: " + gia + " for " + diffInMinutes + " minutes, vehicle type: " + vehicleType);
                return gia;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating price", e);
        }

        return 3000; // Giá mặc định
    }

    private void thanhToanVaInHoaDon() {
        // Kiểm tra hình thức thanh toán đã được chọn
        String paymentMethodText = hinhThucThanhToan.equals("tien_mat") ? "Tiền mặt" : "Chuyển khoản";

        try {
            // Cập nhật trạng thái thanh toán trước khi in
            JSONObject updatePaymentData = new JSONObject();
            updatePaymentData.put("trangThaiThanhToan", "da_thanh_toan");
            updatePaymentData.put("thoiGianThanhToan", getCurrentTime());
            updatePaymentData.put("hinhThucThanhToan", hinhThucThanhToan);
            updatePaymentData.put("giaVe", giaVe);

            // Thêm vehicle ID và type nếu có
            if (vehicleId != null && !vehicleId.isEmpty()) {
                updatePaymentData.put("vehicle", vehicleId);
            }
            if (vehicleType != null && !vehicleType.isEmpty()) {
                updatePaymentData.put("vehicleType", vehicleType);
            }

            // Disable button để tránh spam
            btnInHoaDon.setEnabled(false);
            btnInHoaDon.setText("Đang xử lý...");

            Log.d(TAG, "Processing payment and printing invoice...");

            ApiHelper.updateActivity(activityId, updatePaymentData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Payment status updated successfully");
                    // Sau khi cập nhật thành công, tiến hành in hóa đơn
                    guiLenhInHoaDon(paymentMethodText);
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Failed to update payment status: " + errorMessage);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ThanhToanActivity.this,
                                    "Lỗi cập nhật trạng thái thanh toán: " + errorMessage,
                                    Toast.LENGTH_LONG).show();

                            // Reset button
                            resetButton();
                        }
                    });
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "Error creating payment update JSON", e);
            Toast.makeText(this, "Lỗi tạo dữ liệu thanh toán", Toast.LENGTH_SHORT).show();
            resetButton();
        }
    }

    private void guiLenhInHoaDon(String paymentMethodText) {
        try {
            JSONObject printData = new JSONObject();
            printData.put("bienSoXe", bienSoXe);
            printData.put("loaiXe", getDisplayVehicleType(vehicleType));
            printData.put("thoiGianVao", formatDisplayTime(thoiGianVao));
            printData.put("thoiGianRa", formatDisplayTime(thoiGianRa));
            printData.put("giaVe", giaVe);
            printData.put("hinhThucThanhToan", paymentMethodText);
            printData.put("activityId", activityId);
            printData.put("timestamp", System.currentTimeMillis());

            // Thêm vehicle ID và type nếu có
            if (vehicleId != null && !vehicleId.isEmpty()) {
                printData.put("vehicleId", vehicleId);
            }
            if (vehicleType != null && !vehicleType.isEmpty()) {
                printData.put("vehicleType", vehicleType);
            }

            Log.d(TAG, "Sending print command: " + printData.toString());

            ApiHelper.sendPrintCommand(printData.toString(), new ApiHelper.OnResponseListener() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Print command sent successfully");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ThanhToanActivity.this,
                                    "Thanh toán thành công! Đã gửi lệnh in hóa đơn!",
                                    Toast.LENGTH_LONG).show();

                            showInHoaDonDialog(paymentMethodText);
                            resetButton();
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Failed to send print command: " + errorMessage);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Thanh toán đã thành công nhưng in thất bại
                            Toast.makeText(ThanhToanActivity.this,
                                    "Thanh toán thành công nhưng lỗi in hóa đơn: " + errorMessage,
                                    Toast.LENGTH_LONG).show();

                            showInHoaDonDialog(paymentMethodText);
                            resetButton();
                        }
                    });
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "Error creating print JSON", e);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ThanhToanActivity.this,
                            "Thanh toán thành công nhưng lỗi tạo dữ liệu in",
                            Toast.LENGTH_SHORT).show();
                    resetButton();
                }
            });
        }
    }

    private void resetButton() {
        btnInHoaDon.setEnabled(true);
        btnInHoaDon.setText("💰 Thanh toán & In hóa đơn");
    }

    private void showInHoaDonDialog(String paymentMethodText) {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);

        NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));

        builder.setTitle("✅ Thanh toán thành công")
                .setMessage("Cảm ơn quý khách đã sử dụng dịch vụ!\n\n" +
                        "════════════════════════\n" +
                        "📋 THÔNG TIN HÓA ĐƠN\n" +
                        "════════════════════════\n" +
                        "🚗 Biển số: " + bienSoXe + "\n" +
                        "⏰ Vào lúc: " + formatDisplayTime(thoiGianVao) + "\n" +
                        "🚪 Ra lúc: " + formatDisplayTime(thoiGianRa) + "\n" +
                        "💰 Số tiền: " + formatter.format(giaVe) + " đ\n" +
                        "💳 Hình thức: " + paymentMethodText + "\n" +
                        "════════════════════════\n\n" )
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    // Reset dữ liệu và hiển thị thông báo không có xe
                    resetActivityData();
                })
                .setCancelable(false)
                .show();
    }
}