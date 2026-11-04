package com.szjx.personnel.server.model;

/**
 * 人员在外登记(临时出差/学习等离开单位的事务)。
 * 预计返回可空(返期未定);实际返回为空表示未归,登记后人员状态按事由自动变更,返回后恢复"在位"。
 */
public class AbsenceRecord {
    private Long id;
    private Long personnelId;
    private String personnelName;       // 关联展示字段,不入库
    private String reason;              // 事由:出差/学习/其他(字典 absence_reason)
    private String leaveDate;           // 离开单位日期 yyyy-MM-dd
    private String expectedReturnDate;  // 预计返回日期,可空=返期未定
    private String actualReturnDate;    // 实际返回日期,空=未归
    private String remark;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPersonnelId() { return personnelId; }
    public void setPersonnelId(Long personnelId) { this.personnelId = personnelId; }

    public String getPersonnelName() { return personnelName; }
    public void setPersonnelName(String personnelName) { this.personnelName = personnelName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getLeaveDate() { return leaveDate; }
    public void setLeaveDate(String leaveDate) { this.leaveDate = leaveDate; }

    public String getExpectedReturnDate() { return expectedReturnDate; }
    public void setExpectedReturnDate(String expectedReturnDate) { this.expectedReturnDate = expectedReturnDate; }

    public String getActualReturnDate() { return actualReturnDate; }
    public void setActualReturnDate(String actualReturnDate) { this.actualReturnDate = actualReturnDate; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
