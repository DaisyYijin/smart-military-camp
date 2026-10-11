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

        initDefaultDict();

        if (adminUserDao.count() == 0) {
            adminUserDao.insertAdmin("admin", PasswordUtil.hash("admin123"), "系统管理员");
            log.info("已创建默认管理员账号: admin / admin123");
            System.out.println("[初始化] 已创建默认管理员账号 admin / admin123,请登录后立即修改密码!");
        }
    }
}
