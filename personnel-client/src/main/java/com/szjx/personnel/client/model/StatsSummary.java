package com.szjx.personnel.client.model;

import java.util.List;

/**
 * 统计汇总
 */
public class StatsSummary {

    private long total;
    private List<NameCount> department;
    private List<NameCount> gender;
    private List<NameCount> status;

    public static class NameCount {
        private String name;
        private long count;

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

    public List<NameCount> getDepartment() {
        return department;
    }

    public void setDepartment(List<NameCount> department) {
        this.department = department;
    }

    public List<NameCount> getGender() {
        return gender;
    }

    public void setGender(List<NameCount> gender) {
        this.gender = gender;
    }

    public List<NameCount> getStatus() {
        return status;
    }

    public void setStatus(List<NameCount> status) {
        this.status = status;
    }
}
