package com.szjx.personnel.server.model;

/** 姓名、身份证只用于关联校验和展示，持久化以 personnelId 为准。年度考评：一人一年一条。 */
public class AnnualAppraisalRecord {
    private Long id;
    private Long personnelId;
    private Integer year;
    private String personnelName;
    private String idCard;
    private String rating;
    private String ratingDate;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPersonnelId() { return personnelId; }
    public void setPersonnelId(Long personnelId) { this.personnelId = personnelId; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public String getPersonnelName() { return personnelName; }
    public void setPersonnelName(String personnelName) { this.personnelName = personnelName; }
    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }
    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }
    public String getRatingDate() { return ratingDate; }
    public void setRatingDate(String ratingDate) { this.ratingDate = ratingDate; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
