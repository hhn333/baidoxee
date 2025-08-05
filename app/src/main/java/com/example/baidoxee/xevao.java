package com.example.baidoxee;

public class xevao {
    private String vehicle;
    private String timeIn;
    private String imageUrl; // Thêm field cho hình ảnh

    public xevao() {
    }

    public xevao(String vehicle, String timeIn) {
        this.vehicle = vehicle;
        this.timeIn = timeIn;
    }

    public xevao(String vehicle, String timeIn, String imageUrl) {
        this.vehicle = vehicle;
        this.timeIn = timeIn;
        this.imageUrl = imageUrl;
    }

    public String getVehicle() {
        return vehicle;
    }

    public void setVehicle(String vehicle) {
        this.vehicle = vehicle;
    }

    public String getTimeIn() {
        return timeIn;
    }

    public void setTimeIn(String timeIn) {
        this.timeIn = timeIn;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    @Override
    public String toString() {
        return "XeVao{" +
                "vehicle='" + vehicle + '\'' +
                ", timeIn='" + timeIn + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                '}';
    }
}