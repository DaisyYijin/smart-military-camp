package com.szjx.personnel.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 数据字典域的 schema 种子与迁移：默认字典、唯一约束升级、行政区划种子、
 * 直辖市改名、排序压缩与学历选项迁移。全部幂等，可重复执行。
 * 从 SchemaInitializer 按职责拆出，由其在启动序列中原位调用。
 */
class DictSchemaSeed {

    private static final Logger log = LoggerFactory.getLogger(DictSchemaSeed.class);

    private final JdbcTemplate jdbc;

    DictSchemaSeed(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 启动时确保各类默认字典存在(INSERT OR IGNORE 幂等,不覆盖已有自定义项) */
    void initDefaultDict() {
        addDict("leave_type", new String[]{"事假", "病假", "年假", "探亲假", "婚假", "产假", "陪产假", "其他"});
        addDict("holiday_type", new String[]{"元旦", "春节", "清明节", "劳动节", "端午节", "中秋节", "国庆节", "其他"});
        addDict("fitness_item", new String[]{"3000米跑", "5000米跑", "100米跑", "引体向上", "俯卧撑", "仰卧起坐", "立定跳远", "BMI", "其他"});
        addDict("fitness_grade", new String[]{"优秀", "良好", "及格", "不及格"});
        addDict("de_rating", new String[]{"好", "中", "一般"});
        addDict("annual_rating", new String[]{"优秀", "称职", "一般称职", "不称职"});
        addDict("personnel_status", new String[]{"在职", "休假", "培训", "借调", "离职", "其他"});
        addDict("personnel_category", new String[]{"军官", "士官", "义务兵", "文职人员", "其他"});
        // 军衔按人员类别挂接(级联下拉)
        attachDictChildren("personnel_category", "军官", "rank",
                new String[]{"少尉", "中尉", "上尉", "少校", "中校", "上校", "大校"});
        attachDictChildren("personnel_category", "士官", "rank",
                new String[]{"下士", "中士", "二级上士", "一级上士", "三级军士长", "二级军士长", "一级军士长"});
        attachDictChildren("personnel_category", "义务兵", "rank", new String[]{"列兵", "上等兵"});
        attachDictChildren("personnel_category", "其他", "rank", new String[]{"无"});
        addDict("rank", new String[]{"列兵", "上等兵", "下士", "中士", "二级上士", "一级上士",
                "三级军士长", "二级军士长", "一级军士长", "少尉", "中尉", "上尉", "少校", "中校", "上校", "大校", "无"});
        addDict("equipment_category", new String[]{"枪械", "防护", "通信", "侦察", "生活", "其他"});
        addDict("equipment_status", new String[]{"在用", "库存", "维修", "报废"});
        addDict("equipment_brand", new String[]{"其他"});
        addDict("equipment_location", new String[]{"枪械库", "弹药库", "器材库", "一号库房", "二号库房", "办公室", "其他"});
        addDict("material_category", new String[]{"办公", "被装", "食品", "医药", "工具", "其他"});
        addDict("material_location", new String[]{"一号库房", "二号库房", "器材库", "给养库", "油料库", "弹药库", "车间", "办公室", "其他"});
        addDict("vehicle_type", new String[]{"轿车", "客车", "卡车", "越野车", "特种车", "其他"});
        addDict("vehicle_status", new String[]{"可用", "出车", "维修", "停用"});
        addDict("vehicle_fuel", new String[]{"汽油", "柴油", "电", "混合动力", "其他"});
        addDict("vehicle_brand", new String[]{"东风", "解放", "陕汽", "中国重汽", "北汽", "猎豹", "长城", "上汽大通",
                "丰田", "三菱", "日产", "本田", "大众", "别克", "福特", "奔驰", "其他"});
        // 车辆型号(下级示例):挂在品牌下,表单里选品牌后级联过滤
        addDictChildren("vehicle_brand", "东风", "vehicle_model", new String[]{"猛士EQ2050", "猛士EQ2081", "东风天锦"});
        addDictChildren("vehicle_brand", "北汽", "vehicle_model", new String[]{"勇士BJ2022", "BJ80"});
        addDictChildren("vehicle_brand", "丰田", "vehicle_model", new String[]{"普拉多", "考斯特", "兰德酷路泽"});
        addDict("blood_type", new String[]{"未知", "A", "B", "AB", "O"});
        addDict("nation", new String[]{"汉族", "蒙古族", "回族", "藏族", "维吾尔族", "苗族", "彝族", "壮族",
                "布依族", "朝鲜族", "满族", "侗族", "瑶族", "白族", "土家族", "哈尼族", "哈萨克族", "傣族",
                "黎族", "傈僳族", "佤族", "畲族", "高山族", "拉祜族", "水族", "东乡族", "纳西族", "景颇族",
                "柯尔克孜族", "土族", "达斡尔族", "仫佬族", "羌族", "布朗族", "撒拉族", "毛南族", "仡佬族",
                "锡伯族", "阿昌族", "普米族", "塔吉克族", "怒族", "乌孜别克族", "俄罗斯族", "鄂温克族",
                "德昂族", "保安族", "裕固族", "京族", "塔塔尔族", "独龙族", "鄂伦春族", "赫哲族", "门巴族",
                "珞巴族", "基诺族"});
        addDict("political_status", new String[]{"群众", "共青团员", "中共党员", "其他"});
        addDict("marital_status", new String[]{"未婚", "已婚", "离异", "丧偶", "其他"});
        addDict("education", new String[]{"博士", "硕士", "本科", "大专", "职高", "中专", "初中", "小学", "其他"});
        addDict("only_child", new String[]{"否", "是"});
        addDict("gender", new String[]{"男", "女"});
        addDict("reward_punish_kind", new String[]{"奖励", "处分"});
        addDict("family_relation", new String[]{"配偶", "子女", "父亲", "母亲", "岳父", "岳母", "兄弟", "姐妹", "其他"});
        addDict("license_type", new String[]{"军本", "地方本"});
        addDict("card_type", new String[]{"保障卡号", "保障卡银行卡号", "银行卡号", "其他"});
        addDict("housing_type", new String[]{"自有住房", "按揭住房", "部队公寓", "租房", "其他"});
        addDict("follow_army_status", new String[]{"符合", "不符合", "未申请"});
        addDict("license_status", new String[]{"有效", "过期", "吊销"});
        addDict("card_status", new String[]{"正常", "挂失", "注销"});
        addDict("vehicle_status_sub", new String[]{"正常", "出售", "报废"});
        addDict("house_nature", new String[]{"商品房", "房改房", "自建房", "其他"});
        addDict("has_cert", new String[]{"有", "无"});
        addDict("loan_status", new String[]{"全款", "按揭中", "已还清"});
        log.info("已初始化默认数据字典");
    }

    /** 将装备台账中已有的存放位置与非空品牌迁入字典,保留历史自定义值且可重复执行 */
    void migrateEquipmentLedgerValues() {
        // 将装备台账中已有的存放位置迁入字典,避免历史值在下拉框中无法选择
        jdbc.update("INSERT OR IGNORE INTO sys_dict(dict_type, dict_label, sort, enabled, remark, parent_id) " +
                "SELECT 'equipment_location', location, 90, 1, '', 0 FROM equipment WHERE location IS NOT NULL AND TRIM(location) <> ''");
        // 将装备台账中已有的非空品牌迁入字典,保留历史自定义值且可重复执行
        jdbc.update("INSERT OR IGNORE INTO sys_dict(dict_type, dict_label, sort, enabled, remark, parent_id) " +
                "SELECT DISTINCT 'equipment_brand', brand, 90, 1, '', 0 FROM equipment WHERE brand IS NOT NULL AND TRIM(brand) <> ''");
    }

    /** 包级开放:SchemaInitializer 的建表序列中原位调用(run() 内联种子点)。 */
    void addDict(String type, String[] labels) {
        for (int i = 0; i < labels.length; i++) {
            // 唯一约束按 parent_id 区分后,顶级种子仅在"该类型下任何层级都没有此名称"时才插入,
            // 避免每次启动为已挂接为子项的同名项重复造顶级孤儿行
            jdbc.update("INSERT INTO sys_dict(dict_type, dict_label, sort, enabled, remark) " +
                            "SELECT ?,?,?,1,'' WHERE NOT EXISTS" +
                            "(SELECT 1 FROM sys_dict WHERE dict_type=? AND dict_label=?)",
                    type, labels[i], i, type, labels[i]);
        }
    }

    /** 在 parentType+parentLabel 定位的字典项下挂子项(级联下级) */
    private void addDictChildren(String parentType, String parentLabel, String type, String[] labels) {
        Long pid;
        try {
            pid = jdbc.queryForObject("SELECT id FROM sys_dict WHERE dict_type=? AND dict_label=?",
                    Long.class, parentType, parentLabel);
        } catch (Exception e) {
            return;
        }
        if (pid == null) {
            return;
        }
        for (int i = 0; i < labels.length; i++) {
            jdbc.update("INSERT OR IGNORE INTO sys_dict(dict_type, dict_label, sort, enabled, remark, parent_id) " +
                            "VALUES(?,?,?,1,'',?)",
                    type, labels[i], i, pid);
        }
    }

    /** 同 addDictChildren,且会把已存在但尚未挂接(parent_id=0)的同名项补挂到父项下 */
    private void attachDictChildren(String parentType, String parentLabel, String type, String[] labels) {
        addDictChildren(parentType, parentLabel, type, labels);
        Long pid;
        try {
            pid = jdbc.queryForObject("SELECT id FROM sys_dict WHERE dict_type=? AND dict_label=?",
                    Long.class, parentType, parentLabel);
        } catch (Exception e) {
            return;
        }
        if (pid == null) {
            return;
        }
        for (String label : labels) {
            // 已有挂接子项时,残留的同名顶级孤儿行直接删除(否则改父级会撞唯一约束)
            jdbc.update("DELETE FROM sys_dict WHERE dict_type=? AND dict_label=? AND parent_id=0 " +
                            "AND EXISTS(SELECT 1 FROM sys_dict WHERE dict_type=? AND dict_label=? AND parent_id=?)",
                    type, label, type, label, pid);
            jdbc.update("UPDATE sys_dict SET parent_id=? WHERE dict_type=? AND dict_label=? AND parent_id=0 " +
                            "AND NOT EXISTS(SELECT 1 FROM sys_dict WHERE dict_type=? AND dict_label=? AND parent_id=?)",
                    pid, type, label, type, label, pid);
        }
    }

    /** 一次性压缩历史字典排序空号；之后由字典删除/拖动逻辑维持连续序号。 */
    void normalizeDictSortsOnce() {
        Long done = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_setting WHERE skey='dict_sort_compact_v1'", Long.class);
        if (done != null && done > 0) return;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,dict_type,parent_id FROM sys_dict " +
                        "ORDER BY dict_type,COALESCE(parent_id,0),sort,id");
        String group = null;
        int position = 1;
        for (Map<String, Object> row : rows) {
            String key = String.valueOf(row.get("dict_type")) + ":" +
                    (row.get("parent_id") == null ? "0" : String.valueOf(row.get("parent_id")));
            if (!key.equals(group)) {
                group = key;
                position = 1;
            }
            jdbc.update("UPDATE sys_dict SET sort=? WHERE id=?", position, row.get("id"));
            position++;
        }
        jdbc.update("INSERT OR IGNORE INTO sys_setting(skey,svalue) VALUES('dict_sort_compact_v1','1')");
        log.info("迁移:已压缩字典排序为连续序号");
    }

