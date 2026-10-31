package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.Personnel;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RosterDriverLicenseTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    RosterExcelService roster;
    static final String DRIVER_KEYS="driverLicense,hasDriverLicense,driverLicenseType,driverLicenseNo,driverVehicleClass,driverIssueDate,driverExpiryDate,driverLicenseStatus,driverReviewDate,driverIssuingAuth";

    @BeforeEach void setup() {
        DataScope.clear(); jdbc=DriverLicenseFeatureTest.database(temp);
        roster=new RosterExcelService(DriverLicenseFeatureTest.people(jdbc),DriverLicenseFeatureTest.departments(jdbc),jdbc);
    }
    @AfterEach void clear() { DataScope.clear(); }

    private XSSFWorkbook export(Map<String,String> query) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(roster.build(query)));
    }
    private static Map<String,String> query(String... pairs) {
        Map<String,String> result=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2) result.put(pairs[i],pairs[i+1]);
        return result;
    }
    private static String text(Sheet sheet,int row,int col) {
        Row r=sheet.getRow(row); Cell cell=r==null?null:r.getCell(col);
        return cell==null?"":new DataFormatter().formatCellValue(cell);
    }
    private static int column(Sheet sheet,String label) {
        for(Cell cell:sheet.getRow(3)) if(label.equals(new DataFormatter().formatCellValue(cell))) return cell.getColumnIndex();
        fail("Missing column: "+label); return -1;
    }
    private static Map<String,Integer> people(Sheet sheet) {
        int name=column(sheet,"姓名"); Map<String,Integer> rows=new LinkedHashMap<>();
        for(int r=4;r<=sheet.getLastRowNum();r++) {
            String value=text(sheet,r,name);
            if(Arrays.asList("周明","李宁","陈晓","王强","刘静","段岩","赵林").contains(value)) {
                assertNull(rows.put(value,r),"A person must not be expanded into multiple sheet rows");
            }
        }
        return rows;
    }
    private static String all(Sheet sheet) {
        StringBuilder value=new StringBuilder();
        for(Row row:sheet) for(Cell cell:row) value.append(new DataFormatter().formatCellValue(cell)).append('\n');
        return value.toString();
    }

    private static long licenseReads(JdbcTemplate db) {
        return mockingDetails(db).getInvocations().stream().filter(call -> call.getMethod().getName().equals("queryForList")
                && call.getArguments().length > 0 && String.valueOf(call.getArguments()[0]).startsWith("SELECT dl.id")).count();
    }

    @Test void metadataContainsTenOptionalLicenseFieldsAndRequestedOrderRemainsStable() throws Exception {
        Set<String> options=new HashSet<>();
        for(Map<String,String> field:roster.fieldOptions()) options.add(field.get("key"));
        assertTrue(options.containsAll(Arrays.asList(DRIVER_KEYS.split(","))));
        try(XSSFWorkbook wb=export(query("fields","name,driverLicenseNo,hasDriverLicense,name,unknown"))) {
            Sheet sheet=wb.getSheetAt(0);
            assertEquals("序号",text(sheet,3,0)); assertEquals("姓名",text(sheet,3,1));
            assertEquals("驾驶证号",text(sheet,3,2)); assertEquals("有无驾驶证",text(sheet,3,3));
            assertEquals(4,sheet.getRow(3).getLastCellNum());
        }
    }

    @Test void multipleCertificatesRemainOnePersonRowWithAlignedBlanksAndLeadingZeros() throws Exception {
        try(XSSFWorkbook wb=export(query("fields","name,"+DRIVER_KEYS))) {
            Sheet sheet=wb.getSheetAt(0); Map<String,Integer> rows=people(sheet);
            assertEquals(7,rows.size()); int first=rows.get("周明"), none=rows.get("陈晓");
            assertEquals("有",text(sheet,first,column(sheet,"有无驾驶证")));
            assertEquals("[1] 军本\n[2] 地方本",text(sheet,first,column(sheet,"驾驶证类型")));
            assertEquals("[1] 00123\n[2] 00000456",text(sheet,first,column(sheet,"驾驶证号")));
            assertEquals("[1] B2\n[2] C1",text(sheet,first,column(sheet,"准驾车型")));
            assertEquals("[1] 吊销\n[2] 有效",text(sheet,first,column(sheet,"驾驶证状态")));
            assertEquals("[1] —\n[2] 车管所",text(sheet,first,column(sheet,"驾驶证发证机关")));
            assertTrue(text(sheet,first,column(sheet,"驾驶证初领日期")).contains("2020年1月1日"));
            assertTrue(text(sheet,first,column(sheet,"驾驶证年审日期")).startsWith("[1] —\n[2] "));
            assertEquals(CellType.STRING,sheet.getRow(first).getCell(column(sheet,"驾驶证号")).getCellType());
            assertTrue(sheet.getRow(first).getCell(column(sheet,"驾驶证号")).getCellStyle().getWrapText());
            assertTrue(sheet.getRow(first).getHeightInPoints()>=36);
            assertEquals("无",text(sheet,none,column(sheet,"有无驾驶证")));
            assertEquals("",text(sheet,none,column(sheet,"驾驶证号")));
            assertEquals("无",text(sheet,none,column(sheet,"驾驶证")));
            assertFalse(all(sheet).contains("OUTSIDE-ORPHAN"));
            assertFalse(all(sheet).contains("null"));
            assertTrue(all(sheet).contains("合计:7人"));
        }
    }

    @Test void blankIdentityAndInvalidHistoricalDatesDoNotHideRegisteredLicenses() throws Exception {
        try(XSSFWorkbook wb=export(query("fields","name,hasDriverLicense,driverLicenseNo,driverIssueDate,driverLicenseStatus"))) {
            Sheet sheet=wb.getSheetAt(0); Map<String,Integer> rows=people(sheet);
            assertEquals("有",text(sheet,rows.get("段岩"),column(sheet,"有无驾驶证")));
            assertEquals("00006006",text(sheet,rows.get("段岩"),column(sheet,"驾驶证号")));
            assertEquals("日期待核",text(sheet,rows.get("刘静"),column(sheet,"驾驶证初领日期")));
            assertEquals("有",text(sheet,rows.get("刘静"),column(sheet,"有无驾驶证")));
            assertEquals("过期",text(sheet,rows.get("王强"),column(sheet,"驾驶证状态")));
        }
    }

    @Test void rootAndChildScopesNeverExportForeignOrOrphanLicenseDetails() throws Exception {
        DataScope.set(new DataScope.Context(2L,"child",false,new HashSet<>(Arrays.asList(1L,2L))));
        try(XSSFWorkbook wb=export(query("fields","name,"+DRIVER_KEYS))) {
            assertEquals(new HashSet<>(Arrays.asList("周明","李宁","陈晓")),people(wb.getSheetAt(0)).keySet());
            String output=all(wb.getSheetAt(0));
            assertFalse(output.contains("00004004")); assertFalse(output.contains("00005005")); assertFalse(output.contains("00006006"));
            assertFalse(output.contains("OUTSIDE-ORPHAN"));
        }
        DataScope.set(new DataScope.Context(2L,"empty",false,Collections.emptySet()));
        try(XSSFWorkbook wb=export(query("fields","name,"+DRIVER_KEYS))) {
            assertTrue(people(wb.getSheetAt(0)).isEmpty()); assertTrue(all(wb.getSheetAt(0)).contains("合计:0人"));
        }
    }

    @Test void licenseFiltersApplyBeforeExportAndShareTheSameCertificateSemantics() throws Exception {
        try(XSSFWorkbook wb=export(query("fields","name,driverLicenseNo","driverLicense","yes","driverLicenseType","军本","driverLicenseStatus","有效"))) {
            assertEquals(Collections.singleton("李宁"),people(wb.getSheetAt(0)).keySet());
            assertTrue(all(wb.getSheetAt(0)).contains("合计:1人"));
        }
        try(XSSFWorkbook wb=export(query("fields","name","driverLicense","no"))) {
            assertEquals(new HashSet<>(Arrays.asList("陈晓","赵林")),people(wb.getSheetAt(0)).keySet());
        }
        try(XSSFWorkbook wb=export(query("fields","name","name","周","driverLicense","yes"))) {
            assertEquals(Collections.singleton("周明"),people(wb.getSheetAt(0)).keySet());
        }
    }

    @Test void noLicenseFieldsDoNotLoadDetailsAndMissingJdbcCannotPretendThereAreNoLicenses() throws Exception {
        JdbcTemplate observed=spy(jdbc);
        RosterExcelService checked=new RosterExcelService(DriverLicenseFeatureTest.people(observed),DriverLicenseFeatureTest.departments(observed),observed);
        checked.build(query("fields","name"));
        assertEquals(0L,licenseReads(observed));
        RosterExcelService legacy=new RosterExcelService(DriverLicenseFeatureTest.people(jdbc),DriverLicenseFeatureTest.departments(jdbc));
        assertNotNull(legacy.build(query("fields","name")));
        assertThrows(IllegalStateException.class,()->legacy.build(query("fields","driverLicense")));
        assertThrows(IllegalStateException.class,()->legacy.build(query("fields","name","driverLicense","yes")));
    }

    @Test void driverDetailsAreBatchedRatherThanQueriedOncePerPerson() throws Exception {
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).execute(status->{
            for(int i=0;i<501;i++) jdbc.update("INSERT INTO personnel(id,name,department_id,status,gender) VALUES(?,?,1,'在职','男')",1000+i,"批量人员"+i);
            return null;
        });
        JdbcTemplate observed=spy(jdbc);
        RosterExcelService checked=new RosterExcelService(DriverLicenseFeatureTest.people(observed),DriverLicenseFeatureTest.departments(observed),observed);
        assertNotNull(checked.build(query("fields","name,driverLicenseNo")));
        assertEquals(2L,licenseReads(observed));
    }

    @Test void overlongLicenseTextFailsClearlyInsteadOfSilentlyTruncatingTheWorkbook() {
        String value=String.join("",Collections.nCopies(33000,"X"));
        jdbc.update("UPDATE driver_license SET license_no=? WHERE id=11",value);
        IllegalArgumentException error=assertThrows(IllegalArgumentException.class,()->roster.build(query("fields","name,driverLicenseNo")));
        assertTrue(error.getMessage().contains("32767"));
    }

    @Test void rollcallFilteringFollowsRegistrationButTheExistingDriverMarkStillExcludesOnlyRevokedRecords() throws Exception {
        PersonnelService people=DriverLicenseFeatureTest.people(jdbc);
        RollCallExcelService roll=new RollCallExcelService(people,DriverLicenseFeatureTest.departments(jdbc),jdbc);
        try(XSSFWorkbook wb=new XSSFWorkbook(new ByteArrayInputStream(roll.build(query("driverLicense","yes","marks","driver"))))) {
            String output=all(wb.getSheetAt(0));
            assertTrue(output.contains("应到:5人"));
            assertTrue(output.contains("周明⊛")); assertTrue(output.contains("李宁⊛")); assertTrue(output.contains("王强⊛"));
            assertTrue(output.contains("刘静")); assertFalse(output.contains("刘静⊛")); assertTrue(output.contains("段岩⊛"));
            assertFalse(output.contains("陈晓")); assertFalse(output.contains("赵林"));
        }
    }
}
