package com.szjx.personnel.server.util;

import org.apache.poi.hssf.record.DateWindow1904Record;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ExcelDatesTest {
    private static final String LABEL = "出生日期";

    static Stream<Arguments> validText() {
        return Stream.of(
                Arguments.of("20160901", "2016-09-01"),
                Arguments.of("2016-09-01", "2016-09-01"),
                Arguments.of("2016-9-1", "2016-09-01"),
                Arguments.of("2016/09/01", "2016-09-01"),
                Arguments.of("2016/9/1", "2016-09-01"),
                Arguments.of("2016.09.01", "2016-09-01"),
                Arguments.of("2016.9.1", "2016-09-01"),
                Arguments.of("2016年9月1日", "2016-09-01"),
                Arguments.of("2016年09月01日", "2016-09-01"),
                Arguments.of(" \t2016.9.1\r\n", "2016-09-01"),
                Arguments.of("\u3000\u00a02016/9/1\u00a0\u3000", "2016-09-01"),
                Arguments.of("20000229", "2000-02-29"),
                Arguments.of("2024年2月29日", "2024-02-29"),
                Arguments.of("00010101", "0001-01-01"),
                Arguments.of("0001-1-1", "0001-01-01"),
                Arguments.of("9999.12.31", "9999-12-31"));
    }

    @ParameterizedTest
    @MethodSource("validText")
    void strictTextAndTextCells(String input, String expected) throws Exception {
        assertEquals(expected, ExcelDates.normalize(input, LABEL));
        try (Workbook wb = new XSSFWorkbook()) {
            Cell cell = cell(wb);
            cell.setCellValue(input);
            assertEquals(expected, new ExcelDates(wb).read(cell, LABEL));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "20160230", "2016-2-30", "2016/13/1", "2016.0.1", "2016年9月0日", "2016-1-32",
            "1900-02-29", "21000229", "2023年2月29日", "00000101", "0000-1-1", "10000-1-1",
            "+2016-09-01", "-2016-09-01", "1-1-1", "16-9-1", "2016", "201609", "2016-09",
            "09/01/2016", "01/09/2016", "2016-09/01", "2016/09.01", "2016年9-1日",
            "2016年9月1", "2016-9-1日", "2016- 9-1", "2016-009-01", "2016-09-001",
            "2016-09-01T00:00:00", "2016-09-01 00:00:00", "1472688000000", "42614", "20160901.0",
            "2.0160901E7", "20,160,901", "NaN", "Infinity", "２０１６０９０１", "x2016-09-01"
    })
    void rejectsAmbiguousIncompleteAndImpossibleText(String input) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ExcelDates.normalize(input, LABEL));
        assertTrue(error.getMessage().contains(LABEL));
    }

    @Test
    void blankValuesRemainEmpty() throws Exception {
        assertEquals("", ExcelDates.normalize(null, LABEL));
        assertEquals("", ExcelDates.normalize(" \t\u3000\u00a0", LABEL));
        try (Workbook wb = new XSSFWorkbook()) {
            ExcelDates dates = new ExcelDates(wb);
            assertEquals("", dates.read(null, LABEL));
            Cell cell = cell(wb);
            assertEquals("", dates.read(cell, LABEL));
            cell.setCellValue("");
            assertEquals("", dates.read(cell, LABEL));
            cell.setCellFormula("\"\"");
            assertEquals("", dates.read(cell, LABEL));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"General", "0.00E+00", "#,##0", "yyyy-mm-dd"})
    void compactNumericDateIgnoresDisplayFormat(String format) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Cell cell = cell(wb);
            style(wb, cell, format);
            cell.setCellValue(20160901);
            assertEquals("2016-09-01", new ExcelDates(wb).read(cell, LABEL));
            cell.setCellValue(20160230);
            assertThrows(IllegalArgumentException.class, () -> new ExcelDates(wb).read(cell, LABEL));
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {42614, 60, 0, -1, 20160901.5, 1472688000000d, 99999999, 100000000})
    void ordinaryNumbersAreNeverGuessedAsSerialDates(double value) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Cell cell = cell(wb);
            cell.setCellValue(value);
            assertThrows(IllegalArgumentException.class, () -> new ExcelDates(wb).read(cell, LABEL));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeDatesUseWorkbookWindowAndSurviveRoundTrip(boolean date1904) throws Exception {
        try (XSSFWorkbook source = new XSSFWorkbook()) {
            source.getCTWorkbook().getWorkbookPr().setDate1904(date1904);
            Cell cell = cell(source);
            style(source, cell, "m/d/yy");
            cell.setCellValue(DateUtil.getExcelDate(LocalDate.of(2016, 9, 1), date1904) + 0.75);
            try (Workbook wb = copy(source)) {
                assertEquals("2016-09-01", new ExcelDates(wb).read(wb.getSheetAt(0).getRow(0).getCell(0), LABEL));
            }
        }
    }

    @Test
    void serialBoundariesAndFakeLeapDay() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Cell cell = cell(wb);
            style(wb, cell, "yyyy-mm-dd");
            ExcelDates dates = new ExcelDates(wb);
            cell.setCellValue(1);
            assertEquals("1900-01-01", dates.read(cell, LABEL));
            cell.setCellValue(59.9);
            assertEquals("1900-02-28", dates.read(cell, LABEL));
            cell.setCellValue(61);
            assertEquals("1900-03-01", dates.read(cell, LABEL));
            cell.setCellValue(2958465.9);
            assertEquals("9999-12-31", dates.read(cell, LABEL));
            for (double invalid : new double[]{-1, 0, 0.9, 60, 60.99, 2958466, 1e100, Double.NaN, Double.POSITIVE_INFINITY}) {
                cell.setCellValue(invalid);
                assertThrows(IllegalArgumentException.class, () -> dates.read(cell, LABEL), "serial=" + invalid);
            }
        }
    }

    @Test
    void serialZeroAndSixtyAreRealDatesIn1904System() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.getCTWorkbook().getWorkbookPr().setDate1904(true);
            Cell cell = cell(wb);
            style(wb, cell, "yyyy-mm-dd");
            ExcelDates dates = new ExcelDates(wb);
            cell.setCellValue(0);
            assertEquals("1904-01-01", dates.read(cell, LABEL));
            cell.setCellValue(60);
            assertEquals("1904-03-01", dates.read(cell, LABEL));
            cell.setCellValue(DateUtil.getExcelDate(LocalDate.of(9999, 12, 31), true) + 0.99);
            assertEquals("9999-12-31", dates.read(cell, LABEL));
            cell.setCellValue(DateUtil.getExcelDate(LocalDate.of(9999, 12, 31), true) + 1);
            assertThrows(IllegalArgumentException.class, () -> dates.read(cell, LABEL));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void legacyXlsDateWindows(boolean date1904) throws Exception {
        try (HSSFWorkbook source = new HSSFWorkbook()) {
            DateWindow1904Record window = (DateWindow1904Record) source.getInternalWorkbook()
                    .findFirstRecordBySid(DateWindow1904Record.sid);
            window.setWindowing((short) (date1904 ? 1 : 0));
            Cell cell = cell(source);
            style(source, cell, "yyyy-mm-dd");
            cell.setCellValue(DateUtil.getExcelDate(LocalDate.of(2016, 9, 1), date1904));
            try (Workbook wb = copy(source)) {
                assertEquals("2016-09-01", new ExcelDates(wb).read(wb.getSheetAt(0).getRow(0).getCell(0), LABEL));
            }
        }
    }

    @Test
    void evaluatesLocalDateAndTextFormulasWithoutMutatingCells() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Cell date = cell(wb);
            style(wb, date, "yyyy-mm-dd");
            date.setCellFormula("DATE(2016,9,1)");
            Cell text = date.getRow().createCell(1);
            text.setCellFormula("\"2016年9月1日\"");
            Cell joined = date.getRow().createCell(2);
            joined.setCellFormula("\"2016\"&\"/9/1\"");
            Cell compact = date.getRow().createCell(3);
            compact.setCellFormula("20160900+1");
            ExcelDates dates = new ExcelDates(wb);
            for (Cell cell : new Cell[]{date, text, joined, compact}) {
                assertEquals("2016-09-01", dates.read(cell, LABEL));
                assertEquals(CellType.FORMULA, cell.getCellType());
            }
            assertEquals("DATE(2016,9,1)", date.getCellFormula());
            assertEquals(0d, date.getNumericCellValue(), "evaluate must not replace the stale numeric cache");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1/0", "NA()", "TRUE()", "\"20160230\"", "\"2016-09\"", "DATE(1900,2,29)", "1E100"})
    void invalidOrErrorFormulaResultsAreRowErrors(String formula) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Cell cell = cell(wb);
            style(wb, cell, "yyyy-mm-dd");
            cell.setCellFormula(formula);
            assertThrows(IllegalArgumentException.class, () -> new ExcelDates(wb).read(cell, LABEL));
        }
    }

    @Test
    void unstyledDateFormulaIsNotGuessedAsSerial() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Cell cell = cell(wb);
            cell.setCellFormula("DATE(2016,9,1)");
            assertThrows(IllegalArgumentException.class, () -> new ExcelDates(wb).read(cell, LABEL));
        }
    }

    @Test
    void rejects1904NumericFormulasRatherThanPoiWrongDateButAllowsText() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.getCTWorkbook().getWorkbookPr().setDate1904(true);
            Cell date = cell(wb);
            style(wb, date, "yyyy-mm-dd");
            date.setCellFormula("DATE(2016,9,1)");
            Cell text = date.getRow().createCell(1);
            text.setCellFormula("\"2016.9.1\"");
            ExcelDates dates = new ExcelDates(wb);
            String message = assertThrows(IllegalArgumentException.class, () -> dates.read(date, LABEL)).getMessage();
            assertTrue(message.contains("1904"));
            assertTrue(message.contains("日期值"));
            assertEquals("2016-09-01", dates.read(text, LABEL));
        }
    }

    @Test
    void evaluatorsAreIsolatedPerWorkbook() throws Exception {
        try (Workbook first = new XSSFWorkbook(); Workbook second = new XSSFWorkbook()) {
            Cell a = cell(first), b = cell(second);
            a.setCellFormula("DATE(2016,9,1)");
            b.setCellFormula("DATE(2024,2,29)");
            style(first, a, "yyyy-mm-dd");
            style(second, b, "yyyy-mm-dd");
            ExcelDates reader = new ExcelDates(first);
            assertEquals("2016-09-01", reader.read(a, LABEL));
            assertEquals("2024-02-29", new ExcelDates(second).read(b, LABEL));
            assertThrows(IllegalArgumentException.class, () -> reader.read(b, LABEL));
        }
    }

    @Test
    void externalWorkbookFormulaFailsWithoutLoadingResourcesOrUsingCache() throws Exception {
        try (HSSFWorkbook wb = new HSSFWorkbook(); HSSFWorkbook external = new HSSFWorkbook()) {
            external.createSheet("dates").createRow(0).createCell(0).setCellValue("2016-09-01");
            wb.linkExternalWorkbook("missing-date-source.xlsx", external);
            Cell cell = cell(wb);
            cell.setCellFormula("'[missing-date-source.xlsx]dates'!A1");
            assertThrows(IllegalArgumentException.class, () -> new ExcelDates(wb).read(cell, LABEL));
        }
    }

    @Test
    void booleanAndErrorCellsAreNotDates() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Cell cell = cell(wb);
            cell.setCellValue(true);
            assertThrows(IllegalArgumentException.class, () -> new ExcelDates(wb).read(cell, LABEL));
            cell.setCellErrorValue(FormulaError.VALUE.getCode());
            assertThrows(IllegalArgumentException.class, () -> new ExcelDates(wb).read(cell, LABEL));
        }
    }

    private static Cell cell(Workbook wb) {
        return wb.createSheet("dates").createRow(0).createCell(0);
    }

    private static void style(Workbook wb, Cell cell, String format) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.createDataFormat().getFormat(format));
        cell.setCellStyle(style);
    }

    private static Workbook copy(Workbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()));
    }
}
