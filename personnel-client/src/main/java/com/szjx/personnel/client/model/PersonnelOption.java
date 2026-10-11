package com.szjx.personnel.client.model;

/**
 * 人员下拉选项(精简)
 */
public class PersonnelOption {

    private long id;
    private String name;
    private String departmentName;
    private String rank;
    private String post;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
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

    /** 下拉框显示:姓名(部门/军衔) */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name);
        if (departmentName != null && !departmentName.isEmpty()) {
            sb.append("(").append(departmentName);
            if (rank != null && !rank.isEmpty()) {
                sb.append("/").append(rank);
            }
            sb.append(")");
        }
        return sb.toString();
    }
}
