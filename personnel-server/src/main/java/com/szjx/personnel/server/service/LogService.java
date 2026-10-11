package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.OpLogDao;
import com.szjx.personnel.server.model.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 操作日志:记录与查询(登录/登出/增删改/导入导出/备份恢复等)
 */
@Service
public class LogService {

    private static final Logger log = LoggerFactory.getLogger(LogService.class);

    private final OpLogDao opLogDao;

    public LogService(OpLogDao opLogDao) {
        this.opLogDao = opLogDao;
    }

    public void record(String username, String action, String detail, String ip) {
        try {
            opLogDao.insert(username, action, detail, ip);
        } catch (Exception e) {
            // 日志失败不影响业务主流程
            log.warn("操作日志写入失败: {}", e.getMessage());
        }
    }

    public PageResult<Map<String, Object>> query(String keyword, int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 200) {
            size = 20;
        }
        long total = opLogDao.count(keyword);
        List<Map<String, Object>> list = opLogDao.query(keyword, page, size);
        return new PageResult<Map<String, Object>>(total, list);
    }
}