    /** 学历选项细化：删除“高中及以下”；如历史人员仍在使用则停用保留，避免清空档案。 */
    void migrateEducationOptionsOnce() {
        Long done = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_setting WHERE skey='education_options_v1'", Long.class);
        if (done != null && done > 0) return;
        Long used = jdbc.queryForObject(
                "SELECT COUNT(*) FROM personnel WHERE education='高中及以下'", Long.class);
        if (used == null || used == 0) {
            jdbc.update("DELETE FROM sys_dict WHERE dict_type='education' AND dict_label='高中及以下'");
        } else {
            jdbc.update("UPDATE sys_dict SET enabled=0 WHERE dict_type='education' AND dict_label='高中及以下'");
            log.warn("学历选项已更新，{} 条历史人员仍使用“高中及以下”，该旧选项已停用并保留历史值", used);
        }
        String[] labels = {"博士", "硕士", "本科", "大专", "职高", "中专", "初中", "小学", "其他"};
        for (int i = 0; i < labels.length; i++) {
            jdbc.update("UPDATE sys_dict SET sort=? WHERE dict_type='education' AND dict_label=?",
                    i + 1, labels[i]);
        }
        jdbc.update("INSERT OR IGNORE INTO sys_setting(skey,svalue) VALUES('education_options_v1','1')");
        log.info("迁移:学历选项已更新");
    }

