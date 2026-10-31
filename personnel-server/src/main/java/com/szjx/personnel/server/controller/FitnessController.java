package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.FitnessRecord;
import com.szjx.personnel.server.model.PageResult;
import com.szjx.personnel.server.service.FitnessService;
import com.szjx.personnel.server.service.FitnessWideExcelService;
import com.szjx.personnel.server.service.ModuleExcelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 体能接口
 *
 * 字面路由优先于通用 /api/{module}/* :
 *   GET  /api/fitness/template      宽行(一人一行)导入模板,说明页注明旧格式仍兼容
 *   POST /api/fitness/import-excel  导入:按表头自动识别宽行/旧一项一行,统一覆盖更新
 *   GET  /api/fitness/export        wide=true 宽行导出;缺省保持旧长格式不变
 *   GET  /api/fitness/summary       宽行汇总(人员+日期分组,分页)
 */
@RestController
@RequestMapping("/api/fitness")
public class FitnessController {

    private static final MediaType XLSX =
            new MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final FitnessService fitnessService;
    private final FitnessWideExcelService wideExcel;
    private final ModuleExcelService excelService;

    @Autowired
    public FitnessController(FitnessService fitnessService,
                             FitnessWideExcelService wideExcel,
                             ModuleExcelService excelService) {
        this.fitnessService = fitnessService;
        this.wideExcel = wideExcel;
        this.excelService = excelService;
    }

    /** 兼容仅装配核心服务的旧用法;Excel/汇总端点在依赖缺失时明确报错。 */
    public FitnessController(FitnessService fitnessService) {
        this(fitnessService, null, null);
    }

    private FitnessWideExcelService wideExcel() {
        if (wideExcel == null) throw new IllegalStateException("体能宽行服务未配置");
        return wideExcel;
    }

    private ModuleExcelService excelService() {
        if (excelService == null) throw new IllegalStateException("模块Excel服务未配置");
        return excelService;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String personnelName,
                             @RequestParam(required = false) String idCard,
                             @RequestParam(required = false) String item,
                             @RequestParam(required = false) String grade,
                             @RequestParam(required = false) String startDate,
                             @RequestParam(required = false) String endDate,
                             @RequestParam(required = false) Long departmentId,
                             @RequestParam(required = false) String minScore,
                             @RequestParam(required = false) String maxScore,
                             @RequestParam(required = false) Long personnelId,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 20;
        List<FitnessRecord> list = fitnessService.queryFiltered2(personnelName, idCard, item, grade, startDate, endDate, departmentId, minScore, maxScore, page, size, personnelId);
        return ApiResponse.ok(new PageResult<FitnessRecord>(fitnessService.countFiltered2(personnelName, idCard, item, grade, startDate, endDate, departmentId, minScore, maxScore, personnelId), list));
    }

    @GetMapping("/stats")
    public ApiResponse stats(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String item,
                             @RequestParam(required = false) String grade,
                             @RequestParam(required = false) String startDate,
                             @RequestParam(required = false) String endDate,
                             @RequestParam(required = false) Long departmentId,
                             @RequestParam(required = false) String minScore,
                             @RequestParam(required = false) String maxScore) {
        return ApiResponse.ok(fitnessService.statsFiltered(keyword, item, grade, startDate, endDate, departmentId, minScore, maxScore));
    }

    /** 宽行汇总:total=distinct(人员+考核日期),分页按组;items=启用项目名按字典排序。 */
    @GetMapping("/summary")
    public ApiResponse summary(@RequestParam(required = false) String personnelName,
                               @RequestParam(required = false) String idCard,
                               @RequestParam(required = false) String item,
                               @RequestParam(required = false) String grade,
                               @RequestParam(required = false) String startDate,
                               @RequestParam(required = false) String endDate,
                               @RequestParam(required = false) Long departmentId,
                               @RequestParam(required = false) String minScore,
                               @RequestParam(required = false) String maxScore,
                               @RequestParam(required = false) Long personnelId,
                               @RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(fitnessService.summary(personnelName, idCard, item, grade, startDate, endDate,
                departmentId, minScore, maxScore, personnelId, page, size));
    }

    /** 宽行(一人一行)导入模板;旧「一项一行」格式不再单独提供模板,说明页注明仍可导入。 */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() throws Exception {
        byte[] bytes = wideExcel().template();
        return download(bytes, "体能考核导入模板.xlsx");
    }

    /** 导入:两种格式按表头自动识别(识别与宽行分支在 ModuleExcelService 的 fitness 分支内)。 */
    @PostMapping("/import-excel")
    public ApiResponse importExcel(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error("请选择要导入的 Excel 文件");
        }
        try (InputStream in = file.getInputStream()) {
            return ApiResponse.ok(excelService().importExcel("fitness", in));
        } catch (ForbiddenException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("导入失败:" + e.getMessage());
        }
    }

    /** 导出:wide=true 宽行(人员+日期分组);缺省沿用旧长格式逐记录导出,行为不变。 */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String ids,
                                         @RequestParam(defaultValue = "false") boolean wide,
                                         @RequestParam(required = false) String personnelName,
                                         @RequestParam(required = false) String idCard,
                                         @RequestParam(required = false) String item,
                                         @RequestParam(required = false) String grade,
                                         @RequestParam(required = false) String startDate,
                                         @RequestParam(required = false) String endDate,
                                         @RequestParam(required = false) Long departmentId,
                                         @RequestParam(required = false) String minScore,
                                         @RequestParam(required = false) String maxScore,
                                         @RequestParam(required = false) Long personnelId) {
        byte[] bytes;
        try {
            List<Long> idList = parseIds(ids);
            if (wide) {
                FitnessWideExcelService.Filters filters = new FitnessWideExcelService.Filters();
                filters.personnelName = personnelName;
                filters.idCard = idCard;
                filters.item = item;
                filters.grade = grade;
                filters.startDate = startDate;
                filters.endDate = endDate;
                filters.departmentId = departmentId;
                filters.minScore = minScore;
                filters.maxScore = maxScore;
                filters.personnelId = personnelId;
                bytes = wideExcel().exportWide(idList, filters);
            } else {
                bytes = excelService().export("fitness", idList);
            }
        } catch (ForbiddenException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                    .body(("{\"code\":1,\"message\":\"" + e.getMessage() + "\",\"data\":null}")
                            .getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
        String fileName = "体能考核_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx";
        return download(bytes, fileName);
    }

    private static List<Long> parseIds(String ids) {
        List<Long> result = new ArrayList<Long>();
        if (ids == null || ids.trim().isEmpty()) return result;
        for (String part : ids.split(",", -1)) {
            try {
                long id = Long.parseLong(part.trim());
                if (id <= 0) throw new NumberFormatException();
                result.add(id);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("导出ID必须为正整数");
            }
        }
        return result;
    }

    private static ResponseEntity<byte[]> download(byte[] bytes, String fileName) {
        String encoded;
        try {
            encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            encoded = "fitness.xlsx";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(XLSX)
                .body(bytes);
    }

    @PostMapping
    public ApiResponse create(@RequestBody FitnessRecord r) {
        return ApiResponse.ok(fitnessService.create(r));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody FitnessRecord r) {
        return ApiResponse.ok(fitnessService.update(id, r));
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        fitnessService.delete(id);
        return ApiResponse.ok();
    }
}
