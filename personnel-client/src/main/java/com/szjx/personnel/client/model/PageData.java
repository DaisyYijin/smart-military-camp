package com.szjx.personnel.client.model;

import java.util.List;

/**
 * 分页数据
 */
public class PageData<T> {

    private long total;
    private List<T> list;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }
}
