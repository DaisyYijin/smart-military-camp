package com.szjx.personnel.server.config;

import com.szjx.personnel.server.dao.AdminUserDao;
import com.szjx.personnel.server.service.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 数据库初始化:建表 + 默认管理员
 */
@Component
public class SchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final AdminUserDao adminUserDao;

    public SchemaInitializer(org.springframework.jdbc.core.JdbcTemplate jdbc, AdminUserDao adminUserDao) {
        this.jdbc = jdbc;
        this.adminUserDao = adminUserDao;
    }

    /** 旧库迁移:若表缺少某列则补充 */
    private void ensureColumn(String table, String column, String ddl) {
        try {
            List<Map<String, Object>> cols = jdbc.queryForList("PRAGMA table_info(" + table + ")");
            for (Map<String, Object> col : cols) {
                if (column.equals(String.valueOf(col.get("name")))) {
                    return;
                }
            }
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + ddl);
            log.info("迁移:表 {} 新增列 {}", table, column);
        } catch (Exception e) {
            log.warn("迁移列 {} 失败:{}", column, e.getMessage());
        }
    }

    /** 启动时确保各类默认字典存在(INSERT OR IGNORE 幂等,不覆盖已有自定义项) */
    private void initDefaultDict() {
        addDict("leave_type", new String[]{"事假", "病假", "年假", "探亲假", "婚假", "产假", "陪产假", "其他"});
        addDict("holiday_type", new String[]{"元旦", "春节", "清明节", "劳动节", "端午节", "中秋节", "国庆节", "其他"});
        addDict("fitness_item", new String[]{"3000米跑", "5000米跑", "100米跑", "引体向上", "俯卧撑", "仰卧起坐", "立定跳远", "BMI", "其他"});
        addDict("fitness_grade", new String[]{"优秀", "良好", "及格", "不及格"});
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
        addDict("education", new String[]{"博士", "硕士", "本科", "大专", "高中及以下", "其他"});
        addDict("only_child", new String[]{"否", "是"});
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

    private void addDict(String type, String[] labels) {
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

    @Override
    public void run(String... args) {
        // 所有字典种子/清理之前建表;旧库先升级 parent_id 和唯一约束。
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_dict (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,dict_type TEXT NOT NULL,dict_label TEXT NOT NULL," +
                "sort INTEGER DEFAULT 0,enabled INTEGER DEFAULT 1,remark TEXT DEFAULT ''," +
                "parent_id INTEGER DEFAULT 0,created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime')),UNIQUE(dict_type,dict_label,parent_id))");
        ensureColumn("sys_dict", "parent_id", "parent_id INTEGER DEFAULT 0");
        migrateDictUnique();
        jdbc.execute("CREATE TABLE IF NOT EXISTS department (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "parent_id INTEGER DEFAULT 0," +
                "sort INTEGER DEFAULT 0," +
                "remark TEXT DEFAULT ''" +
                ")");
        ensureColumn("department", "leader_id", "leader_id INTEGER DEFAULT 0");
        // 手机设备管理(手机/手表/手环)
        jdbc.execute("CREATE TABLE IF NOT EXISTS phone_device (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL DEFAULT 0," +
                "device_type TEXT DEFAULT ''," +
                "brand TEXT DEFAULT ''," +
                "model TEXT DEFAULT ''," +
                "serial_no TEXT DEFAULT ''," +
                "imei1 TEXT DEFAULT ''," +
                "imei2 TEXT DEFAULT ''," +
                "phone_number TEXT DEFAULT ''," +
                "purchase_date TEXT DEFAULT ''," +
                "status TEXT DEFAULT '在用'," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_phone_personnel ON phone_device(personnel_id)");
        addDict("device_type", new String[]{"军用手机", "地方手机", "智能手表", "智能手环", "其他终端"});
        addDict("phone_brand", new String[]{"华为", "小米", "OPPO", "vivo", "荣耀", "苹果", "三星"});
        addDict("device_status", new String[]{"在用", "停用", "维修", "报废"});

        // 休假计划模块已下线:清理旧数据(历史备份中仍可找回)
        jdbc.execute("DROP TABLE IF EXISTS leave_plan");
        jdbc.update("DELETE FROM sys_dict WHERE dict_type = 'leave_plan_status'");

        jdbc.execute("CREATE TABLE IF NOT EXISTS personnel (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "gender TEXT DEFAULT ''," +
                "id_card TEXT DEFAULT ''," +
                "birthday TEXT DEFAULT ''," +
                "phone TEXT DEFAULT ''," +
                "department_id INTEGER DEFAULT 0," +
                "rank TEXT DEFAULT ''," +
                "post TEXT DEFAULT ''," +
                "status TEXT DEFAULT '在职'," +
                "entry_date TEXT DEFAULT ''," +
                "address TEXT DEFAULT ''," +
                "photo TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "blood_type TEXT DEFAULT ''," +
                "nation TEXT DEFAULT ''," +
                "political_status TEXT DEFAULT ''," +
                "party_join_date TEXT DEFAULT ''," +
                "marital_status TEXT DEFAULT ''," +
                "education TEXT DEFAULT ''," +
                "only_child TEXT DEFAULT ''," +
                "native_place TEXT DEFAULT ''," +
                "spouse_name TEXT DEFAULT ''," +
                "spouse_work TEXT DEFAULT ''," +
                "spouse_phone TEXT DEFAULT ''," +
                "spouse_id_card TEXT DEFAULT ''," +
                "spouse_job TEXT DEFAULT ''," +
                "marriage_date TEXT DEFAULT ''," +
                "height REAL," +
                "weight REAL," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        // 旧库迁移:补充新增列
        ensureColumn("personnel", "blood_type", "blood_type TEXT DEFAULT ''");
        ensureColumn("personnel", "nation", "nation TEXT DEFAULT ''");
        ensureColumn("personnel", "political_status", "political_status TEXT DEFAULT ''");
        ensureColumn("personnel", "party_join_date", "party_join_date TEXT DEFAULT ''");
        ensureColumn("personnel", "marital_status", "marital_status TEXT DEFAULT ''");
        ensureColumn("personnel", "education", "education TEXT DEFAULT ''");
        ensureColumn("personnel", "only_child", "only_child TEXT DEFAULT ''");
        ensureColumn("personnel", "native_place", "native_place TEXT DEFAULT ''");
        ensureColumn("personnel", "enlist_place", "enlist_place TEXT DEFAULT ''");
        ensureColumn("personnel", "spouse_name", "spouse_name TEXT DEFAULT ''");
        ensureColumn("personnel", "spouse_work", "spouse_work TEXT DEFAULT ''");
        ensureColumn("personnel", "spouse_phone", "spouse_phone TEXT DEFAULT ''");
        ensureColumn("personnel", "spouse_id_card", "spouse_id_card TEXT DEFAULT ''");
        ensureColumn("personnel", "spouse_job", "spouse_job TEXT DEFAULT ''");
        ensureColumn("personnel", "marriage_date", "marriage_date TEXT DEFAULT ''");
        ensureColumn("personnel", "height", "height REAL");
        ensureColumn("personnel", "weight", "weight REAL");
        ensureColumn("personnel", "auto_leave", "auto_leave INTEGER DEFAULT 0");
        ensureColumn("personnel", "auto_leave_previous_status", "auto_leave_previous_status TEXT DEFAULT ''");
        ensureColumn("personnel", "location_point", "location_point TEXT DEFAULT ''");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_personnel_dept ON personnel(department_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_personnel_name ON personnel(name)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_personnel_idcard ON personnel(id_card)");
        // 人员删除撤回:删除前打包人员及全部级联数据的快照
        jdbc.execute("CREATE TABLE IF NOT EXISTS delete_undo (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL," +
                "name TEXT DEFAULT ''," +
                "summary TEXT DEFAULT ''," +
                "payload TEXT NOT NULL," +
                "photo_trash TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");

        jdbc.execute("CREATE TABLE IF NOT EXISTS leave_record (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER DEFAULT 0," +
                "leave_type TEXT DEFAULT ''," +
                "start_date TEXT DEFAULT ''," +
                "end_date TEXT DEFAULT ''," +
                "days REAL DEFAULT 0," +
                "reason TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_leave_personnel ON leave_record(personnel_id)");

        // 休假计划(年度安排,与实际休假记录互补)
        jdbc.execute("CREATE TABLE IF NOT EXISTS leave_plan (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL," +
                "year TEXT DEFAULT ''," +
                "leave_type TEXT DEFAULT ''," +
                "start_date TEXT DEFAULT ''," +
                "end_date TEXT DEFAULT ''," +
                "days REAL DEFAULT 0," +
                "status TEXT DEFAULT '计划中'," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_leave_plan_personnel ON leave_plan(personnel_id)");


        jdbc.execute("CREATE TABLE IF NOT EXISTS fitness_record (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER DEFAULT 0," +
                "item TEXT DEFAULT ''," +
                "score TEXT DEFAULT ''," +
                "grade TEXT DEFAULT ''," +
                "test_date TEXT DEFAULT ''," +
                "tester TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_fitness_personnel ON fitness_record(personnel_id)");

        // 军事考核(理论/射击/战术等,按分数评定)
        jdbc.execute("CREATE TABLE IF NOT EXISTS military_assessment (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER DEFAULT 0," +
                "category TEXT DEFAULT ''," +
                "item TEXT DEFAULT ''," +
                "score TEXT DEFAULT ''," +
                "points REAL DEFAULT 0," +
                "grade TEXT DEFAULT ''," +
                "test_date TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        ensureColumn("military_assessment", "scheme_id", "scheme_id INTEGER DEFAULT 0");
        ensureColumn("military_assessment", "detail", "detail TEXT DEFAULT ''");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_military_personnel ON military_assessment(personnel_id)");

        // 军事考核方案:两级课目+占比(顶级课目占总分百分比;子项占该课目百分比)
        jdbc.execute("CREATE TABLE IF NOT EXISTS mil_scheme (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mil_scheme_item (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "scheme_id INTEGER NOT NULL," +
                "parent_id INTEGER DEFAULT 0," +
                "name TEXT DEFAULT ''," +
                "weight REAL DEFAULT 0," +
                "sort INTEGER DEFAULT 0," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        // 默认方案种子:军事课目综合考核 = 理论30% + 实操70%(射击40%/战术30%/体能30%)
        try {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM mil_scheme", Integer.class) == 0) {
                jdbc.update("INSERT INTO mil_scheme(name, remark) VALUES(?, ?)",
                        "军事课目综合考核", "理论+实操加权,百分制");
                Long sid = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
                jdbc.update("INSERT INTO mil_scheme_item(scheme_id, parent_id, name, weight, sort) VALUES(?,0,?,?,1)", sid, "理论", 30);
                jdbc.update("INSERT INTO mil_scheme_item(scheme_id, parent_id, name, weight, sort) VALUES(?,0,?,?,2)", sid, "实操", 70);
                Long prac = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
                String[] names = {"射击", "战术", "体能"};
                double[] ws = {40, 30, 30};
                for (int i = 0; i < names.length; i++) {
                    jdbc.update("INSERT INTO mil_scheme_item(scheme_id, parent_id, name, weight, sort) VALUES(?,?,?,?,?)",
                            sid, prac, names[i], ws[i], i + 1);
                }
            }
        } catch (Exception ignore) {
            // 种子失败不影响启动
        }
        addDict("military_type", new String[]{"理论考核", "射击考核", "战术考核", "共同科目", "专业科目", "其他"});
        addDict("personnel_location", new String[]{"机关", "营区", "哨位", "执勤点", "外训点", "其他"});
        addDict("equipment_category", new String[]{"枪支", "大型装备", "小型装备"});
        addDict("edu_degree", new String[]{"博士", "硕士", "学士", "无"});
        addDict("edu_form", new String[]{"全日制", "非全日制"});
        addDict("leave_plan_status", new String[]{"计划中", "已执行", "已取消"});

        // 体能考核分数
        try { jdbc.execute("ALTER TABLE fitness_record ADD COLUMN points REAL DEFAULT 0"); } catch (Exception ignore) {}

        // 通用设置表(如体能评定标准)
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_setting (" +
                "skey TEXT PRIMARY KEY," +
                "svalue TEXT DEFAULT '')");
        jdbc.update("INSERT OR IGNORE INTO sys_setting(skey, svalue) VALUES('fitness_grade_rule', ?)",
                "[{\"label\":\"优秀\",\"op\":\">=\",\"min\":90},{\"label\":\"良好\",\"op\":\">=\",\"min\":80},{\"label\":\"及格\",\"op\":\">=\",\"min\":60}]");

        // 老数据迁移:grade 存数字分数的 → 挪到 points,并按默认阈值换算成评定
        jdbc.update("UPDATE fitness_record SET points = CAST(TRIM(grade) AS REAL) " +
                "WHERE (points IS NULL OR points = 0) AND TRIM(grade) GLOB '[0-9]*'");
        jdbc.update("UPDATE fitness_record SET grade = CASE " +
                "WHEN points >= 90 THEN '优秀' WHEN points >= 80 THEN '良好' " +
                "WHEN points >= 60 THEN '及格' ELSE '不及格' END " +
                "WHERE TRIM(grade) GLOB '[0-9]*'");

        jdbc.execute("CREATE TABLE IF NOT EXISTS admin_user (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT NOT NULL UNIQUE," +
                "password_hash TEXT NOT NULL," +
                "display_name TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");

        // 任职经历(人员的子记录)
        jdbc.execute("CREATE TABLE IF NOT EXISTS position_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL," +
                "start_date TEXT DEFAULT ''," +
                "end_date TEXT DEFAULT ''," +
                "place TEXT DEFAULT ''," +
                "post TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_position_history_person ON position_history(personnel_id)");

        // 学历情况(人员直属字段,直接填写)
        ensureColumn("personnel", "edu_enroll_date", "edu_enroll_date TEXT DEFAULT ''");
        ensureColumn("personnel", "edu_graduate_date", "edu_graduate_date TEXT DEFAULT ''");
        ensureColumn("personnel", "edu_school", "edu_school TEXT DEFAULT ''");
        ensureColumn("personnel", "edu_major", "edu_major TEXT DEFAULT ''");
        ensureColumn("personnel", "edu_degree", "edu_degree TEXT DEFAULT ''");
        ensureColumn("personnel", "edu_form", "edu_form TEXT DEFAULT ''");
        ensureColumn("personnel", "edu_cert_no", "edu_cert_no TEXT DEFAULT ''");

        // 奖惩情况(人员的子记录,kind 区分 奖励/处分)
        jdbc.execute("CREATE TABLE IF NOT EXISTS reward_punish (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL," +
                "kind TEXT DEFAULT ''," +
                "occur_date TEXT DEFAULT ''," +
                "reason TEXT DEFAULT ''," +
                "content TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_reward_punish_person ON reward_punish(personnel_id)");

        // 家庭成员(统一:配偶/子女/父母/岳父母等)
        jdbc.execute("CREATE TABLE IF NOT EXISTS family_member (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL," +
                "relationship TEXT DEFAULT ''," +
                "name TEXT DEFAULT ''," +
                "id_card TEXT DEFAULT ''," +
                "gender TEXT DEFAULT ''," +
                "birth_date TEXT DEFAULT ''," +
                "political_status TEXT DEFAULT ''," +
                "work_info TEXT DEFAULT ''," +
                "phone TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_family_member_person ON family_member(personnel_id)");

        // 子女信息(人员的子记录)
        jdbc.execute("CREATE TABLE IF NOT EXISTS child_info (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL," +
                "name TEXT DEFAULT ''," +
                "id_card TEXT DEFAULT ''," +
                "gender TEXT DEFAULT ''," +
                "birth_date TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_child_info_person ON child_info(personnel_id)");
        // 老库升级:补充 id_card 列(新库已含)
        try {
            jdbc.execute("ALTER TABLE child_info ADD COLUMN id_card TEXT DEFAULT ''");
        } catch (Exception ignore) {
            // 列已存在则跳过
        }

        // 人员表补充列:党团籍与婚姻扩展字段(老库自动升级)
        String[] extraCols = {"party_org_location", "party_post", "party_remark",
                "divorce_date", "marriage_remark",
                "party_apply_date", "party_activist_date", "party_candidate_date", "party_full_date",
                "follow_army_date", "follow_army_status"};
        for (String col : extraCols) {
            try {
                jdbc.execute("ALTER TABLE personnel ADD COLUMN " + col + " TEXT DEFAULT ''");
            } catch (Exception ignore) {
                // 列已存在
            }
        }


        // 驾驶证(军本/地方本)
        jdbc.execute("CREATE TABLE IF NOT EXISTS driver_license (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL," +
                "license_type TEXT DEFAULT ''," +
                "license_no TEXT DEFAULT ''," +
                "vehicle_class TEXT DEFAULT ''," +
                "issue_date TEXT DEFAULT ''," +
                "expiry_date TEXT DEFAULT ''," +
                "issuing_auth TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");

        // 个人卡号(保障卡/银行卡等)
        jdbc.execute("CREATE TABLE IF NOT EXISTS personal_card (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL," +
                "card_type TEXT DEFAULT ''," +
                "card_no TEXT DEFAULT ''," +
                "bank_name TEXT DEFAULT ''," +
                "branch_name TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");

        // 私家车
        jdbc.execute("CREATE TABLE IF NOT EXISTS private_vehicle (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL," +
                "plate_no TEXT DEFAULT ''," +
                "brand TEXT DEFAULT ''," +
                "purchase_date TEXT DEFAULT ''," +
                "owner_name TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");

        // 住房情况
        jdbc.execute("CREATE TABLE IF NOT EXISTS housing_info (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL," +
                "housing_type TEXT DEFAULT ''," +
                "address TEXT DEFAULT ''," +
                "area TEXT DEFAULT ''," +
                "purchase_date TEXT DEFAULT ''," +
                "owner_name TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");

        // 子表补充列
        String[][] subExtra = {
            {"personal_card", "open_date"},
            {"personal_card", "card_status"},
            {"private_vehicle", "color"},
            {"private_vehicle", "price"},
            {"private_vehicle", "insurance_date"},
            {"private_vehicle", "vehicle_status"},
            {"housing_info", "house_nature"},
            {"housing_info", "has_cert"},
            {"housing_info", "loan_status"},
            {"driver_license", "license_status"},
            {"driver_license", "review_date"},
        };
        for (String[] se : subExtra) {
            try {
                jdbc.execute("ALTER TABLE " + se[0] + " ADD COLUMN " + se[1] + " TEXT DEFAULT ''");
            } catch (Exception ignore) { }
        }

        // 体能评分矩阵:项目 x 年龄段 x 分数 → 达标值(用户可自行维护)
        jdbc.execute("CREATE TABLE IF NOT EXISTS fitness_matrix (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "item TEXT NOT NULL," +
                "age_group TEXT NOT NULL," +
                "score INTEGER NOT NULL," +
                "value REAL DEFAULT 0," +
                "unit TEXT DEFAULT 'count'," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        ensureColumn("fitness_matrix", "unit", "unit TEXT DEFAULT 'count'");
        // 种子:引体向上示例(用户可自行调整)
        Long fmCount = jdbc.queryForObject("SELECT COUNT(*) FROM fitness_matrix", Long.class);
        if (fmCount == null || fmCount == 0) {
            Object[][] seed = {
                {"100", 30, 28, 26, 23, 20, 17},
                {"95", 27, 25, 23, 20, 17, 15},
                {"90", 24, 22, 20, 17, 14, 13},
                {"85", 21, 19, 17, 15, 12, 11},
                {"80", 18, 16, 15, 13, 11, 10},
                {"75", 15, 14, 13, 11, 10, 9},
                {"70", 14, 13, 11, 10, 9, 8},
                {"65", 12, 11, 10, 9, 8, 7},
                {"60", 11, 10, 9, 8, 7, 6},
                {"55", 10, 9, 8, 7, 6, 5},
            };
            String[] ages = {"24岁以下", "25-27", "28-30", "31-33", "34-36", "37-39"};
            for (Object[] row : seed) {
                for (int a = 0; a < ages.length; a++) {
                    jdbc.update("INSERT INTO fitness_matrix(item, age_group, score, value) VALUES(?,?,?,?)",
                            "引体向上", ages[a], Integer.parseInt((String) row[0]), ((Number) row[a + 1]).doubleValue());
                }
            }
            log.info("已初始化体能评分矩阵示例(引体向上)");
        }


        // 休假扩展:年度/路途/节假日
        String[] leaveCols = {"year", "travel_days", "holiday_days", "holiday_name"};
        for (String col : leaveCols) {
            try { jdbc.execute("ALTER TABLE leave_record ADD COLUMN " + col + " TEXT DEFAULT ''"); } catch (Exception ignore) {}
        }

        // 节假日日历
        jdbc.execute("CREATE TABLE IF NOT EXISTS holiday_calendar (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "holiday_date TEXT NOT NULL," +
                "holiday_name TEXT DEFAULT ''" +
                ")");
        Long hCount = jdbc.queryForObject("SELECT COUNT(*) FROM holiday_calendar", Long.class);
        if (hCount == null || hCount == 0) {
            String[][] holidays = {
                {"2026-01-01", "元旦"}, {"2026-01-02", "元旦"}, {"2026-01-03", "元旦"},
                {"2026-02-16", "春节"}, {"2026-02-17", "春节"}, {"2026-02-18", "春节"},
                {"2026-02-19", "春节"}, {"2026-02-20", "春节"}, {"2026-02-21", "春节"}, {"2026-02-22", "春节"},
                {"2026-04-04", "清明"}, {"2026-04-05", "清明"}, {"2026-04-06", "清明"},
                {"2026-05-01", "劳动节"}, {"2026-05-02", "劳动节"}, {"2026-05-03", "劳动节"},
                {"2026-05-04", "劳动节"}, {"2026-05-05", "劳动节"},
                {"2026-06-19", "端午"}, {"2026-06-20", "端午"}, {"2026-06-21", "端午"},
                {"2026-09-25", "中秋"}, {"2026-09-26", "中秋"}, {"2026-09-27", "中秋"},
                {"2026-10-01", "国庆"}, {"2026-10-02", "国庆"}, {"2026-10-03", "国庆"},
                {"2026-10-04", "国庆"}, {"2026-10-05", "国庆"}, {"2026-10-06", "国庆"}, {"2026-10-07", "国庆"},
                {"2027-01-01", "元旦"},
                {"2027-02-06", "春节"}, {"2027-02-07", "春节"}, {"2027-02-08", "春节"},
                {"2027-02-09", "春节"}, {"2027-02-10", "春节"}, {"2027-02-11", "春节"}, {"2027-02-12", "春节"},
            };
            for (String[] h : holidays) {
                jdbc.execute("INSERT INTO holiday_calendar(holiday_date, holiday_name) VALUES('" + h[0] + "','" + h[1] + "')");
            }
            log.info("已初始化节假日日历 {} 天", holidays.length);
        }

        // 操作日志:谁、何时、从哪个 IP、做了什么
        jdbc.execute("CREATE TABLE IF NOT EXISTS op_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT DEFAULT ''," +
                "action TEXT DEFAULT ''," +
                "detail TEXT DEFAULT ''," +
                "ip TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_oplog_time ON op_log(created_at)");

        // 人员类别(军衔按类别级联挂接在 initDefaultDict 中执行)
        try { jdbc.execute("ALTER TABLE personnel ADD COLUMN category TEXT DEFAULT ''"); } catch (Exception ignore) {}

        jdbc.execute("CREATE TABLE IF NOT EXISTS equipment (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "code TEXT DEFAULT ''," +
                "category TEXT DEFAULT ''," +
                "model TEXT DEFAULT ''," +
                "status TEXT DEFAULT ''," +
                "keeper_id INTEGER DEFAULT 0," +
                "location TEXT DEFAULT ''," +
                "buy_date TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_equipment_keeper ON equipment(keeper_id)");
        // 装备时间(列装/配发时间,区别于购置日期)
        ensureColumn("equipment", "equip_date", "equip_date TEXT DEFAULT ''");

        jdbc.execute("CREATE TABLE IF NOT EXISTS material (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "category TEXT DEFAULT ''," +
                "spec TEXT DEFAULT ''," +
                "unit TEXT DEFAULT ''," +
                "quantity REAL DEFAULT 0," +
                "min_quantity REAL DEFAULT 0," +
                "keeper_id INTEGER DEFAULT 0," +
                "location TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_material_keeper ON material(keeper_id)");

        // 物资详细存放点
        try { jdbc.execute("ALTER TABLE material ADD COLUMN detail_location TEXT DEFAULT ''"); } catch (Exception ignore) {}
        // 已用的存放位置收进字典,老数据下拉仍可选
        jdbc.update("INSERT OR IGNORE INTO sys_dict(dict_type, dict_label, sort, enabled, remark, parent_id) " +
                "SELECT 'material_location', location, 90, 1, '', 0 FROM material WHERE location IS NOT NULL AND TRIM(location) <> ''");

        jdbc.execute("CREATE TABLE IF NOT EXISTS vehicle (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "plate_no TEXT NOT NULL," +
                "brand TEXT DEFAULT ''," +
                "type TEXT DEFAULT ''," +
                "status TEXT DEFAULT ''," +
                "driver_id INTEGER DEFAULT 0," +
                "seats INTEGER DEFAULT 0," +
                "mileage REAL DEFAULT 0," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_vehicle_driver ON vehicle(driver_id)");

        // 车辆扩展:车架号/发动机号/颜色/燃料/购置/保险/年检/型号
        String[] vehicleCols = {"vin", "engine_no", "color", "fuel_type", "purchase_date", "insurance_expiry", "inspection_expiry", "model"};
        for (String col : vehicleCols) {
            try { jdbc.execute("ALTER TABLE vehicle ADD COLUMN " + col + " TEXT DEFAULT ''"); } catch (Exception ignore) {}
        }
        // 装备时间(列装时间,区别于购置日期)
        ensureColumn("vehicle", "equip_date", "equip_date TEXT DEFAULT ''");

        initDefaultDict();
        seedRegionDict();
        migrateMunicipalityCity();

        if (adminUserDao.count() == 0) {
            adminUserDao.insertAdmin("admin", PasswordUtil.hash("admin123"), "系统管理员");
            log.info("已创建默认管理员账号: admin / admin123");
            System.out.println("[初始化] 已创建默认管理员账号 admin / admin123,请登录后立即修改密码!");
        }
    }

    /**
     * sys_dict 唯一约束迁移:(type,label) → (type,label,parent_id)。
     * 区县级地名存在跨市同名(鼓楼区/新华区等),必须按父项区分。
     * SQLite 无法直接删约束,检测到旧约束时整表重建(保留 id 与 parent_id)。
     */
    private void migrateDictUnique() {
        try {
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
            jdbc.update("INSERT OR IGNORE INTO sys_dict_mig(id, dict_type, dict_label, sort, enabled, remark, parent_id, created_at, updated_at) " +
                    "SELECT id, dict_type, dict_label, sort, enabled, remark, parent_id, created_at, updated_at FROM sys_dict");
            jdbc.execute("DROP TABLE sys_dict");
            jdbc.execute("ALTER TABLE sys_dict_mig RENAME TO sys_dict");
        } catch (Exception e) {
            log.warn("sys_dict 唯一约束迁移失败:{}", e.getMessage());
        }
    }

    /**
     * 省市区三级行政区划 → 数据字典:
     * region_province(省) / region_city(市,挂省) / region_district(区县,挂市)。
     * 数据来自 resources/region/regions.tsv(民政部行政区划整理,直辖市合并为 省/省/区)。
     */
    private void seedRegionDict() {
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
    private void migrateMunicipalityCity() {
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
