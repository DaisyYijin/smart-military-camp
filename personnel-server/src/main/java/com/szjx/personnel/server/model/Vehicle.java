package com.szjx.personnel.server.model;

/**
 * 车辆台账
 */
public class Vehicle {

    private Long id;
    private String plateNo;      // 车牌号
    private String vin;          // 车架号
    private String engineNo;     // 发动机号
    private String brand;        // 品牌型号
    private String type;         // 车型(字典 vehicle_type)
    private String color;        // 车身颜色
    private String fuelType;     // 燃料类型(字典 vehicle_fuel)
    private String status;       // 状态(字典 vehicle_status)
    private Long driverId;       // 驾驶员ID
    private String driverName;   // 驾驶员姓名(查询时附带)
    private Integer seats;       // 核定人数
    private Double mileage;      // 里程(公里)
    private String purchaseDate;      // 购置日期
    private String insuranceExpiry;   // 保险到期日
    private String inspectionExpiry;  // 年检到期日
    private String remark;
    private String createdAt;
    private String updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public String getEngineNo() {
        return engineNo;
    }

    public void setEngineNo(String engineNo) {
        this.engineNo = engineNo;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getFuelType() {
        return fuelType;
    }

    public void setFuelType(String fuelType) {
        this.fuelType = fuelType;
    }

    public String getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(String purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public String getInsuranceExpiry() {
        return insuranceExpiry;
    }

    public void setInsuranceExpiry(String insuranceExpiry) {
        this.insuranceExpiry = insuranceExpiry;
    }

    public String getInspectionExpiry() {
        return inspectionExpiry;
    }

    public void setInspectionExpiry(String inspectionExpiry) {
        this.inspectionExpiry = inspectionExpiry;
    }

    public String getPlateNo() {
        return plateNo;
    }

    public void setPlateNo(String plateNo) {
        this.plateNo = plateNo;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public Integer getSeats() {
        return seats;
    }

    public void setSeats(Integer seats) {
        this.seats = seats;
    }

    public Double getMileage() {
        return mileage;
    }

    public void setMileage(Double mileage) {
        this.mileage = mileage;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
