package com.example.baidoxee;

public class ParkingLog {
    private String _id;
    private String plate;
    private String time_in;
    private String time_out;
    private int fee;
    private String vehicle;  // <- Bắt buộc theo yêu cầu từ backend

    // Constructors
    public ParkingLog(String plate, String time_in, String time_out, int fee, String vehicle) {
        this.plate = plate;
        this.time_in = time_in;
        this.time_out = time_out;
        this.fee = fee;
        this.vehicle = vehicle;
    }

    public ParkingLog() {
        // empty constructor for deserialization
    }

    // Getters
    public String getId() {
        return _id;
    }

    public String getPlate() {
        return plate;
    }

    public String getTime_in() {
        return time_in;
    }

    public String getTime_out() {
        return time_out;
    }

    public int getFee() {
        return fee;
    }

    public String getVehicle() {
        return vehicle;
    }

    // Setters (nếu cần cập nhật hoặc khởi tạo từ JSON)
    public void setId(String _id) {
        this._id = _id;
    }

    public void setPlate(String plate) {
        this.plate = plate;
    }

    public void setTime_in(String time_in) {
        this.time_in = time_in;
    }

    public void setTime_out(String time_out) {
        this.time_out = time_out;
    }

    public void setFee(int fee) {
        this.fee = fee;
    }

    public void setVehicle(String vehicle) {
        this.vehicle = vehicle;
    }
}
