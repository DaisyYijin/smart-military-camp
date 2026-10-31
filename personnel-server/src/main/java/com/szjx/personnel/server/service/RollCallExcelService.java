package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.util.PersonnelExtraFilters;
import com.szjx.personnel.server.util.DepartmentHierarchy;
import com.szjx.personnel.server.model.Personnel;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.text.Collator;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 人员点名册 Excel 导出。
 * 表头=标题+日期;在位人员=按单位 ID 生成任意深度横向树形合并表头,同级并排、姓名同列纵填;
 * 本级人员在其单位表头下一行开始纵填,不另加人员分组表头,不填充单元格底色;
 * 不在位人员=原因在左侧整块合并、姓名横向排列,休假人员姓名下方(单元格内换行)显示归队时间;
 * 底部=应到/在位/不在位统计。横版 A4、按宽度缩放打印。
 */
@Service
public class RollCallExcelService {

    private final PersonnelService personnelService;
    private final JdbcTemplate jdbc;

    public RollCallExcelService(PersonnelService personnelService, DepartmentService departmentService, JdbcTemplate jdbc) {
        this.personnelService = personnelService;
        // 保留构造器兼容现有调用方;层级只读纯单位元数据,不加载全库人员统计。
        this.jdbc = jdbc;
    }

    private static final int MIN_COLUMNS = 6;

    private static class DepartmentBlock {
        Long parentId;
        String label;
        int depth;
        int total;
        boolean branch;
        int firstColumn;
        int lastColumn;
        int width;
        int height;
        int childHeight;
        int row;
        int nextChildColumn;
        List<Personnel> names;
    }

