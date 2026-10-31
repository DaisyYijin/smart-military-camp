package com.szjx.personnel.server.util;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 单位名称和层级的请求级索引；调用方仍须独立限制人员及可返回单位的权限。 */
public final class DepartmentHierarchy {
    private final Map<Long, Node> nodes = new LinkedHashMap<Long, Node>();
    private final Map<Long, List<Node>> children = new HashMap<Long, List<Node>>();
    private final List<Node> ordered = new ArrayList<Node>();

    public static DepartmentHierarchy load(JdbcTemplate jdbc) {
        return new DepartmentHierarchy(jdbc.query(
                "SELECT id,name,parent_id,sort FROM department ORDER BY sort,id",
                (rs, row) -> new Node(rs.getLong("id"), rs.getLong("parent_id"),
                        rs.getString("name"), rs.getInt("sort"))));
    }

    private DepartmentHierarchy(List<Node> input) {
        for (Node node : input) {
            if (node.id > 0) nodes.put(node.id, node);
        }
        for (Node node : nodes.values()) {
            if (node.parentId.equals(node.id) || !nodes.containsKey(node.parentId)) node.parentId = 0L;
        }
        // 历史脏数据可能形成环。固定断开环内最小 ID 的父边，不合并同名单位。
        Set<Long> done = new HashSet<Long>();
        for (Node start : nodes.values()) {
            List<Node> chain = new ArrayList<Node>();
            Map<Long, Integer> positions = new HashMap<Long, Integer>();
            Node current = start;
            while (current != null && !done.contains(current.id)) {
                Integer previous = positions.put(current.id, chain.size());
                if (previous != null) {
                    Node root = current;
                    for (int i = previous; i < chain.size(); i++) {
                        if (chain.get(i).id < root.id) root = chain.get(i);
                    }
                    root.parentId = 0L;
                    break;
                }
                chain.add(current);
                current = nodes.get(current.parentId);
            }
            for (Node node : chain) done.add(node.id);
        }
        for (Node node : nodes.values()) {
            node.parent = nodes.get(node.parentId);
            List<Node> list = children.get(node.parentId);
            if (list == null) {
                list = new ArrayList<Node>();
                children.put(node.parentId, list);
            }
            list.add(node);
        }
        Comparator<Node> order = Comparator.comparingInt(Node::getSort).thenComparingLong(Node::getId);
        for (List<Node> list : children.values()) list.sort(order);
        Deque<Node> stack = new ArrayDeque<Node>();
        pushReversed(stack, children.get(0L));
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            node.depth = node.parent == null ? 0 : node.parent.depth + 1;
            ordered.add(node);
            pushReversed(stack, children.get(node.id));
        }
    }

    private static void pushReversed(Deque<Node> stack, List<Node> list) {
        if (list == null) return;
        for (int i = list.size() - 1; i >= 0; i--) stack.push(list.get(i));
    }

    public List<Node> ordered() { return Collections.unmodifiableList(ordered); }
    public Node get(Long id) { return nodes.get(id); }
    public String path(Long id) {
        Node node = get(id);
        return node == null ? "" : node.getPath();
    }

    public List<Long> ancestorIds(Long id) {
        List<Long> ids = new ArrayList<Long>();
        Node node = get(id);
        while (node != null) {
            ids.add(node.id);
            node = node.parent;
        }
        return ids;
    }

    public Set<Long> descendantIds(Long id) {
        Set<Long> ids = new LinkedHashSet<Long>();
        Node root = get(id);
        if (root == null) return ids;
        Deque<Node> stack = new ArrayDeque<Node>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            ids.add(node.id);
            pushReversed(stack, children.get(node.id));
        }
        return ids;
    }

    /** 列表通用:按行内单位ID填充完整路径(键 idKey 移除,输出 outKey);供各业务列表附带人员单位。 */
    public static void fillPath(org.springframework.jdbc.core.JdbcTemplate jdbc,
                                List<Map<String, Object>> rows, String idKey, String outKey) {
        if (rows == null || rows.isEmpty()) return;
        DepartmentHierarchy hierarchy = load(jdbc);
        for (Map<String, Object> row : rows) {
            Object id = row.remove(idKey);
            row.put(outKey, id instanceof Number ? hierarchy.path(((Number) id).longValue()) : "");
        }
    }

    public static final class Node {
        private final Long id;
        private Long parentId;
        private final String name;
        private final int sort;
        private int depth;
        private Node parent;
        private String path;

        private Node(Long id, Long parentId, String name, int sort) {
            this.id = id;
            this.parentId = parentId;
            this.name = name == null ? "" : name;
            this.sort = sort;
        }

        public Long getId() { return id; }
        public Long getParentId() { return parentId; }
        public String getName() { return name; }
        public int getSort() { return sort; }
        public int getDepth() { return depth; }
        public String getPath() {
            if (path == null) {
                LinkedList<String> names = new LinkedList<String>();
                Node current = this;
                while (current != null) {
                    names.addFirst(current.name);
                    current = current.parent;
                }
                path = String.join("", names);
            }
            return path;
        }
    }
}
