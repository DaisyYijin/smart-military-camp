package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.DictDao;
import com.szjx.personnel.server.model.DictItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据字典业务
 */
@Service
public class DictService {

    /** 字典类型 → 中文名(管理界面显示用) */
    public static final Map<String, String> TYPE_NAMES = new HashMap<String, String>();

    static {
        TYPE_NAMES.put("leave_type", "休假类型");
        TYPE_NAMES.put("device_type", "设备类型");
        TYPE_NAMES.put("device_status", "设备状态");
        TYPE_NAMES.put("phone_brand", "手机品牌");
        TYPE_NAMES.put("fitness_item", "体能项目");
        TYPE_NAMES.put("fitness_grade", "评定等级");
        TYPE_NAMES.put("personnel_status", "人员状态");
        TYPE_NAMES.put("absence_reason", "在外事由");
        TYPE_NAMES.put("rank", "军衔");
        TYPE_NAMES.put("equipment_category", "装备类别");
        TYPE_NAMES.put("equipment_brand", "装备品牌");
        TYPE_NAMES.put("equipment_status", "装备状态");
        TYPE_NAMES.put("equipment_location", "装备存放位置");
        TYPE_NAMES.put("material_category", "物资分类");
        TYPE_NAMES.put("vehicle_type", "车型");
        TYPE_NAMES.put("vehicle_status", "车辆状态");
        TYPE_NAMES.put("blood_type", "血型");
        TYPE_NAMES.put("nation", "民族");
        TYPE_NAMES.put("political_status", "政治面貌");
        TYPE_NAMES.put("marital_status", "婚姻状况");
        TYPE_NAMES.put("education", "学历");
        TYPE_NAMES.put("edu_degree", "学位");
        TYPE_NAMES.put("edu_form", "学历形式");
        TYPE_NAMES.put("military_type", "军事考核类型");
        TYPE_NAMES.put("personnel_location", "人员所在地点");
        TYPE_NAMES.put("gender", "性别");
        TYPE_NAMES.put("reward_punish_kind", "奖惩类型");
        TYPE_NAMES.put("reward_content", "奖惩内容");
        TYPE_NAMES.put("only_child", "是否独生子女");
        TYPE_NAMES.put("family_relation", "家庭关系");
        TYPE_NAMES.put("license_type", "驾驶证类型");
        TYPE_NAMES.put("card_type", "卡类型");
        TYPE_NAMES.put("housing_type", "住房类型");
        TYPE_NAMES.put("follow_army_status", "随军条件");
        TYPE_NAMES.put("license_status", "驾驶证状态");
        TYPE_NAMES.put("card_status", "卡状态");
        TYPE_NAMES.put("vehicle_status_sub", "私家车状态");
        TYPE_NAMES.put("house_nature", "房屋性质");
        TYPE_NAMES.put("has_cert", "是否有房产证");
        TYPE_NAMES.put("loan_status", "贷款情况");
        TYPE_NAMES.put("material_location", "物资存放位置");
        TYPE_NAMES.put("personnel_category", "人员类别");
        TYPE_NAMES.put("vehicle_brand", "车辆品牌");
        TYPE_NAMES.put("vehicle_fuel", "车辆燃料");
        TYPE_NAMES.put("vehicle_model", "车辆型号");
        TYPE_NAMES.put("holiday_type", "节假日");
        TYPE_NAMES.put("region_province", "省份(行政区划)");
        TYPE_NAMES.put("region_city", "城市(行政区划)");
        TYPE_NAMES.put("region_district", "区县(行政区划)");
        TYPE_NAMES.put("de_rating", "双争评定");
        TYPE_NAMES.put("annual_rating", "年度考评评定");
    }

    /**
     * 字典类型 → 业务表.字段。
     * 字典项改名时,自动把各业务表中旧名称同步为新名称,
     * 保证历史数据(如人员状态"在职")随字典一起更新。
     */
    private static final Map<String, String> DICT_TABLE_COLUMN = new HashMap<String, String>();

