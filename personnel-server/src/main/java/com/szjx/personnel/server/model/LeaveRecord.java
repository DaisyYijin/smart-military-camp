package com.szjx.personnel.server.model;

/**
 * 休假记录
 */
public class LeaveRecord {

    private Long id;
    private Long personnelId;       // 休假人员ID
    private String personnelName;   // 姓名(查询时附带)
    private String year;            // 休假年度(跨年休假用哪一年额度)
    private String leaveType;       // 类型:事假/病假/年假/探亲假/婚假/其他
    private String startDate;       // 开始日期
    private String endDate;         // 结束日期
    private Double days;            // 基本天数(不含路途和节假日)
    private Double travelDays;      // 路途天数(每年度限一次)
    private Double holidayDays;     // 节假日加假(手动填写)
    private String holidayName;     // 节假日名称(国庆/元旦等,字典 holiday_type)
    private String reason;          // 事由
    private String remark;          // 备注
    private String createdAt;
    private String updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPersonnelId() {
        return personnelId;
    }

    public void setPersonnelId(Long personnelId) {
        this.personnelId = personnelId;
    }

    public String getPersonnelName() {
        return personnelName;
    }

    public void setPersonnelName(String personnelName) {
        this.personnelName = personnelName;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public Double getTravelDays() {
        return travelDays;
    }

    public void setTravelDays(Double travelDays) {
        this.travelDays = travelDays;
    }

    public Double getHolidayDays() {
        return holidayDays;
    }

    public void setHolidayDays(Double holidayDays) {
        this.holidayDays = holidayDays;
    }

    public String getHolidayName() {
        return holidayName;
    }

    public void setHolidayName(String holidayName) {
        this.holidayName = holidayName;
    }

    public String getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(String leaveType) {
        this.leaveType = leaveType;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public Double getDays() {
        return days;
    }

    public void setDays(Double days) {
        this.days = days;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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
