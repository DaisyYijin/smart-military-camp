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
        addDict("fitness_item", new String[]{"3000米跑", "5000米跑", "100米跑", "引体向上", "俯卧撑", "仰卧起坐", "立定跳远", "其他"});
        addDict("fitness_grade", new String[]{"优秀", "良好", "及格", "不及格"});
        addDict("personnel_status", new String[]{"在职", "休假", "培训", "借调", "离职", "其他"});
        addDict("rank", new String[]{"列兵", "上等兵", "下士", "中士", "二级上士", "一级上士",
                "三级军士长", "二级军士长", "一级军士长", "少尉", "中尉", "上尉", "少校", "中校", "上校", "大校", "无"});
        addDict("equipment_category", new String[]{"枪械", "防护", "通信", "侦察", "生活", "其他"});
        addDict("equipment_status", new String[]{"在用", "库存", "维修", "报废"});
        addDict("material_category", new String[]{"办公", "被装", "食品", "医药", "工具", "其他"});
        addDict("vehicle_type", new String[]{"轿车", "客车", "卡车", "越野车", "特种车", "其他"});
        addDict("vehicle_status", new String[]{"可用", "出车", "维修", "停用"});
        addDict("vehicle_fuel", new String[]{"汽油", "柴油", "电", "混合动力", "其他"});
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
            jdbc.update("INSERT OR IGNORE INTO sys_dict(dict_type, dict_label, sort, enabled, remark) VALUES(?,?,?,1,'')",
                    type, labels[i], i);
        }
    }

    @Override
    public void run(String... args) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS department (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "parent_id INTEGER DEFAULT 0," +
                "sort INTEGER DEFAULT 0," +
                "remark TEXT DEFAULT ''" +
                ")");

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
        ensureColumn("personnel", "spouse_name", "spouse_name TEXT DEFAULT ''");
        ensureColumn("personnel", "spouse_work", "spouse_work TEXT DEFAULT ''");
        ensureColumn("personnel", "spouse_phone", "spouse_phone TEXT DEFAULT ''");
        ensureColumn("personnel", "spouse_id_card", "spouse_id_card TEXT DEFAULT ''");
        ensureColumn("personnel", "spouse_job", "spouse_job TEXT DEFAULT ''");
        ensureColumn("personnel", "marriage_date", "marriage_date TEXT DEFAULT ''");
        ensureColumn("personnel", "height", "height REAL");
        ensureColumn("personnel", "weight", "weight REAL");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_personnel_dept ON personnel(department_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_personnel_name ON personnel(name)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_personnel_idcard ON personnel(id_card)");

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
        try {
            jdbc.execute("ALTER TABLE fitness_matrix ADD COLUMN unit TEXT DEFAULT ''");
        } catch (Exception ignore) { }
        jdbc.execute("CREATE TABLE IF NOT EXISTS fitness_matrix (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "item TEXT NOT NULL," +
                "age_group TEXT NOT NULL," +
                "score INTEGER NOT NULL," +
                "value REAL DEFAULT 0," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
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

        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_dict (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "dict_type TEXT NOT NULL," +
                "dict_label TEXT NOT NULL," +
                "sort INTEGER DEFAULT 0," +
                "enabled INTEGER DEFAULT 1," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))," +
                "UNIQUE(dict_type, dict_label)" +
                ")");

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

        // 车辆扩展:车架号/发动机号/颜色/燃料/购置/保险/年检
        String[] vehicleCols = {"vin", "engine_no", "color", "fuel_type", "purchase_date", "insurance_expiry", "inspection_expiry"};
        for (String col : vehicleCols) {
            try { jdbc.execute("ALTER TABLE vehicle ADD COLUMN " + col + " TEXT DEFAULT ''"); } catch (Exception ignore) {}
        }

        initDefaultDict();

        if (adminUserDao.count() == 0) {
            adminUserDao.insertAdmin("admin", PasswordUtil.hash("admin123"), "系统管理员");
            log.info("已创建默认管理员账号: admin / admin123");
            System.out.println("[初始化] 已创建默认管理员账号 admin / admin123,请登录后立即修改密码!");
        }
    }
}