    static {
        DICT_TABLE_COLUMN.put("leave_type", "leave_record.leave_type");
        DICT_TABLE_COLUMN.put("fitness_item", "fitness_record.item");
        DICT_TABLE_COLUMN.put("fitness_grade", "fitness_record.grade");
        DICT_TABLE_COLUMN.put("personnel_status", "personnel.status");
        DICT_TABLE_COLUMN.put("rank", "personnel.rank");
        DICT_TABLE_COLUMN.put("equipment_category", "equipment.category");
        DICT_TABLE_COLUMN.put("equipment_brand", "equipment.brand");
        DICT_TABLE_COLUMN.put("equipment_status", "equipment.status");
        DICT_TABLE_COLUMN.put("equipment_location", "equipment.location");
        DICT_TABLE_COLUMN.put("material_category", "material.category");
        DICT_TABLE_COLUMN.put("material_location", "material.location");
        DICT_TABLE_COLUMN.put("vehicle_type", "vehicle.type");
        DICT_TABLE_COLUMN.put("vehicle_status", "vehicle.status");
        DICT_TABLE_COLUMN.put("blood_type", "personnel.blood_type");
        DICT_TABLE_COLUMN.put("nation", "personnel.nation");
        DICT_TABLE_COLUMN.put("political_status", "personnel.political_status");
        DICT_TABLE_COLUMN.put("marital_status", "personnel.marital_status");
        DICT_TABLE_COLUMN.put("education", "personnel.education");
        DICT_TABLE_COLUMN.put("only_child", "personnel.only_child");
        // 评级字典改名时同步更新评比/考评历史记录,与人员状态等字典的既有机制一致
        DICT_TABLE_COLUMN.put("de_rating", "double_excellence_record.rating");
        DICT_TABLE_COLUMN.put("annual_rating", "annual_appraisal_record.rating");
    }

    /** 字典类型业务分组(有序):管理页左侧按组分段显示;未列出的类型自动归入"其他"组 */
    private static final java.util.LinkedHashMap<String, java.util.List<String>> DICT_GROUPS = new java.util.LinkedHashMap<String, java.util.List<String>>();
    static {
        String[][] groups = {
            {"人员档案", "gender,nation,blood_type,political_status,marital_status,education,edu_degree,edu_form,personnel_category,rank,personnel_status,personnel_location,only_child,family_relation,follow_army_status,housing_type,house_nature"},
            {"证件与卡", "card_type,card_status,license_status,license_type,has_cert"},
            {"在外与休假", "absence_reason,leave_type,holiday_type"},
            {"军事训练与考评", "military_type,fitness_item,fitness_grade,de_rating,annual_rating"},
            {"装备与物资", "equipment_category,equipment_status,equipment_location,equipment_brand,material_category,material_location,device_type,device_status"},
            {"手机与借还", "phone_brand,loan_status"},
            {"车辆", "vehicle_type,vehicle_brand,vehicle_model,vehicle_fuel,vehicle_status,vehicle_status_sub"},
            {"奖惩", "reward_punish_kind,reward_content"},
            {"行政区划", "region_province,region_city,region_district"},
        };
        for (String[] g : groups) {
            DICT_GROUPS.put(g[0], java.util.Arrays.asList(g[1].split(",")));
        }
    }

    private final DictDao dictDao;
    private final JdbcTemplate jdbc;

    public DictService(DictDao dictDao, JdbcTemplate jdbc) {
        this.dictDao = dictDao;
        this.jdbc = jdbc;
    }

    /** 某类型启用项(下拉用) */
    public List<DictItem> listEnabled(String type) {
        return dictDao.findByTypeEnabled(type);
    }

