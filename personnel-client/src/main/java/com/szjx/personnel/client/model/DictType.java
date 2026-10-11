package com.szjx.personnel.client.model;

import java.util.List;

/**
 * 字典类型及选项(管理页面用)
 */
public class DictType {

    private String type;
    private String name;
    private List<DictItem> items;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<DictItem> getItems() {
        return items;
    }

    public void setItems(List<DictItem> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return name == null ? type : name;
    }
}
