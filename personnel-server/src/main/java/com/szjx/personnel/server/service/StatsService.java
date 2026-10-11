package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.StatsSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 统计业务
 */
@Service
public class StatsService {

    private final JdbcTemplate jdbc;
    private final PersonnelDao personnelDao;

    public StatsService(JdbcTemplate jdbc, PersonnelDao personnelDao) {
        this.jdbc = jdbc;
        this.personnelDao = personnelDao;
    }

    public StatsSummary summary() {
        StatsSummary s = new StatsSummary();
        s.setTotal(personnelDao.countAll());

        List<Map<String, Object>> dept = jdbc.queryForList(
                "SELECT IFNULL(d.name, '未分配') AS name, COUNT(*) AS count FROM personnel p " +
                "LEFT JOIN department d ON p.department_id = d.id GROUP BY p.department_id ORDER BY count DESC");
        for (Map<String, Object> row : dept) {
            s.getDepartment().add(new StatsSummary.NameCount(
                    String.valueOf(row.get("name")),
                    ((Number) row.get("count")).longValue()));
        }

        List<Map<String, Object>> gender = jdbc.queryForList(
                "SELECT IFNULL(NULLIF(p.gender, ''), '未填写') AS name, COUNT(*) AS count FROM personnel p " +
                "GROUP BY p.gender ORDER BY count DESC");
        for (Map<String, Object> row : gender) {
            s.getGender().add(new StatsSummary.NameCount(
                    String.valueOf(row.get("name")),
                    ((Number) row.get("count")).longValue()));
        }

        List<Map<String, Object>> status = jdbc.queryForList(
                "SELECT IFNULL(NULLIF(p.status, ''), '未填写') AS name, COUNT(*) AS count FROM personnel p " +
                "GROUP BY p.status ORDER BY count DESC");
        for (Map<String, Object> row : status) {
            s.getStatus().add(new StatsSummary.NameCount(
                    String.valueOf(row.get("name")),
                    ((Number) row.get("count")).longValue()));
        }
        return s;
    }
}
