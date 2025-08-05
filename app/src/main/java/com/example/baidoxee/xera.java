package com.example.baidoxee;

public class xera {
    private String vehicle;
    private String timeIn;
    private String timeOut;

    public xera() {
    }

    public xera(String vehicle, String timeIn, String timeOut) {
        this.vehicle = vehicle;
        this.timeIn = timeIn;
        this.timeOut = timeOut;
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

    public String getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(String timeOut) {
        this.timeOut = timeOut;
    }

    @Override
    public String toString() {
        return "xera{" +
                "vehicle='" + vehicle + '\'' +
                ", timeIn='" + timeIn + '\'' +
                ", timeOut='" + timeOut + '\'' +
                '}';
    }
}