    public byte[] build(Map<String, String> q) throws Exception {
        // 排序协议先校验,非法参数在任何数据查询前拒绝;不参与 SQL、单位/原因分组。
        final Comparator<Personnel> nameOrder = sortComparator(q.get("sort"), LocalDate.now());
        // 1) 人员筛选(参数名与列表接口一致;departmentId 单独做含下级展开)
        Long departmentId = parseLong(q.get("departmentId"));
        List<Personnel> rows = personnelService.query(
                trim(q.get("keyword")), trim(q.get("name")), trim(q.get("idCard")), null,
                trim(q.get("status")), trim(q.get("gender")), trim(q.get("politicalStatus")),
                trim(q.get("category")), trim(q.get("rank")), trim(q.get("post")),
                trim(q.get("nativePlace")), trim(q.get("enlistPlace")), trim(q.get("education")),
                trim(q.get("maritalStatus")), trim(q.get("nation")), trim(q.get("bloodType")),
                trim(q.get("onlyChild")), trim(q.get("locationPoint")),
                trim(q.get("entryDateStart")), trim(q.get("entryDateEnd")), 1, 1000000);

        if (DataScope.restricted()) rows = rows.stream().filter(p -> DataScope.allowsDepartment(p.getDepartmentId()))
                .collect(java.util.stream.Collectors.toList());

        // 2) 纯单位元数据按 ID 建层级;祖先标题不代表扩大人员权限范围。
        DepartmentHierarchy hierarchy = DepartmentHierarchy.load(jdbc);
        if (departmentId != null && (departmentId > 0 || hierarchy.get(departmentId) != null)) {
            final Set<Long> allowed = new HashSet<Long>(hierarchy.descendantIds(departmentId));
            allowed.add(departmentId); // 历史缺失单位仍可按该 ID 筛选直属人员。
            List<Personnel> kept = new ArrayList<Personnel>();
            for (Personnel p : rows) {
                if (p.getDepartmentId() != null && allowed.contains(p.getDepartmentId())) {
                    kept.add(p);
                }
            }
            rows = kept;
        }

        // 共享筛选(驾驶证+实际在岗)沿用列表同一口径,一次收窄已授权/单位匹配人员;不改变标志规则及排序。
        PersonnelExtraFilters extras = PersonnelExtraFilters.fromQuery(q);
        if (!extras.isEmpty()) {
            if (jdbc == null) throw new IllegalStateException("筛选(驾驶证/实际在岗)需要注入 JdbcTemplate");
            if (!rows.isEmpty()) rows = extras.retainMatching(jdbc, rows);
        }

        // 3) 在位 / 不在位划分
        List<Personnel> present = new ArrayList<Personnel>();
        List<Personnel> absent = new ArrayList<Personnel>();
        for (Personnel p : rows) {
            String st = p.getStatus() == null ? "" : p.getStatus();
            if ("在位".equals(st) || "在职".equals(st)) {
                present.add(p);
            } else {
                absent.add(p);
            }
        }

        // 4) 归队时间:当前在休(结束日期>=今天)的最晚结束日期
        Map<Long, String> returns = new HashMap<Long, String>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT personnel_id, MAX(end_date) AS ed FROM leave_record " +
                        "WHERE " + DataScope.personSql("personnel_id") + " AND IFNULL(end_date,'')<>'' AND end_date>=date('now','localtime') GROUP BY personnel_id")) {
            Object pid = r.get("personnel_id");
            if (pid != null) {
                returns.put(((Number) pid).longValue(), String.valueOf(r.get("ed")));
            }
        }

        // 4.5) 标志(可选):党员☭/团员✪前缀、驾驶员⊛后缀;未请求时不查询驾驶员,保持原版式
        java.util.Set<String> marks = parseMarks(q.get("marks"));
        java.util.Set<Long> drivers = marks.contains("driver") ? loadDrivers() : java.util.Collections.<Long>emptySet();

        // 5) 每个人只放入本单位直属名单;标题人数仅汇总本次筛选及权限内的在位人员。
        // actualUnit=true 时仅分组归属变化:实际单位>0且在单位层级内则按实际单位分组,否则回编制单位;
        // marks/归队/排序/不在位块/版式与人数统计(按人)全部不变。
        final boolean groupByActualUnit = "true".equals(trim(q.get("actualUnit")));
        LinkedHashMap<Long, List<Personnel>> direct = new LinkedHashMap<Long, List<Personnel>>();
        for (Personnel p : present) {
            direct.computeIfAbsent(groupingDepartmentId(p, hierarchy, groupByActualUnit), id -> new ArrayList<Personnel>()).add(p);
        }
        Map<Long, Integer> totals = new HashMap<Long, Integer>();
        for (Map.Entry<Long, List<Personnel>> entry : direct.entrySet()) {
            if (hierarchy.get(entry.getKey()) == null) continue;
            for (Long ancestor : hierarchy.ancestorIds(entry.getKey())) {
                totals.merge(ancestor, entry.getValue().size(), Integer::sum);
            }
        }
        List<DepartmentBlock> blocks = new ArrayList<DepartmentBlock>();
        Map<Long, DepartmentBlock> blocksById = new HashMap<Long, DepartmentBlock>();
        // ordered 是环/孤儿安全的稳定前序;仅保留命中单位及必要祖先,不递归渲染。
        for (DepartmentHierarchy.Node node : hierarchy.ordered()) {
            Integer total = totals.get(node.getId());
            if (total == null) continue;
            DepartmentBlock block = new DepartmentBlock();
            block.parentId = node.getParentId();
            block.label = emptyAs(node.getName(), "未命名单位");
            block.depth = node.getDepth();
            block.total = total;
            block.names = direct.getOrDefault(node.getId(), new ArrayList<Personnel>());
            blocksById.put(node.getId(), block);
            DepartmentBlock parent = blocksById.get(block.parentId);
            if (parent != null) parent.branch = true;
            blocks.add(block);
        }
        // 缺失单位仍按 ID 分块;无单位也保留,不能按同名把不同 ID 合并或静默丢人。
        for (Map.Entry<Long, List<Personnel>> entry : direct.entrySet()) {
            if (hierarchy.get(entry.getKey()) != null) continue;
            DepartmentBlock block = new DepartmentBlock();
            block.label = entry.getKey() == null ? "未分配单位"
                    : emptyAs(entry.getValue().get(0).getDepartmentName(), "缺失单位") + "(单位ID:" + entry.getKey() + ")";
            block.names = entry.getValue();
            block.total = block.names.size();
            blocks.add(block);
        }

        // 子单位共享父级宽度;本级姓名使用与父表头相同的整行跨度,不再增加姓名专列。
        int columnCount = 0;
        int presentHeight = 0;
        for (int i = blocks.size() - 1; i >= 0; i--) {
            DepartmentBlock block = blocks.get(i);
            if (!block.branch) block.width = 1;
            block.height = 1 + block.names.size() + block.childHeight;
            DepartmentBlock parent = blocksById.get(block.parentId);
            if (parent != null) {
                parent.width += block.width;
                parent.childHeight = Math.max(parent.childHeight, block.height);
            } else {
                columnCount += block.width;
                presentHeight = Math.max(presentHeight, block.height);
            }
        }
        int nextRootColumn = 0;
        for (DepartmentBlock block : blocks) {
            DepartmentBlock parent = blocksById.get(block.parentId);
            if (parent == null) {
                block.firstColumn = nextRootColumn;
                nextRootColumn += block.width;
            } else {
                block.firstColumn = parent.nextChildColumn;
                parent.nextChildColumn += block.width;
                block.row = parent.row + 1 + parent.names.size();
            }
            block.lastColumn = block.firstColumn + block.width - 1;
            block.nextChildColumn = block.firstColumn;
        }
        if (columnCount > SpreadsheetVersion.EXCEL2007.getMaxColumns()) {
            throw new IllegalArgumentException("点名册在位单位列数超过Excel上限(" +
                    SpreadsheetVersion.EXCEL2007.getMaxColumns() + "列)，请缩小筛选范围");
        }

        // 6) 不在位按原因分组
        LinkedHashMap<String, List<Personnel>> reasons = new LinkedHashMap<String, List<Personnel>>();
        for (Personnel p : absent) {
            String key = emptyAs(p.getStatus(), "其他");
            List<Personnel> list = reasons.get(key);
            if (list == null) {
                list = new ArrayList<Personnel>();
                reasons.put(key, list);
            }
            list.add(p);
        }

        // 只在原分组内稳定排序。所有键相同返回 0,保留查询原序,不追加 ID 等隐式键。
        if (nameOrder != null) {
            for (DepartmentBlock block : blocks) {
                block.names.sort(nameOrder);
            }
            for (List<Personnel> people : reasons.values()) {
                people.sort(nameOrder);
            }
        }

        // ================= 生成工作簿 =================
        Workbook wb = new XSSFWorkbook();
        try {
            Sheet sh = wb.createSheet("点名册");
            int totalCols = Math.max(columnCount, MIN_COLUMNS);
            // 列宽自适应:目标总宽≈160字符,让内容宽高比接近横版A4,减少整页缩放后的左右留白;
            // 同时保证不在位列能容纳“归队:yyyy-MM-dd”整行
            int colWidth = Math.max(16, Math.min(24, 160 / Math.max(1, totalCols)));
            for (int c = 0; c < totalCols; c++) {
                sh.setColumnWidth(c, colWidth * 256);
            }
            sh.getPrintSetup().setLandscape(true);
            sh.getPrintSetup().setPaperSize((short) 9); // A4
            sh.setFitToPage(true);
            sh.getPrintSetup().setFitWidth((short) 1);
            sh.getPrintSetup().setFitHeight((short) 1); // 整表缩放到一页
            sh.setHorizontallyCenter(true);
            sh.setMargin(Sheet.LeftMargin, 0.25);
            sh.setMargin(Sheet.RightMargin, 0.25);
            sh.setMargin(Sheet.TopMargin, 0.35);
            sh.setMargin(Sheet.BottomMargin, 0.35);
            sh.setMargin(Sheet.HeaderMargin, 0.15);
            sh.setMargin(Sheet.FooterMargin, 0.15);

            // 公文字体:标题=方正小标宋简体,表头=黑体,内容/日期/统计=仿宋_GB2312;标题与表头字体自带笔重,不再加粗
            CellStyle titleStyle = style(wb, "方正小标宋简体", 18, false, false, null, false);
            CellStyle subStyle = style(wb, "仿宋_GB2312", 12, false, false, null, false);
            CellStyle sectionStyle = style(wb, "黑体", 12, false, false, null, false);
            // 按单位层级有界复用样式,不为深树逐节点创建样式。
            CellStyle[] departmentStyles = new CellStyle[16];
            for (int depth = 0; depth < departmentStyles.length; depth++) {
                CellStyle heading = style(wb, "黑体", depth == 0 ? 12 : 11, false, true, null, true);
                departmentStyles[depth] = heading;
            }
            CellStyle reasonStyle = style(wb, "黑体", 11, false, true, null, true);
            CellStyle nameStyle = style(wb, "仿宋_GB2312", 12, false, true, null, true);
            CellStyle footStyle = style(wb, "仿宋_GB2312", 12, false, false, null, false);

            int r = 0;
            // 标题 + 日期
            merge(sh, r, 0, r, totalCols - 1, titleStyle, "人员点名册");
            sh.getRow(r).setHeightInPoints(30);
            r++;
            LocalDate today = LocalDate.now();
            merge(sh, r, 0, r, totalCols - 1, subStyle,
                    "日期:" + today.getYear() + "年" + today.getMonthValue() + "月" + today.getDayOfMonth() + "日");
            r++;
            r++; // 空行

            // 在位人员
            merge(sh, r, 0, r, totalCols - 1, sectionStyle, "在位人员(共" + present.size() + "人)");
            r++;
            if (blocks.isEmpty()) {
                merge(sh, r, 0, r, totalCols - 1, nameStyle, "无");
                r++;
            } else {
                int headerStart = r;
                int presentEnd = headerStart + presentHeight;
                // 先铺网格再合并单位和姓名行,后续填字不能压扁已经扩高的行。
                for (int row = headerStart; row < presentEnd; row++) {
                    ensureRow(sh, row).setHeightInPoints(22);
                    for (int c = 0; c < totalCols; c++) setCell(sh, row, c, nameStyle, "");
                }
                for (DepartmentBlock block : blocks) {
                    int row = headerStart + block.row;
                    CellStyle heading = departmentStyles[Math.min(block.depth, departmentStyles.length - 1)];
                    mergeHeader(sh, row, block.firstColumn, row, block.lastColumn,
                            heading, block.label + "(在位" + block.total + "人)");
                    for (int i = 0; i < block.names.size(); i++) {
                        int nameRow = row + 1 + i;
                        merge(sh, nameRow, block.firstColumn, nameRow, block.lastColumn, nameStyle, null);
                        setNameCell(sh, nameRow, block.firstColumn, nameStyle, block.names.get(i), null, marks, drivers);
                        fitMergedText(sh, nameRow, block.firstColumn, nameRow, block.lastColumn,
                                ensureRow(sh, nameRow).getCell(block.firstColumn).getStringCellValue());
                    }
                }
                r = presentEnd;
            }
            r++; // 空行

            // 不在位人员
            StringBuilder reasonText = new StringBuilder();
            for (Map.Entry<String, List<Personnel>> e : reasons.entrySet()) {
                if (reasonText.length() > 0) {
                    reasonText.append("、");
                }
                reasonText.append(e.getKey()).append(e.getValue().size()).append("人");
            }
            String absentTitle = "不在位人员(共" + absent.size() + "人" +
                    (reasonText.length() > 0 ? ":" + reasonText : "") + ")";
            merge(sh, r, 0, r, totalCols - 1, sectionStyle, absentTitle);
            r++;
            int nameCols = Math.max(1, totalCols - 1); // 姓名横向铺满原因列右侧的所有列
            for (Map.Entry<String, List<Personnel>> e : reasons.entrySet()) {
                List<Personnel> people = e.getValue();
                int rowsN = Math.max(1, (people.size() + nameCols - 1) / nameCols);
                for (int i = 0; i < rowsN; i++) {
                    ensureRow(sh, r + i).setHeightInPoints(32); // 预留归队时间第二行
                    for (int c = 1; c < totalCols; c++) {
                        setCell(sh, r + i, c, nameStyle, "");
                    }
                }
                merge(sh, r, 0, r + rowsN - 1, 0, reasonStyle, e.getKey() + "(" + people.size() + "人)");
                for (int i = 0; i < people.size(); i++) {
                    Personnel p = people.get(i);
                    int rowAt = r + i / nameCols;
                    int colAt = 1 + i % nameCols;
                    setNameCell(sh, rowAt, colAt, nameStyle, p, returns.get(p.getId()), marks, drivers);
                }
                r += rowsN;
            }
            if (reasons.isEmpty()) {
                merge(sh, r, 0, r, totalCols - 1, nameStyle, "无");
                r++;
            }
            r++; // 空行

            // 底部统计
            merge(sh, r, 0, r, totalCols - 1, footStyle,
                    "应到:" + rows.size() + "人    在位:" + present.size() + "人    不在位:" + absent.size() +
                            "人    实到:        人    点名人:");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.setPrintArea(0, 0, totalCols - 1, 0, r);
            wb.write(out);
            return out.toByteArray();
        } finally {
            wb.close();
        }
    }

    /**
     * 分组归属单位:actualUnit=true 且实际单位>0且存在于单位层级时用实际单位,否则回编制单位。
     * 仅影响表头归属,不改人员名单、排序、统计口径。
     */
    private static Long groupingDepartmentId(Personnel p, DepartmentHierarchy hierarchy, boolean actualUnit) {
        if (!actualUnit) return p.getDepartmentId();
        Long actual = p.getActualDepartmentId();
        if (actual != null && actual > 0 && hierarchy.get(actual) != null) return actual;
        return p.getDepartmentId();
    }

    private enum SortField {
        serviceYears, rank, age, name, entryDate, postDate, category, education, post
    }

    /**
     * sort=serviceYears:desc,rank:desc,name:asc;第一项优先,最多五项。
     * 空/缺省返回 null 表示不排序。Collator 只属于本次 build,不跨请求共享。
     */
    static Comparator<Personnel> sortComparator(String raw, LocalDate today) {
        if (trim(raw).isEmpty()) return null;
        String[] parts = raw.split(",", -1);
        if (parts.length > 5) throw new IllegalArgumentException("点名册排序最多支持5项");
        Set<SortField> seen = new HashSet<SortField>();
        List<Comparator<Personnel>> orders = new ArrayList<Comparator<Personnel>>();
        final Collator chinese = Collator.getInstance(Locale.CHINA);
        for (String part : parts) {
            String[] pair = part.split(":", -1);
            if (pair.length != 2) throw new IllegalArgumentException("排序格式应为field:asc或field:desc");
            final SortField field;
            try {
                field = SortField.valueOf(trim(pair[0]));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("不支持的点名册排序字段:" + trim(pair[0]));
            }
            if (!seen.add(field)) throw new IllegalArgumentException("排序字段不可重复:" + field);
            String direction = trim(pair[1]);
            if (!"asc".equals(direction) && !"desc".equals(direction)) {
                throw new IllegalArgumentException("排序方向仅支持asc或desc");
            }
            boolean ascending = "asc".equals(direction);
            switch (field) {
                // 军龄/年龄越高,相应日期越早;不能用整年差,否则同年不同月会误判同值。
                case serviceYears:
                    orders.add(compareKey(p -> serviceDate(p, today), Comparator.naturalOrder(), !ascending));
                    break;
                case age:
                    orders.add(compareKey(p -> pastDate(p.getBirthday(), today), Comparator.naturalOrder(), !ascending));
                    break;
                case rank:
                    orders.add(compareKey(p -> rankWeight(p.getRank()), Comparator.naturalOrder(), ascending));
                    break;
                case education:
                    orders.add(compareKey(p -> educationWeight(p.getEducation()), Comparator.naturalOrder(), ascending));
                    break;
                case entryDate:
                    orders.add(compareKey(p -> sortDate(p.getEntryDate()), Comparator.naturalOrder(), ascending));
                    break;
                case postDate:
                    orders.add(compareKey(p -> sortDate(p.getPostDate()), Comparator.naturalOrder(), ascending));
                    break;
                case name:
                    orders.add(compareKey(p -> textKey(p.getName()), chinese::compare, ascending));
                    break;
                case category:
                    orders.add(compareKey(p -> textKey(p.getCategory()), chinese::compare, ascending));
                    break;
                case post:
                    // 职务仅按中文文本,不推断职务高低。
                    orders.add(compareKey(p -> textKey(p.getPost()), chinese::compare, ascending));
                    break;
                default:
                    throw new IllegalArgumentException("不支持的点名册排序字段:" + field);
            }
        }
        return (a, b) -> {
            for (Comparator<Personnel> order : orders) {
                int compared = order.compare(a, b);
                if (compared != 0) return compared;
            }
            return 0;
        };
    }

    /** 只翻转有效键的方向,空/未知/非法键始终最后;两者都无效则交给下一排序项。 */
    private static <T> Comparator<Personnel> compareKey(Function<Personnel, T> key,
                                                       Comparator<? super T> values, boolean ascending) {
        return (a, b) -> {
            T left = key.apply(a);
            T right = key.apply(b);
            if (left == null) return right == null ? 0 : 1;
            if (right == null) return -1;
            return ascending ? values.compare(left, right) : values.compare(right, left);
        };
    }

    private static String textKey(String value) {
        String text = trim(value);
        return text.isEmpty() ? null : text;
    }

    /**
     * 项目标准是严格 yyyy-MM-dd (Personnel/Dates);仅对历史导入兼容 yyyy-MM、yyyy、yyyyMMdd。
     * 缺日/月取该月/年首日;整串匹配且为真实日历日期,不截断时间戳、不接收斜线/点号。
     * 四位年份 0001..9999,0000 无效;不会把 2 月 30 日宽松归一化。
     */
    private static LocalDate sortDate(String raw) {
        String value = trim(raw);
        try {
            LocalDate date;
            if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                date = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            } else if (value.matches("[0-9]{4}-[0-9]{2}")) {
                date = LocalDate.parse(value + "-01", DateTimeFormatter.ISO_LOCAL_DATE);
            } else if (value.matches("[0-9]{4}")) {
                date = LocalDate.parse(value + "-01-01", DateTimeFormatter.ISO_LOCAL_DATE);
            } else if (value.matches("[0-9]{8}")) {
                date = LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
            } else {
                return null;
            }
            return date.getYear() > 0 ? date : null;
        } catch (DateTimeException ex) {
            return null;
        }
    }

    private static LocalDate pastDate(String raw, LocalDate today) {
        LocalDate date = sortDate(raw);
        return date != null && !date.isAfter(today) ? date : null;
    }

    private static LocalDate serviceDate(Personnel person, LocalDate today) {
        LocalDate equivalent = pastDate(person.getEquivEntryDate(), today);
        return equivalent != null ? equivalent : pastDate(person.getEntryDate(), today);
    }

    /** 固定军衔权重,不用字典顺序,不从军衔/职级混合字段的自定义文本猜等级。 */
    private static Integer rankWeight(String raw) {
        switch (trim(raw)) {
            case "上将": return 190;
            case "中将": return 180;
            case "少将": return 170;
            case "大校": return 160;
            case "上校": return 150;
            case "中校": return 140;
            case "少校": return 130;
            case "上尉": return 120;
            case "中尉": return 110;
            case "少尉": return 100;
            case "一级军士长": return 90;
            case "二级军士长": return 80;
            case "三级军士长": return 70;
            case "一级上士":
            case "四级军士长": return 60;
            case "二级上士":
            case "上士": return 50;
            case "中士": return 40;
            case "下士": return 30;
            case "上等兵": return 20;
            case "列兵": return 10;
            default: return null;
        }
    }

    /** 明确学历等级,高中/职高/中专同为高中阶段;不将含糊的“研究生”猜成硕士。 */
    private static Integer educationWeight(String raw) {
        switch (trim(raw)) {
            case "博士":
            case "博士研究生": return 80;
            case "硕士":
            case "硕士研究生": return 70;
            case "本科":
            case "大学本科": return 60;
            case "大专":
            case "专科":
            case "大学专科":
            case "高职": return 50;
            case "高中":
            case "普通高中":
            case "职高":
            case "职业高中":
            case "中专":
            case "中等专科": return 40;
            case "初中": return 30;
            case "小学": return 20;
            default: return null;
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** marks=party,league,driver;未知项忽略,空/缺省=不加任何标志(与旧版一致) */
    private static java.util.Set<String> parseMarks(String raw) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        for (String part : raw.split("[,，]")) {
            String key = part.trim().toLowerCase(java.util.Locale.ROOT);
            if ("party".equals(key) || "league".equals(key) || "driver".equals(key)) {
                out.add(key);
            }
        }
        return out;
    }

    private static boolean isParty(Personnel p) {
        String v = p.getPoliticalStatus() == null ? "" : p.getPoliticalStatus();
        return v.contains("党员");
    }

    private static boolean isLeague(Personnel p) {
        String v = p.getPoliticalStatus() == null ? "" : p.getPoliticalStatus();
        return !isParty(p) && v.contains("团员");
    }

    /** 有驾驶证记录即视为驾驶员;吊销的不计 */
    private java.util.Set<Long> loadDrivers() {
        java.util.Set<Long> ids = new java.util.HashSet<Long>();
        String sql = "SELECT DISTINCT personnel_id FROM driver_license " +
                "WHERE personnel_id IS NOT NULL AND IFNULL(license_status,'')<>'吊销'";
        if (DataScope.restricted()) {
            sql += " AND " + DataScope.personSql("personnel_id");
        }
        for (Map<String, Object> r : jdbc.queryForList(sql)) {
            Object pid = r.get("personnel_id");
            if (pid != null) {
                ids.add(((Number) pid).longValue());
            }
        }
        return ids;
    }

    /** 姓名单元格:党员☭/团员✪前缀、驾驶员⊛后缀,黑白显示,与姓名同字体同颜色;无标志时与普通写入一致 */
    private static void setNameCell(Sheet sh, int rowIdx, int colIdx, CellStyle st, Personnel p, String returnDate,
                                    java.util.Set<String> marks, java.util.Set<Long> drivers) {
        Row row = ensureRow(sh, rowIdx);
        Cell cell = row.getCell(colIdx);
        if (cell == null) {
            cell = row.createCell(colIdx);
        }
        cell.setCellStyle(st);
        String name = p.getName() == null ? "" : p.getName();
        String prefix = "";
        if (marks.contains("party") && isParty(p)) {
            prefix = "☭";
        } else if (marks.contains("league") && isLeague(p)) {
            prefix = "✪";
        }
        String suffix = marks.contains("driver") && p.getId() != null && drivers.contains(p.getId()) ? "⊛" : "";
        String returnLine = returnDate != null && !returnDate.isEmpty() ? "\n归队:" + cnDate(returnDate) : "";
        String text = prefix + name + suffix + returnLine;
        if (!text.isEmpty()) {
            cell.setCellValue(text);
        }
    }

    private static String emptyAs(String s, String def) {
        return s == null || s.isEmpty() ? def : s;
    }

    /** 日期显示为 年月日;解析失败原样返回 */
    private static String cnDate(String s) {
        if (s == null || s.trim().isEmpty()) {
            return "";
        }
        String v = s.trim();
        try {
            String iso = v.length() == 8
                    ? v.substring(0, 4) + "-" + v.substring(4, 6) + "-" + v.substring(6, 8)
                    : v.substring(0, 10);
            java.time.LocalDate d = java.time.LocalDate.parse(iso, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
            return d.getYear() + "年" + d.getMonthValue() + "月" + d.getDayOfMonth() + "日";
        } catch (Exception e) {
            return v;
        }
    }

    private static Long parseLong(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static CellStyle style(Workbook wb, String fontName, int fontPt, boolean bold, boolean wrap, IndexedColors fill, boolean bordered) {
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) fontPt);
        font.setBold(bold);
        font.setFontName(fontName);
        CellStyle st = wb.createCellStyle();
        st.setFont(font);
        st.setAlignment(HorizontalAlignment.CENTER);
        st.setVerticalAlignment(VerticalAlignment.CENTER);
        st.setWrapText(wrap);
        if (bordered) {
            st.setBorderTop(BorderStyle.THIN);
            st.setBorderBottom(BorderStyle.THIN);
            st.setBorderLeft(BorderStyle.THIN);
            st.setBorderRight(BorderStyle.THIN);
        }
        if (fill != null) {
            st.setFillForegroundColor(fill.getIndex());
            st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return st;
    }

    private static Row ensureRow(Sheet sh, int rowIdx) {
        Row row = sh.getRow(rowIdx);
        if (row == null) {
            row = sh.createRow(rowIdx);
        }
        return row;
    }

    private static void mergeHeader(Sheet sh, int r1, int c1, int r2, int c2, CellStyle st, String text) {
        merge(sh, r1, c1, r2, c2, st, text);
        fitMergedText(sh, r1, c1, r2, c2, text);
    }

    private static void fitMergedText(Sheet sh, int r1, int c1, int r2, int c2, String text) {
        double width = 0;
        for (int col = c1; col <= c2; col++) width += sh.getColumnWidth(col) / 256.0;
        width = Math.max(1, width - 2);
        int lines = 0;
        for (String line : text.split("\\n", -1)) {
            int units = 0;
            for (int i = 0; i < line.length(); i++) units += line.charAt(i) > 255 ? 2 : 1;
            lines += Math.max(1, (int) Math.ceil(units / width));
        }
        // 合并单元格不会自动增加行高；按中西文宽度预留换行空间，已有纵向跨度也计入。
        float available = 0;
        for (int row = r1; row <= r2; row++) available += ensureRow(sh, row).getHeightInPoints();
        float required = Math.min(409f, Math.max(22f, lines * 16f + 4f));
        if (required > available) {
            Row last = ensureRow(sh, r2);
            last.setHeightInPoints(Math.min(409f, last.getHeightInPoints() + required - available));
        }
    }

    /** 先给合并区内所有单元格上样式与文本(左上角),再合并,保证边框完整 */
    private static void merge(Sheet sh, int r1, int c1, int r2, int c2, CellStyle st, String text) {
        for (int r = r1; r <= r2; r++) {
            Row row = ensureRow(sh, r);
            for (int c = c1; c <= c2; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) {
                    cell = row.createCell(c);
                }
                cell.setCellStyle(st);
            }
        }
        if (text != null && !text.isEmpty()) {
            ensureRow(sh, r1).getCell(c1).setCellValue(text);
        }
        if (r1 != r2 || c1 != c2) {
            sh.addMergedRegion(new CellRangeAddress(r1, r2, c1, c2));
        }
    }

    private static void setCell(Sheet sh, int rowIdx, int colIdx, CellStyle st, String text) {
        Row row = ensureRow(sh, rowIdx);
        Cell cell = row.getCell(colIdx);
        if (cell == null) {
            cell = row.createCell(colIdx);
        }
        cell.setCellStyle(st);
        if (text != null && !text.isEmpty()) {
            cell.setCellValue(text);
        }
    }
}
