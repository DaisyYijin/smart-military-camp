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
        TYPE_NAMES.put("fitness_item", "体能项目");
        TYPE_NAMES.put("fitness_grade", "评定等级");
        TYPE_NAMES.put("personnel_status", "人员状态");
        TYPE_NAMES.put("rank", "军衔");
        TYPE_NAMES.put("equipment_category", "装备类别");
        TYPE_NAMES.put("equipment_status", "装备状态");
        TYPE_NAMES.put("material_category", "物资分类");
        TYPE_NAMES.put("vehicle_type", "车型");
        TYPE_NAMES.put("vehicle_status", "车辆状态");
        TYPE_NAMES.put("blood_type", "血型");
        TYPE_NAMES.put("nation", "民族");
        TYPE_NAMES.put("political_status", "政治面貌");
        TYPE_NAMES.put("marital_status", "婚姻状况");
        TYPE_NAMES.put("education", "学历");
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
        DICT_TABLE_COLUMN.put("equipment_status", "equipment.status");
        DICT_TABLE_COLUMN.put("material_category", "material.category");
        DICT_TABLE_COLUMN.put("vehicle_type", "vehicle.type");
        DICT_TABLE_COLUMN.put("vehicle_status", "vehicle.status");
        DICT_TABLE_COLUMN.put("blood_type", "personnel.blood_type");
        DICT_TABLE_COLUMN.put("nation", "personnel.nation");
        DICT_TABLE_COLUMN.put("political_status", "personnel.political_status");
        DICT_TABLE_COLUMN.put("marital_status", "personnel.marital_status");
        DICT_TABLE_COLUMN.put("education", "personnel.education");
        DICT_TABLE_COLUMN.put("only_child", "personnel.only_child");
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

    /** 所有类型及其全部项(管理页面用) */
    public List<Map<String, Object>> listTypes() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (String type : dictDao.findTypes()) {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("type", type);
            m.put("name", TYPE_NAMES.get(type) == null ? type : TYPE_NAMES.get(type));
            m.put("items", dictDao.findByType(type));
            result.add(m);
        }
        return result;
    }

    public DictItem create(DictItem d) {
        if (!StringUtils.hasText(d.getDictType())) {
            throw new IllegalArgumentException("字典类型不能为空");
        }
        if (!StringUtils.hasText(d.getDictLabel())) {
            throw new IllegalArgumentException("字典项内容不能为空");
        }
        if (dictDao.exists(d.getDictType(), d.getDictLabel())) {
            throw new IllegalArgumentException("该类型下已存在相同的选项");
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
        String type = StringUtils.hasText(d.getDictType()) ? d.getDictType() : old.getDictType();
        String oldLabel = old.getDictLabel();
        String newLabel = d.getDictLabel();
        if (!newLabel.equals(oldLabel)
                && dictDao.exists(type, newLabel)) {
            throw new IllegalArgumentException("该类型下已存在相同的选项");
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
}