    /**
     * sys_dict 唯一约束迁移:(type,label) → (type,label,parent_id)。
     * 区县级地名存在跨市同名(鼓楼区/新华区等),必须按父项区分。
     * SQLite 无法直接删约束,检测到旧约束时整表重建(保留 id 与 parent_id)。
     */
    void migrateDictUnique() {
        org.springframework.transaction.support.TransactionTemplate tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        try {
            tx.executeWithoutResult(status -> {
            boolean oldStyle = false;
            List<Map<String, Object>> idx = jdbc.queryForList("PRAGMA index_list(sys_dict)");
            for (Map<String, Object> ix : idx) {
                if (!"1".equals(String.valueOf(ix.get("unique")))) { continue; }
                List<String> cols = new java.util.ArrayList<String>();
                for (Map<String, Object> c : jdbc.queryForList("PRAGMA index_info(" + ix.get("name") + ")")) {
                    cols.add(String.valueOf(c.get("name")));
                }
                java.util.Collections.sort(cols);
                if ("dict_label,dict_type".equals(String.join(",", cols))) {
                    oldStyle = true; // 唯一索引只含 type+label,不含 parent_id
                }
            }
            if (!oldStyle) { return; }
            log.info("迁移:sys_dict 唯一约束升级为 (dict_type, dict_label, parent_id)");
            jdbc.execute("CREATE TABLE sys_dict_mig (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "dict_type TEXT NOT NULL," +
                    "dict_label TEXT NOT NULL," +
                    "sort INTEGER DEFAULT 0," +
                    "enabled INTEGER DEFAULT 1," +
                    "remark TEXT DEFAULT ''," +
                    "parent_id INTEGER DEFAULT 0," +
                    "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                    "updated_at TEXT DEFAULT (datetime('now','localtime'))," +
                    "UNIQUE(dict_type, dict_label, parent_id)" +
                    ")");
            jdbc.update("INSERT INTO sys_dict_mig(id, dict_type, dict_label, sort, enabled, remark, parent_id, created_at, updated_at) " +
                    "SELECT id, dict_type, dict_label, sort, enabled, remark, parent_id, created_at, updated_at FROM sys_dict");
            jdbc.execute("DROP TABLE sys_dict");
            jdbc.execute("ALTER TABLE sys_dict_mig RENAME TO sys_dict");
            });
        } catch (Exception e) {
            throw new IllegalStateException("字典结构升级失败，已回滚，请检查日志后重试", e);
        }
    }

