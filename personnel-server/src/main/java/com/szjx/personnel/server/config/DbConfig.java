package com.szjx.personnel.server.config;

import com.szjx.personnel.server.util.Db;
import com.szjx.personnel.server.util.DbProfile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * 数据源与 JdbcTemplate 装配:
 *  - DataSource:默认 SQLite(application.yml 原行为);data/db-config.json 配置了外部库
 *    (mysql|postgresql|kingbase|dm)时改连外部库(驱动从运行目录 lib/ 动态加载)。
 *  - JdbcTemplate:统一走 DialectJdbcTemplate,所有 String SQL 执行前经 Db.rewrite()
 *    按当前方言转换(SQLite 直通零开销)。
 */
@Configuration
public class DbConfig {

    @Bean
    @Primary
    public DataSource dataSource(Environment env) {
        DbProfile profile = DbProfile.load();
        if (profile.external()) {
            Db.setDialect(profile.dialect);
            DbProfile.loadExternalDrivers();
            DriverManagerDataSource ds = new DriverManagerDataSource(profile.url(), profile.user, profile.password);
            return ds;
        }
        Db.setDialect("sqlite");
        // 未配置外部库:沿用 application.yml 的 SQLite 连接(保持原相对路径与 WAL 参数)
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(env.getProperty("spring.datasource.url"));
        ds.setDriverClassName(env.getProperty("spring.datasource.driver-class-name"));
        return ds;
    }

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
