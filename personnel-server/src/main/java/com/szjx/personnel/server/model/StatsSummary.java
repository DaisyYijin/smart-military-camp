package com.szjx.personnel.server.model;

/**
 * 统计信息
 */
public class StatsSummary {

    private long total;
    private java.util.List<NameCount> department = new java.util.ArrayList<>();
    private java.util.List<NameCount> gender = new java.util.ArrayList<>();
    private java.util.List<NameCount> status = new java.util.ArrayList<>();

    public static class NameCount {
        private String name;
        private long count;

        public NameCount() {
        }

        public NameCount(String name, long count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public java.util.List<NameCount> getDepartment() {
        return department;
    }

    public void setDepartment(java.util.List<NameCount> department) {
        this.department = department;
    }

    public java.util.List<NameCount> getGender() {
        return gender;
    }

    public void setGender(java.util.List<NameCount> gender) {
        this.gender = gender;
    }

    public java.util.List<NameCount> getStatus() {
        return status;
    }

    public void setStatus(java.util.List<NameCount> status) {
        this.status = status;
    }
}