    /**
     * 省市区三级行政区划 → 数据字典:
     * region_province(省) / region_city(市,挂省) / region_district(区县,挂市)。
     * 数据来自 resources/region/regions.tsv(民政部行政区划整理,直辖市合并为 省/省/区)。
     */
    void seedRegionDict() {
        try {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_dict WHERE dict_type='region_district'", Integer.class);
            if (cnt != null && cnt > 2000) { return; } // 已初始化过

            java.io.InputStream in = getClass().getResourceAsStream("/region/regions.tsv");
            if (in == null) { log.warn("行政区划资源 region/regions.tsv 不存在,跳过"); return; }
            java.util.List<String[]> rows = new java.util.ArrayList<String[]>();
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) { continue; }
                String[] seg = line.split("\t");
                if (seg.length >= 3) { rows.add(new String[]{seg[0], seg[1], seg[2]}); }
            }
            br.close();
            if (rows.isEmpty()) { return; }

            // 去重保序:省、市
            java.util.LinkedHashSet<String> provs = new java.util.LinkedHashSet<String>();
            java.util.LinkedHashSet<String> cityKeys = new java.util.LinkedHashSet<String>();
            for (String[] r : rows) {
                provs.add(r[0]);
                cityKeys.add(r[0] + "\t" + r[1]);
            }
            // 1) 省
            List<Object[]> provBatch = new java.util.ArrayList<Object[]>();
            int i = 0;
            for (String p : provs) { provBatch.add(new Object[]{"region_province", p, i++}); }
            jdbc.batchUpdate("INSERT OR IGNORE INTO sys_dict(dict_type, dict_label, sort, enabled, remark, parent_id) VALUES(?,?,?,1,'',0)", provBatch);
            // 2) 市(挂省)
            java.util.Map<String, Long> provIds = new java.util.HashMap<String, Long>();
            for (Map<String, Object> m : jdbc.queryForList("SELECT id, dict_label FROM sys_dict WHERE dict_type='region_province'")) {
                provIds.put(String.valueOf(m.get("dict_label")), ((Number) m.get("id")).longValue());
            }
            List<Object[]> cityBatch = new java.util.ArrayList<Object[]>();
            java.util.Map<String, Long> cityIds = new java.util.HashMap<String, Long>();
            for (String ck : cityKeys) {
                int tab = ck.indexOf('\t');
                String p = ck.substring(0, tab), c = ck.substring(tab + 1);
                Long pid = provIds.get(p);
                if (pid == null) { continue; }
                cityBatch.add(new Object[]{"region_city", c, cityBatch.size(), pid});
                cityIds.put(ck, pid); // 先占位,插入后回填真实 id
            }
            jdbc.batchUpdate("INSERT OR IGNORE INTO sys_dict(dict_type, dict_label, sort, enabled, remark, parent_id) VALUES(?,?,?,1,'',?)", cityBatch);
            for (Map<String, Object> m : jdbc.queryForList("SELECT id, dict_label, parent_id FROM sys_dict WHERE dict_type='region_city'")) {
                Long pid = ((Number) m.get("parent_id")).longValue();
                for (java.util.Map.Entry<String, Long> e : cityIds.entrySet()) {
                    if (e.getValue().equals(pid) && e.getKey().endsWith("\t" + String.valueOf(m.get("dict_label")))) {
                        cityIds.put(e.getKey(), ((Number) m.get("id")).longValue());
                        break;
                    }
                }
            }
            // 3) 区县(挂市)
            List<Object[]> distBatch = new java.util.ArrayList<Object[]>();
            java.util.Map<String, Integer> distSort = new java.util.HashMap<String, Integer>();
            for (String[] r : rows) {
                Long cid = cityIds.get(r[0] + "\t" + r[1]);
                if (cid == null) { continue; }
                String key = r[0] + "\t" + r[1] + "\t" + r[2];
                int s = distSort.containsKey(key) ? distSort.get(key) : distBatch.size();
                distSort.put(key, s);
                distBatch.add(new Object[]{"region_district", r[2], s, cid});
            }
            jdbc.batchUpdate("INSERT OR IGNORE INTO sys_dict(dict_type, dict_label, sort, enabled, remark, parent_id) VALUES(?,?,?,1,'',?)", distBatch);
            log.info("已初始化行政区划字典:省 {} / 市 {} / 区县 {}", provs.size(), cityKeys.size(), distBatch.size());
        } catch (Exception e) {
            log.warn("行政区划字典初始化失败:{}", e.getMessage());
        }
    }

    /**
     * 直辖市城市级改为"直辖市"(北京市/直辖市/东城区,而非重复省名)。
     * 字典标签与人员已有数据(籍贯/入伍地)同步替换,幂等可重复执行。
     */
    void migrateMunicipalityCity() {
        try {
            String[] munis = {"北京市", "天津市", "上海市", "重庆市"};
            // 1) 字典:直辖市的城市级项改名为"直辖市"
            int n = jdbc.update("UPDATE sys_dict SET dict_label='直辖市' WHERE dict_type='region_city' " +
                    "AND parent_id IN (SELECT id FROM sys_dict WHERE dict_type='region_province' " +
                    "AND dict_label IN ('北京市','天津市','上海市','重庆市')) " +
                    "AND dict_label IN ('北京市','天津市','上海市','重庆市')");
            // 2) 人员数据:"北京市/北京市/区县" → "北京市/直辖市/区县"
            int rows = 0;
            for (String m : munis) {
                rows += jdbc.update("UPDATE personnel SET native_place = ? WHERE native_place = ?",
                        m + "/直辖市", m + "/" + m);
                rows += jdbc.update("UPDATE personnel SET enlist_place = ? WHERE enlist_place = ?",
                        m + "/直辖市", m + "/" + m);
                // 前缀"省/省/"共 2*len+2 字符,SUBSTR 从其下一字符开始
                rows += jdbc.update("UPDATE personnel SET native_place = ? || SUBSTR(native_place, ?) WHERE native_place LIKE ?",
                        m + "/直辖市/", 2 * m.length() + 3, m + "/" + m + "/%");
                rows += jdbc.update("UPDATE personnel SET enlist_place = ? || SUBSTR(enlist_place, ?) WHERE enlist_place LIKE ?",
                        m + "/直辖市/", 2 * m.length() + 3, m + "/" + m + "/%");
            }
            // 历史脏数据修复(曾产生"直辖市//区县"双斜杠)
            rows += jdbc.update("UPDATE personnel SET native_place = REPLACE(native_place, '/直辖市//', '/直辖市/')");
            rows += jdbc.update("UPDATE personnel SET enlist_place = REPLACE(enlist_place, '/直辖市//', '/直辖市/')");
            if (n > 0 || rows > 0) {
                log.info("迁移:直辖市城市级改为\"直辖市\",字典 {} 项,人员数据 {} 行", n, rows);
            }
        } catch (Exception e) {
            log.warn("直辖市城市级迁移失败:{}", e.getMessage());
        }
    }
}
