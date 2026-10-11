package com.szjx.personnel.client.model;

/**
 * 部门节点(树)
 */
public class DepartmentNode {

    private long id;
    private String name;
    private long parentId;
    private int sort;
    private String remark;
    private java.util.List<DepartmentNode> children;

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

    public long getParentId() {
        return parentId;
    }

    public void setParentId(long parentId) {
        this.parentId = parentId;
    }

    public int getSort() {
        return sort;
    }

    public void setSort(int sort) {
        this.sort = sort;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public java.util.List<DepartmentNode> getChildren() {
        return children;
    }

    public void setChildren(java.util.List<DepartmentNode> children) {
        this.children = children;
    }

    /** 树节点显示名称 */
    @Override
    public String toString() {
        return name == null ? "" : name;
    }
}
