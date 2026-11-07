package com.szjx.personnel.server.config;

import com.szjx.personnel.server.util.Db;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * JdbcTemplate 装配:统一走 DialectJdbcTemplate,所有 String SQL 在执行前经 Db.rewrite()
 * 按当前方言转换(SQLite 直通零开销)。替代 Spring Boot 默认的 JdbcTemplate Bean。
 */
@Configuration
public class DbConfig {

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new DialectJdbcTemplate(dataSource);
    }

    /**
     * 方言感知的 JdbcTemplate:重写项目实际使用的 String 入口,执行前做方言改写。
     * PreparedStatement 变体与 batchUpdate 不经改写(参数化 SQL 本身无方言函数)。
     */
    public static class DialectJdbcTemplate extends JdbcTemplate {

        public DialectJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        private static String rw(String sql) {
            return Db.rewrite(sql);
        }

        @Override
        public void execute(String sql) {
            super.execute(rw(sql));
        }

        @Override
        public int update(String sql) {
            return super.update(rw(sql));
        }

        @Override
        public int update(String sql, Object... args) {
            return super.update(rw(sql), args);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql) {
            return super.queryForList(rw(sql));
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return super.queryForList(rw(sql), args);
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            return super.queryForList(rw(sql), elementType, args);
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            return super.queryForMap(rw(sql), args);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            return super.queryForObject(rw(sql), requiredType);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return super.queryForObject(rw(sql), requiredType, args);
        }

        @Override
        public <T> T queryForObject(String sql, Object[] args, Class<T> requiredType) {
            return super.queryForObject(rw(sql), args, requiredType);
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            return super.queryForObject(rw(sql), rowMapper, args);
        }

        @Override
        public void query(String sql, RowCallbackHandler rch, Object... args) {
            super.query(rw(sql), rch, args);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return super.query(rw(sql), rowMapper, args);
        }
    }
}
