package com.szjx.personnel.server.config;

import com.szjx.personnel.server.dao.AdminUserDao;
import com.szjx.personnel.server.service.PasswordUtil;
import com.szjx.personnel.server.util.ItemLoanSnapshots;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 数据库初始化:建表 + 默认管理员
 */
@Component
public class SchemaInitializer implements InitializingBean, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final AdminUserDao adminUserDao;
    private final DictSchemaSeed dictSeed;
    private Environment environment = new StandardEnvironment();
    private boolean administratorCreated;

    public boolean isAdministratorCreated() { return administratorCreated; }

    @Override
    public void setEnvironment(Environment environment) { this.environment = environment; }

    @Override
    public void afterPropertiesSet() { run(); }

    public SchemaInitializer(org.springframework.jdbc.core.JdbcTemplate jdbc, AdminUserDao adminUserDao) {
        this.jdbc = jdbc;
        this.adminUserDao = adminUserDao;
        this.dictSeed = new DictSchemaSeed(jdbc);
    }

    /** 旧库迁移:若表缺少某列则补充，返回本次是否新增。 */
    private boolean ensureColumn(String table, String column, String ddl) {
        try {
            List<Map<String, Object>> cols = jdbc.queryForList("PRAGMA table_info(" + table + ")");
            for (Map<String, Object> col : cols) {
                if (column.equals(String.valueOf(col.get("name")))) {
                    return false;
                }
            }
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + ddl);
            log.info("迁移:表 {} 新增列 {}", table, column);
            return true;
        } catch (Exception e) {
            throw new IllegalStateException("数据库结构升级失败: " + table + "." + column + "，服务未开放，请检查日志后重试", e);
        }
    }

    /** 软人员引用：备份部分恢复不得触发数据库级联删除。评定合法值由数据字典 de_rating 驱动,不设硬 CHECK。 */
    private void initializeDoubleExcellence() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS double_excellence_record (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL," +
                "year INTEGER NOT NULL CHECK(year BETWEEN 1900 AND 9999)," +
                "quarter INTEGER NOT NULL CHECK(quarter BETWEEN 1 AND 4)," +
                "rating TEXT NOT NULL," +
                "rating_date TEXT NOT NULL," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime')))" );
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_double_excellence_person_year_quarter ON double_excellence_record(personnel_id,year,quarter)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_double_excellence_year ON double_excellence_record(year,quarter)");
        migrateDoubleExcellenceRating();
    }

    /**
     * 历史版本的表带固定评级 CHECK(rating IN ('好','中','一般'))，与字典驱动的评定相抵触：
     * 管理员在数据字典新增/改名评级后写入会被数据库拒绝。SQLite 无法直接删约束，
     * 检测到旧结构时在事务内重建表（无 rating CHECK，其余约束保留）并完整搬移数据。
     */
    private void migrateDoubleExcellenceRating() {
        String ddl;
        try {
            ddl = jdbc.queryForObject(
                    "SELECT sql FROM sqlite_master WHERE type='table' AND name='double_excellence_record'", String.class);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return; // 表尚未创建（新建库由上面的建表语句处理）
        }
        if (ddl == null || !ddl.toUpperCase().contains("RATING IN (")) return; // 已是新结构
        org.springframework.transaction.support.TransactionTemplate tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        try {
            tx.executeWithoutResult(status -> {
                log.info("迁移:double_excellence_record 移除固定评级 CHECK,评定改由数据字典 de_rating 驱动");
                jdbc.execute("CREATE TABLE double_excellence_mig (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "personnel_id INTEGER NOT NULL," +
                        "year INTEGER NOT NULL CHECK(year BETWEEN 1900 AND 9999)," +
                        "quarter INTEGER NOT NULL CHECK(quarter BETWEEN 1 AND 4)," +
                        "rating TEXT NOT NULL," +
                        "rating_date TEXT NOT NULL," +
                        "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                        "updated_at TEXT DEFAULT (datetime('now','localtime')))");
                jdbc.update("INSERT INTO double_excellence_mig(id, personnel_id, year, quarter, rating, rating_date, created_at, updated_at) " +
                        "SELECT id, personnel_id, year, quarter, rating, rating_date, created_at, updated_at FROM double_excellence_record");
                jdbc.execute("DROP TABLE double_excellence_record");
                jdbc.execute("ALTER TABLE double_excellence_mig RENAME TO double_excellence_record");
                jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_double_excellence_person_year_quarter ON double_excellence_record(personnel_id,year,quarter)");
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_double_excellence_year ON double_excellence_record(year,quarter)");
            });
        } catch (Exception e) {
            throw new IllegalStateException("双争评比表结构升级失败，已回滚，服务未开放，请检查日志后重试", e);
        }
    }

    /** 软人员引用：备份部分恢复不得触发数据库级联删除。评定合法值由数据字典 annual_rating 驱动,不设硬 CHECK。 */
    private void initializeAnnualAppraisal() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS annual_appraisal_record (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL," +
                "year INTEGER NOT NULL CHECK(year BETWEEN 1900 AND 9999)," +
                "rating TEXT NOT NULL," +
                "rating_date TEXT NOT NULL," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime')))" );
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_annual_appraisal_person_year ON annual_appraisal_record(personnel_id,year)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_annual_appraisal_year ON annual_appraisal_record(year)");
    }

    private void initializeVehicleFaultHistory() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS vehicle_fault_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "vehicle_id INTEGER NOT NULL," +
                "fault_date TEXT NOT NULL DEFAULT ''," +
                "description TEXT NOT NULL DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_vehicle_fault_history_vehicle ON vehicle_fault_history(vehicle_id)");
    }

    /** 人员外出登记(临时出差/学习等);personnel.auto_absence 标记本服务置过的状态,手工改状态即放弃自动管理。 */
    private void initializeAbsence() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS absence_record (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL," +
                "reason TEXT NOT NULL," +
                "leave_date TEXT NOT NULL," +
                "expected_return_date TEXT DEFAULT ''," +
                "actual_return_date TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime')))");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_absence_personnel ON absence_record(personnel_id)");
        ensureColumn("personnel", "auto_absence", "auto_absence INTEGER DEFAULT 0");
    }

    /** 借还独立建表；旧库仅补列/索引，不重建、不清理已有记录。 */
    private void initializeItemLoans() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS item_loan (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "item_name TEXT NOT NULL," +
                "specification TEXT NOT NULL DEFAULT ''," +
                "quantity TEXT NOT NULL," +
                "unit TEXT NOT NULL," +
                "personnel_id INTEGER NOT NULL DEFAULT 0," +
                "borrower_name TEXT NOT NULL DEFAULT ''," +
                "borrower_id_card TEXT NOT NULL DEFAULT ''," +
                "borrower_phone TEXT NOT NULL DEFAULT ''," +
                "borrower_unit TEXT NOT NULL DEFAULT ''," +
                "registration_department TEXT NOT NULL DEFAULT ''," +
                "department_id INTEGER NOT NULL DEFAULT 0," +
                "owner_user_id INTEGER NOT NULL DEFAULT 0," +
                "handler_id INTEGER NOT NULL DEFAULT 0," +
                "handler_name TEXT NOT NULL DEFAULT ''," +
                "handler_id_card TEXT NOT NULL DEFAULT ''," +
                "borrower_migrated INTEGER NOT NULL DEFAULT 0," +
                "returned INTEGER NOT NULL DEFAULT 0," +
                "borrow_date TEXT NOT NULL," +
                "expected_return_date TEXT NOT NULL DEFAULT ''," +
                "actual_return_date TEXT NOT NULL DEFAULT ''," +
                "return_condition TEXT NOT NULL DEFAULT ''," +
                "return_remark TEXT NOT NULL DEFAULT ''," +
                "remark TEXT NOT NULL DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime')))" );
        String[] textColumns = {"item_name", "specification", "quantity", "unit", "borrow_date",
                "expected_return_date", "actual_return_date", "return_condition", "return_remark",
                "remark", "created_at", "updated_at"};
        for (String column : textColumns) ensureColumn("item_loan", column, column + " TEXT DEFAULT ''");
        ensureColumn("item_loan", "personnel_id", "personnel_id INTEGER DEFAULT 0");
        for (String column : new String[]{"borrower_name", "borrower_id_card", "borrower_phone", "borrower_unit"}) {
            ensureColumn("item_loan", column, column + " TEXT NOT NULL DEFAULT ''");
        }
        ensureColumn("item_loan", "department_id", "department_id INTEGER NOT NULL DEFAULT 0");
        ensureColumn("item_loan", "owner_user_id", "owner_user_id INTEGER NOT NULL DEFAULT 0");
        // Optional descriptive snapshots only: no personnel foreign key or historical inference.
        ensureColumn("item_loan", "handler_id", "handler_id INTEGER NOT NULL DEFAULT 0");
        ensureColumn("item_loan", "handler_name", "handler_name TEXT NOT NULL DEFAULT ''");
        ensureColumn("item_loan", "handler_id_card", "handler_id_card TEXT NOT NULL DEFAULT ''");
        ensureColumn("item_loan", "borrower_migrated", "borrower_migrated INTEGER NOT NULL DEFAULT 0");
        org.springframework.transaction.support.TransactionTemplate returnMigration =
                new org.springframework.transaction.support.TransactionTemplate(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        returnMigration.executeWithoutResult(status -> {
            // Add the column and backfill atomically, exactly once; retained dates never override explicit state.
            if (ensureColumn("item_loan", "returned", "returned INTEGER NOT NULL DEFAULT 0")) {
                jdbc.update("UPDATE item_loan SET returned=1 WHERE actual_return_date IS NOT NULL AND actual_return_date<>''");
            }
        });
        boolean registrationAdded = jdbc.queryForList("PRAGMA table_info(item_loan)").stream()
                .noneMatch(column -> "registration_department".equals(column.get("name")));
        ensureColumn("item_loan", "registration_department", "registration_department TEXT NOT NULL DEFAULT ''");
        ItemLoanSnapshots.migrateExisting(jdbc);
        if (registrationAdded) {
            // One-time display snapshot only; never derive or change the permission owner from text.
            jdbc.update("UPDATE item_loan SET registration_department=COALESCE(" +
                    "(SELECT name FROM department WHERE department.id=item_loan.department_id),'') " +
                    "WHERE registration_department=''");
        }
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_item_loan_personnel ON item_loan(personnel_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_item_loan_department ON item_loan(department_id)");
        jdbc.execute("DROP INDEX IF EXISTS idx_item_loan_return_dates");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_item_loan_returned ON item_loan(returned)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_item_loan_updated ON item_loan(updated_at,id)");
    }

    public void run(String... args) {
        // 所有字典种子/清理之前建表;旧库先升级 parent_id 和唯一约束。
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_dict (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,dict_type TEXT NOT NULL,dict_label TEXT NOT NULL," +
                "sort INTEGER DEFAULT 0,enabled INTEGER DEFAULT 1,remark TEXT DEFAULT ''," +
                "parent_id INTEGER DEFAULT 0,created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime')),UNIQUE(dict_type,dict_label,parent_id))");
        ensureColumn("sys_dict", "parent_id", "parent_id INTEGER DEFAULT 0");
        dictSeed.migrateDictUnique();
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
        dictSeed.addDict("device_type", new String[]{"军用手机", "地方手机", "智能手表", "智能手环", "其他终端"});
        dictSeed.addDict("phone_brand", new String[]{"华为", "小米", "OPPO", "vivo", "荣耀", "苹果", "三星"});
        dictSeed.addDict("device_status", new String[]{"在用", "停用", "维修", "报废"});

        jdbc.execute("CREATE TABLE IF NOT EXISTS personnel (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "gender TEXT DEFAULT ''," +
                "id_card TEXT DEFAULT ''," +
                "military_id_no TEXT DEFAULT ''," +
                "birthday TEXT DEFAULT ''," +
                "phone TEXT DEFAULT ''," +
                "department_id INTEGER DEFAULT 0," +
                "rank TEXT DEFAULT ''," +
                "post TEXT DEFAULT ''," +
                "post_date TEXT DEFAULT ''," +
                "actual_department_id INTEGER DEFAULT 0," +
                "actual_post TEXT DEFAULT ''," +
                "actual_post_date TEXT DEFAULT ''," +
                "status TEXT DEFAULT '在位'," +
                "entry_date TEXT DEFAULT ''," +
                "equiv_entry_date TEXT DEFAULT ''," +
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
        // 旧档案和旧备份允许缺值，必填校验仅在正常保存/Excel导入入口执行。
        ensureColumn("personnel", "military_id_no", "military_id_no TEXT DEFAULT ''");
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
        ensureColumn("personnel", "post_date", "post_date TEXT DEFAULT ''");
        // 编制岗位与实际在岗分离:实际单位/实际岗位/实际在岗开始时间(旧库幂等补列)
        ensureColumn("personnel", "actual_department_id", "actual_department_id INTEGER DEFAULT 0");
        ensureColumn("personnel", "actual_post", "actual_post TEXT DEFAULT ''");
        ensureColumn("personnel", "actual_post_date", "actual_post_date TEXT DEFAULT ''");
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
                "leave_location TEXT DEFAULT ''," +
                "reason TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "updated_at TEXT DEFAULT (datetime('now','localtime'))" +
                ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_leave_personnel ON leave_record(personnel_id)");

        // 休假计划(年度安排,与实际休假记录互补);保留已有数据并迁移旧表结构
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
        ensureColumn("leave_plan", "personnel_id", "personnel_id INTEGER DEFAULT 0");
        ensureColumn("leave_plan", "year", "year TEXT DEFAULT ''");
        ensureColumn("leave_plan", "leave_type", "leave_type TEXT DEFAULT ''");
        ensureColumn("leave_plan", "start_date", "start_date TEXT DEFAULT ''");
        ensureColumn("leave_plan", "end_date", "end_date TEXT DEFAULT ''");
        ensureColumn("leave_plan", "days", "days REAL DEFAULT 0");
        ensureColumn("leave_plan", "status", "status TEXT DEFAULT '计划中'");
        ensureColumn("leave_plan", "remark", "remark TEXT DEFAULT ''");
        ensureColumn("leave_plan", "created_at", "created_at TEXT DEFAULT (datetime('now','localtime'))");
        ensureColumn("leave_plan", "updated_at", "updated_at TEXT DEFAULT (datetime('now','localtime'))");
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
        dictSeed.addDict("military_type", new String[]{"理论考核", "射击考核", "战术考核", "共同科目", "专业科目", "其他"});
        dictSeed.addDict("personnel_location", new String[]{"机关", "营区", "哨位", "执勤点", "外训点", "其他"});
        dictSeed.addDict("equipment_category", new String[]{"枪支", "大型装备", "小型装备"});
        dictSeed.addDict("edu_degree", new String[]{"博士", "硕士", "学士", "无"});
        dictSeed.addDict("edu_form", new String[]{"普通全日制", "非全日制", "成人高考(函授)", "成人高考(业余)", "自学考试", "网络教育", "开放教育"});

        // 体能考核分数
        try { jdbc.execute("ALTER TABLE fitness_record ADD COLUMN points REAL DEFAULT 0"); } catch (Exception ignore) {}

        // 通用设置表(如体能评定标准)
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_setting (" +
                "skey TEXT PRIMARY KEY," +
                "svalue TEXT DEFAULT '')");
        jdbc.update("INSERT OR IGNORE INTO sys_setting(skey, svalue) VALUES('fitness_grade_rule', ?)",
                "[{\"label\":\"优秀\",\"op\":\">=\",\"min\":90},{\"label\":\"良好\",\"op\":\">=\",\"min\":80},{\"label\":\"及格\",\"op\":\">=\",\"min\":60}]");
        // 仪表盘布局设置(独立成表,备份页可单独勾选"仪表盘"模块)
        jdbc.execute("CREATE TABLE IF NOT EXISTS dash_setting (" +
                "skey TEXT PRIMARY KEY," +
                "svalue TEXT DEFAULT '')");

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

        // Security-critical migration must fail startup rather than silently weaken identity checks.
        migrateAccounts();

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
        ensureColumn("personnel", "follow_army", "follow_army TEXT DEFAULT ''");
        ensureColumn("personnel", "edu_length", "edu_length TEXT DEFAULT ''");

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
                "follow_army_date", "follow_army_status", "equiv_entry_date"};
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
        ensureColumn("leave_record", "leave_location", "leave_location TEXT DEFAULT ''");

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
                "ident_code TEXT DEFAULT ''," +
                "category TEXT DEFAULT ''," +
                "brand TEXT DEFAULT ''," +
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
        ensureColumn("equipment", "photo", "photo TEXT DEFAULT ''");
        // 识别码(唯一识别,Excel导入按此查重更新)
        ensureColumn("equipment", "ident_code", "ident_code TEXT DEFAULT ''");
        ensureColumn("equipment", "brand", "brand TEXT DEFAULT ''");

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
        // 物资照片(每件一张,与装备一致)
        ensureColumn("material", "photo", "photo TEXT DEFAULT ''");

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
        // 车辆照片(每辆一张)
        ensureColumn("vehicle", "photo", "photo TEXT DEFAULT ''");

        initializeVehicleFaultHistory();
        initializeItemLoans();
        initializeAbsence();
        initializeDoubleExcellence();
        initializeAnnualAppraisal();

        dictSeed.migratePersonnelTerms();
        dictSeed.migrateBloodTypeRh();
        dictSeed.migrateEduForm();
        // 私家车价格单位统一为元:历史以万元记录的值(0<price<1000)一次性换算;换算后不再命中条件,幂等
        try {
            jdbc.update("UPDATE private_vehicle SET price = CAST(CAST(price AS REAL) * 10000 AS INTEGER) " +
                    "WHERE TRIM(IFNULL(price,'')) <> '' AND CAST(price AS REAL) > 0 AND CAST(price AS REAL) < 1000");
        } catch (org.springframework.dao.DataAccessException e) {
            // 旧库尚无 price 列时静默跳过(该列由 ensureColumn 补齐)
        }
        dictSeed.initDefaultDict();
        dictSeed.migrateEquipmentLedgerValues();
        dictSeed.seedRegionDict();
        dictSeed.migrateMunicipalityCity();
        dictSeed.normalizeDictSortsOnce();
        dictSeed.migrateEducationOptionsOnce();

        initializeAdministrator();
    }

    public void initializeAdministrator() {
        if (adminUserDao.count() != 0) return;
        String username = environment.getProperty("app.bootstrap.username", "admin").trim();
        String password = environment.getProperty("app.bootstrap.password", "");
        if (username.isEmpty() || username.length() > 64) {
            throw new IllegalStateException("首次初始化的管理员账号不能为空且不能超过64位");
        }
        if (password.trim().isEmpty() || password.length() < 6 || password.length() > 256) {
            throw new IllegalStateException("首次启动必须通过本机向导或 APP_BOOTSTRAP_PASSWORD 环境变量设置6至256位管理员密码；服务未开放，请完成初始化后重试");
        }
        adminUserDao.insertAdmin(username, PasswordUtil.hash(password), "系统管理员");
        administratorCreated = true;
        log.info("已使用本次初始化设置创建管理员，未使用固定默认口令");
    }

    public void migrateAccounts() {
        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        tx.execute(status -> {
            List<Map<String, Object>> columns = jdbc.queryForList("PRAGMA table_info(admin_user)");
            boolean parent = false, enabled = false;
            for (Map<String, Object> column : columns) {
                parent |= "parent_id".equals(column.get("name"));
                enabled |= "enabled".equals(column.get("name"));
            }
            // Every legacy account remains a root administrator; never rename or reset passwords.
            if (!parent) jdbc.execute("ALTER TABLE admin_user ADD COLUMN parent_id INTEGER NOT NULL DEFAULT 0");
            if (!enabled) jdbc.execute("ALTER TABLE admin_user ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1");
            jdbc.execute("CREATE TABLE IF NOT EXISTS account_department (" +
                    "user_id INTEGER NOT NULL REFERENCES admin_user(id)," +
                    "department_id INTEGER NOT NULL REFERENCES department(id)," +
                    "PRIMARY KEY(user_id,department_id))");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_admin_user_parent ON admin_user(parent_id)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_account_department_department ON account_department(department_id)");
            return null;
        });
    }

}
