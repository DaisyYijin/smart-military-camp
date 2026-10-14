package com.szjx.personnel.server.model;

/**
 * 军事考核记录(理论/射击/战术等,按分数评定)
 */
public class MilitaryAssessment {

    private Long id;
    private Long personnelId;       // 考核人员ID
    private String personnelName;   // 姓名(查询时附带)
    private String idCard;          // 身份证号(查询时附带)
    private String category;        // 考核类别:理论考核/射击考核/战术考核等
    private String item;            // 考核项目
    private String score;           // 成绩
    private Double points;          // 分数(0-100)
    private String grade;           // 评定:优秀/良好/及格/不及格
    private String testDate;        // 考核日期
    private String remark;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPersonnelId() { return personnelId; }
    public void setPersonnelId(Long personnelId) { this.personnelId = personnelId; }

    public String getPersonnelName() { return personnelName; }
    public void setPersonnelName(String personnelName) { this.personnelName = personnelName; }

    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }

    public String getScore() { return score; }
    public void setScore(String score) { this.score = score; }

    public Double getPoints() { return points; }
    public void setPoints(Double points) { this.points = points; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getTestDate() { return testDate; }
    public void setTestDate(String testDate) { this.testDate = testDate; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
