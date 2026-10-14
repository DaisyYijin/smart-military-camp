package com.szjx.personnel.server.model;

/**
 * 人员信息
 */
public class Personnel {

    private Long id;
    private String name;          // 姓名
    private String gender;        // 性别
    private String idCard;        // 身份证号
    private String birthday;      // 出生日期 yyyy-MM-dd
    private String phone;         // 手机号
    private Long departmentId;    // 部门ID
    private String departmentName;// 部门名称(查询时附带)
    private String rank;          // 军衔/职级
    private String category;      // 人员类别(军官/士官/义务兵/文职人员)
    private String post;          // 职务/岗位
    private String status;        // 状态:在职/休假/培训/借调/离职/其他
    private String entryDate;     // 入伍/入职日期
    private String address;       // 家庭住址
    private String photo;         // 照片文件名(服务端 data/photos/ 下)
    private String photoBase64;   // 仅提交时使用:base64 编码的图片数据
    private String remark;        // 备注
    private String bloodType;     // 血型
    private String nation;        // 民族
    private String politicalStatus;// 政治面貌
    private String partyJoinDate; // 党团时间(入党/入团日期)
    private String maritalStatus; // 婚姻状况
    private String education;     // 学历
    private String onlyChild;     // 是否独生子女
    private String nativePlace;   // 籍贯所在地(省/市/区县)
    private String enlistPlace;   // 入伍所在地(省/市/区县)
    private String spouseName;    // 配偶姓名
    private String spouseWork;    // 配偶工作单位
    private String spousePhone;   // 配偶电话
    private String spouseIdCard;  // 配偶身份证号
    private String spouseJob;     // 配偶职业
    private String marriageDate;  // 结婚日期
    private String partyOrgLocation; // 党团组织关系所在地
    private String partyPost;     // 党内职务
    private String partyRemark;   // 党团备注
    private String divorceDate;   // 离婚日期
    private String marriageRemark; // 婚姻备注
    private String partyApplyDate;    // 提交入党申请书时间
    private String partyActivistDate; // 入党积极分子时间
    private String partyCandidateDate; // 预备党员时间
    private String partyFullDate;      // 转正时间
    private String followArmyDate;     // 随军时间
    private String followArmyStatus;   // 随军条件
    private Double height;        // 身高(cm)
    private Double weight;        // 体重(kg)
    private String createdAt;
    private String updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getIdCard() {
        return idCard;
    }

    public void setIdCard(String idCard) {
        this.idCard = idCard;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getRank() {
        return rank;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }

    public String getPost() {
        return post;
    }

    public void setPost(String post) {
        this.post = post;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(String entryDate) {
        this.entryDate = entryDate;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public String getPhotoBase64() {
        return photoBase64;
    }

    public void setPhotoBase64(String photoBase64) {
        this.photoBase64 = photoBase64;
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

    public String getBloodType() {
        return bloodType;
    }

    public void setBloodType(String bloodType) {
        this.bloodType = bloodType;
    }

    public String getNation() {
        return nation;
    }

    public void setNation(String nation) {
        this.nation = nation;
    }

    public String getPoliticalStatus() {
        return politicalStatus;
    }

    public void setPoliticalStatus(String politicalStatus) {
        this.politicalStatus = politicalStatus;
    }

    public String getPartyJoinDate() {
        return partyJoinDate;
    }

    public void setPartyJoinDate(String partyJoinDate) {
        this.partyJoinDate = partyJoinDate;
    }

    public String getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(String maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public String getEducation() {
        return education;
    }

    public void setEducation(String education) {
        this.education = education;
    }

    public String getOnlyChild() {
        return onlyChild;
    }

    public void setOnlyChild(String onlyChild) {
        this.onlyChild = onlyChild;
    }

    public String getNativePlace() {
        return nativePlace;
    }

    public void setNativePlace(String nativePlace) {
        this.nativePlace = nativePlace;
    }

    public String getEnlistPlace() {
        return enlistPlace;
    }

    public void setEnlistPlace(String enlistPlace) {
        this.enlistPlace = enlistPlace;
    }

    public String getSpouseName() {
        return spouseName;
    }

    public void setSpouseName(String spouseName) {
        this.spouseName = spouseName;
    }

    public String getSpouseWork() {
        return spouseWork;
    }

    public void setSpouseWork(String spouseWork) {
        this.spouseWork = spouseWork;
    }

    public String getSpousePhone() {
        return spousePhone;
    }

    public void setSpousePhone(String spousePhone) {
        this.spousePhone = spousePhone;
    }

    public String getSpouseIdCard() {
        return spouseIdCard;
    }

    public void setSpouseIdCard(String spouseIdCard) {
        this.spouseIdCard = spouseIdCard;
    }

    public String getSpouseJob() {
        return spouseJob;
    }

    public void setSpouseJob(String spouseJob) {
        this.spouseJob = spouseJob;
    }

    public String getPartyOrgLocation() { return partyOrgLocation; }
    public void setPartyOrgLocation(String v) { this.partyOrgLocation = v; }
    public String getPartyPost() { return partyPost; }
    public void setPartyPost(String v) { this.partyPost = v; }
    public String getPartyRemark() { return partyRemark; }
    public void setPartyRemark(String v) { this.partyRemark = v; }
    public String getDivorceDate() { return divorceDate; }
    public void setDivorceDate(String v) { this.divorceDate = v; }
    public String getMarriageRemark() { return marriageRemark; }
    public void setMarriageRemark(String v) { this.marriageRemark = v; }
    public String getPartyApplyDate() { return partyApplyDate; }
    public void setPartyApplyDate(String v) { this.partyApplyDate = v; }
    public String getPartyActivistDate() { return partyActivistDate; }
    public void setPartyActivistDate(String v) { this.partyActivistDate = v; }
    public String getPartyCandidateDate() { return partyCandidateDate; }
    public void setPartyCandidateDate(String v) { this.partyCandidateDate = v; }
    public String getPartyFullDate() { return partyFullDate; }
    public void setPartyFullDate(String v) { this.partyFullDate = v; }
    public String getFollowArmyDate() { return followArmyDate; }
    public void setFollowArmyDate(String v) { this.followArmyDate = v; }
    public String getFollowArmyStatus() { return followArmyStatus; }
    public void setFollowArmyStatus(String v) { this.followArmyStatus = v; }

    public String getMarriageDate() {
        return marriageDate;
    }

    public void setMarriageDate(String marriageDate) {
        this.marriageDate = marriageDate;
    }

    public Double getHeight() {
        return height;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }
}