    /** 所有类型及其全部项(管理页面用):按业务分组有序输出,每项含 group 字段,未映射类型归入"其他"组 */
    public List<Map<String, Object>> listTypes() {
        List<String> types = dictDao.findTypes(); // 组内仍按类型 key 字母序,整体顺序由业务分组决定
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        java.util.Set<String> emitted = new java.util.HashSet<String>();
        for (java.util.Map.Entry<String, java.util.List<String>> g : DICT_GROUPS.entrySet()) {
            for (String type : types) {
                if (g.getValue().contains(type)) {
                    result.add(typeEntry(type, g.getKey()));
                    emitted.add(type);
                }
            }
        }
        for (String type : types) {
            if (!emitted.contains(type)) {
                result.add(typeEntry(type, "其他"));
            }
        }
        return result;
    }

    private Map<String, Object> typeEntry(String type, String group) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("type", type);
        m.put("name", TYPE_NAMES.get(type) == null ? type : TYPE_NAMES.get(type));
        m.put("group", group);
        m.put("items", dictDao.findByType(type));
        return m;
    }

    public DictItem create(DictItem d) {
        if (!StringUtils.hasText(d.getDictType())) {
            throw new IllegalArgumentException("字典类型不能为空");
        }
        if (!StringUtils.hasText(d.getDictLabel())) {
            throw new IllegalArgumentException("字典项内容不能为空");
        }
        if (d.getParentId() != null && d.getParentId() > 0) {
            // 有上级的项允许跨父级同名(如不同城市下的同名区县)
            Long n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_dict WHERE dict_type=? AND dict_label=? AND parent_id=?",
                    Long.class, d.getDictType(), d.getDictLabel(), d.getParentId());
            if (n != null && n > 0) {
                throw new IllegalArgumentException("该类型下已存在相同的选项");
            }
        } else if (dictDao.exists(d.getDictType(), d.getDictLabel())) {
            throw new IllegalArgumentException("该类型下已存在相同的选项");
        }
        if (d.getEnabled() == null) {
            d.setEnabled(true);   // API 未传时默认启用
        }
        if (d.getSort() == null) {
            d.setSort(dictDao.nextSort(d.getDictType()));   // 默认排在已有选项末尾，而不是 0
        }
        long id = dictDao.insert(d);
        return dictDao.findById(id);
    }

    @Transactional
    public DictItem update(Long id, DictItem d) {
        DictItem old = dictDao.findById(id);
        if (old == null) {
            throw new IllegalArgumentException("字典项不存在");
        }
        if (!StringUtils.hasText(d.getDictLabel())) {
            throw new IllegalArgumentException("字典项内容不能为空");
        }
        if (d.getEnabled() == null) {
            d.setEnabled(old.getEnabled());   // 未传时保持原状态
        }
        if (d.getSort() == null) {
            d.setSort(old.getSort() == null ? 0 : old.getSort());   // 未传时保持原排序
        }
        String type = StringUtils.hasText(d.getDictType()) ? d.getDictType() : old.getDictType();
        String oldLabel = old.getDictLabel();
        String newLabel = d.getDictLabel();
        Long newParent = d.getParentId() != null ? d.getParentId()
                : (old.getParentId() != null ? old.getParentId() : 0L);
        if (!newLabel.equals(oldLabel)) {
            if (newParent != null && newParent > 0) {
                // 有上级的项允许跨父级同名
                Long n = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_dict WHERE dict_type=? AND dict_label=? AND parent_id=? AND id<>?",
                        Long.class, type, newLabel, newParent, id);
                if (n != null && n > 0) {
                    throw new IllegalArgumentException("该类型下已存在相同的选项");
                }
            } else if (dictDao.exists(type, newLabel)) {
                throw new IllegalArgumentException("该类型下已存在相同的选项");
            }
        }
        d.setId(id);
        if (!StringUtils.hasText(d.getDictType())) {
            d.setDictType(old.getDictType());
        }
        dictDao.update(d);
        // 字典改名时,同步更新各业务表中的历史数据
        if (!newLabel.equals(oldLabel)) {
            String tableCol = DICT_TABLE_COLUMN.get(old.getDictType());
            if (tableCol != null) {
                String[] parts = tableCol.split("\\.");
                int n = jdbc.update("UPDATE " + parts[0] + " SET " + parts[1] + "=? WHERE " + parts[1] + "=?",
                        newLabel, oldLabel);
                if (n > 0) {
                    org.slf4j.LoggerFactory.getLogger(DictService.class)
                            .info("字典[{}] {}({}) → {}:已同步更新 {} 条业务数据",
                                    old.getDictType(), oldLabel, tableCol, newLabel, n);
                }
            }
        }
        return dictDao.findById(id);
    }

    /** 拖拽排序：只调整同一上级下的完整选项组，避免部分更新造成错位。 */
    @Transactional
    public void reorder(String type, List<Long> ids) {
        if (!StringUtils.hasText(type)) {
            throw new IllegalArgumentException("字典类型不能为空");
        }
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("排序项不能为空");
        }
        List<DictItem> all = dictDao.findByType(type);
        java.util.Set<Long> seen = new java.util.LinkedHashSet<Long>();
        List<DictItem> group = new ArrayList<DictItem>();
        long parentId = -1;
        for (Long id : ids) {
            if (id == null || id <= 0 || !seen.add(id)) {
                throw new IllegalArgumentException("排序项ID不合法或重复");
            }
            DictItem found = null;
            for (DictItem item : all) {
                if (id.equals(item.getId())) { found = item; break; }
            }
            if (found == null) {
                throw new IllegalArgumentException("字典项不属于当前类型");
            }
            long p = found.getParentId() == null ? 0L : found.getParentId();
            if (parentId < 0) {
                parentId = p;
            } else if (parentId != p) {
                throw new IllegalArgumentException("只能在同级选项之间排序");
            }
            group.add(found);
        }
        int siblings = 0;
        for (DictItem item : all) {
            long p = item.getParentId() == null ? 0L : item.getParentId();
            if (p == parentId) siblings++;
        }
        if (siblings != group.size()) {
            throw new IllegalArgumentException("排序必须包含该组全部选项");
        }
        for (int i = 0; i < group.size(); i++) {
            dictDao.updateSort(group.get(i).getId(), i + 1);
        }
    }

    /**
     * 删除字典项,并同步清空业务数据中引用该选项的值
     * @return 被清空的业务记录数
     */
    @Transactional
    public int delete(Long id) {
        DictItem old = dictDao.findById(id);
        if (old == null) {
            throw new IllegalArgumentException("字典项不存在");
        }
        if (dictDao.countChildren(id) > 0) {
            throw new IllegalArgumentException("该选项下有 " + dictDao.countChildren(id) + " 个下级项,请先删除下级");
        }
        dictDao.delete(id);
        renumberSiblings(old);
        int cleared = 0;
        String tableCol = DICT_TABLE_COLUMN.get(old.getDictType());
        if (tableCol != null) {
            String[] parts = tableCol.split("\\.");
            cleared = jdbc.update("UPDATE " + parts[0] + " SET " + parts[1] + "='' WHERE " + parts[1] + "=?",
                    old.getDictLabel());
            if (cleared > 0) {
                org.slf4j.LoggerFactory.getLogger(DictService.class)
                        .info("字典[{}] 删除 \"{}\":已清空 {} 条业务数据中的该选项",
                                old.getDictType(), old.getDictLabel(), cleared);
            }
        }
        return cleared;
    }

    /** 删除后把同一上级下的剩余选项重排为连续序号，其他层级保持不变。 */
    private void renumberSiblings(DictItem deleted) {
        long parentId = deleted.getParentId() == null ? 0L : deleted.getParentId();
        int position = 1;
        for (DictItem item : dictDao.findByType(deleted.getDictType())) {
            long p = item.getParentId() == null ? 0L : item.getParentId();
            if (p != parentId) continue;
            Integer current = item.getSort();
            if (current == null || current.intValue() != position) {
                dictDao.updateSort(item.getId(), position);
            }
            position++;
        }
    }
}
