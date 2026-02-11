package com.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class JdbcUtils {

    private static final Properties props;
    private static final ConcurrentHashMap<String, SshTunnel> sshTunnels = new ConcurrentHashMap<>();
    private static final ThreadLocal<DatabaseConfig> currentConfig = new ThreadLocal<>();

    static {
        props = new Properties();
        try (InputStream input = new java.io.FileInputStream("/opt/wxwork-tools/wxwork-tools.properties")) {
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException("加载配置文件失败，请确保 /opt/wxwork-tools/wxwork-tools.properties 文件存在", e);
        }
    }

    public static void setCscrmConfig() {
        currentConfig.set(getCscrmDatabaseConfig());
    }

    public static void setCordyscrmConfig() {
        currentConfig.set(getCordyscrmDatabaseConfig());
    }

    public static void setConfig(DatabaseConfig config) {
        currentConfig.set(config);
    }

    public static void clearConfig() {
        currentConfig.remove();
    }

    private static DatabaseConfig getCurrentConfig() {
        DatabaseConfig config = currentConfig.get();
        if (config == null) {
            config = getCscrmDatabaseConfig();
        }
        return config;
    }

    public static DatabaseConfig getCscrmDatabaseConfig() {
        DatabaseConfig config = new DatabaseConfig();
        config.setDriverClassName(props.getProperty("cscrm.datasource.driver-class-name"));
        config.setUrl(props.getProperty("cscrm.datasource.url"));
        config.setUsername(props.getProperty("cscrm.datasource.username"));
        config.setPassword(props.getProperty("cscrm.datasource.password"));
        config.setUseSshTunnel(false);
        return config;
    }

    public static DatabaseConfig getCordyscrmDatabaseConfig() {
        DatabaseConfig config = new DatabaseConfig();
        config.setDriverClassName(props.getProperty("cordyscrm.datasource.driver-class-name"));
        config.setUrl(props.getProperty("cordyscrm.datasource.url"));
        config.setUsername(props.getProperty("cordyscrm.datasource.username"));
        config.setPassword(props.getProperty("cordyscrm.datasource.password"));
        
        config.setUseSshTunnel(true);
        config.setSshHost(props.getProperty("cordyscrm.ssh.host"));
        config.setSshPort(Integer.parseInt(props.getProperty("cordyscrm.ssh.port")));
        config.setSshUsername(props.getProperty("cordyscrm.ssh.username"));
        config.setSshPrivateKeyPath(props.getProperty("cordyscrm.ssh.privateKeyPath"));
        config.setSshLocalHost(props.getProperty("cordyscrm.ssh.local.host"));
        config.setSshLocalPort(Integer.parseInt(props.getProperty("cordyscrm.ssh.local.port")));
        config.setSshRemoteHost(props.getProperty("cordyscrm.ssh.remote.host"));
        config.setSshRemotePort(Integer.parseInt(props.getProperty("cordyscrm.ssh.remote.port")));
        
        return config;
    }

    public static Connection getConnection() throws SQLException {
        return getConnection(getCurrentConfig());
    }

    public static Connection getConnection(DatabaseConfig config) throws SQLException {
        try {
            Class.forName(config.getDriverClassName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("数据库驱动加载失败", e);
        }

        String url = config.getUrl();
        
        if (config.isUseSshTunnel()) {
            String tunnelKey = config.getSshHost() + ":" + config.getSshPort() + "->" + 
                              config.getSshLocalHost() + ":" + config.getSshLocalPort();
            
            SshTunnel tunnel = sshTunnels.computeIfAbsent(tunnelKey, k -> {
                return new SshTunnel(
                    config.getSshHost(),
                    config.getSshPort(),
                    config.getSshUsername(),
                    config.getSshPrivateKeyPath(),
                    config.getSshLocalHost(),
                    config.getSshLocalPort(),
                    config.getSshRemoteHost(),
                    config.getSshRemotePort()
                );
            });
            
            url = tunnel.getTunnelUrl(url);
        }

        return DriverManager.getConnection(url, config.getUsername(), config.getPassword());
    }

    public static void close(Connection conn, Statement stmt, ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static List<Object[]> query(String sql, Object... params) {
        return query(getCurrentConfig(), sql, params);
    }

    public static List<Object[]> query(DatabaseConfig config, String sql, Object... params) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        List<Object[]> result = new ArrayList<>();

        try {
            conn = getConnection(config);
            pstmt = conn.prepareStatement(sql);
            setParameters(pstmt, params);
            rs = pstmt.executeQuery();

            int columnCount = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Object[] row = new Object[columnCount];
                for (int i = 0; i < columnCount; i++) {
                    row[i] = rs.getObject(i + 1);
                }
                result.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("查询失败", e);
        } finally {
            close(conn, pstmt, rs);
        }

        return result;
    }

    public static Object queryForObject(String sql, Object... params) {
        return queryForObject(getCurrentConfig(), sql, params);
    }

    public static Object queryForObject(DatabaseConfig config, String sql, Object... params) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Object result = null;

        try {
            conn = getConnection(config);
            pstmt = conn.prepareStatement(sql);
            setParameters(pstmt, params);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                result = rs.getObject(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询失败", e);
        } finally {
            close(conn, pstmt, rs);
        }

        return result;
    }

    public static int update(String sql, Object... params) {
        return update(getCurrentConfig(), sql, params);
    }

    public static int update(DatabaseConfig config, String sql, Object... params) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        int result = 0;

        try {
            conn = getConnection(config);
            pstmt = conn.prepareStatement(sql);
            setParameters(pstmt, params);
            result = pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新失败", e);
        } finally {
            close(conn, pstmt, null);
        }

        return result;
    }

    public static int insert(String sql, Object... params) {
        return insert(getCurrentConfig(), sql, params);
    }

    public static int insert(DatabaseConfig config, String sql, Object... params) {
        return update(config, sql, params);
    }

    public static int delete(String sql, Object... params) {
        return delete(getCurrentConfig(), sql, params);
    }

    public static int delete(DatabaseConfig config, String sql, Object... params) {
        return update(config, sql, params);
    }

    public static int[] batchUpdate(String sql, List<Object[]> paramsList) {
        return batchUpdate(getCurrentConfig(), sql, paramsList);
    }

    public static int[] batchUpdate(DatabaseConfig config, String sql, List<Object[]> paramsList) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        int[] result = null;

        try {
            conn = getConnection(config);
            conn.setAutoCommit(false);
            pstmt = conn.prepareStatement(sql);

            for (Object[] params : paramsList) {
                setParameters(pstmt, params);
                pstmt.addBatch();
            }

            result = pstmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            throw new RuntimeException("批量更新失败", e);
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            close(conn, pstmt, null);
        }

        return result;
    }

    private static void setParameters(PreparedStatement pstmt, Object... params) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
        }
    }

    public static boolean execute(String sql, Object... params) {
        return execute(getCurrentConfig(), sql, params);
    }

    public static boolean execute(DatabaseConfig config, String sql, Object... params) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        boolean result = false;

        try {
            conn = getConnection(config);
            pstmt = conn.prepareStatement(sql);
            setParameters(pstmt, params);
            result = pstmt.execute();
        } catch (SQLException e) {
            throw new RuntimeException("执行失败", e);
        } finally {
            close(conn, pstmt, null);
        }

        return result;
    }

    public static void closeSshTunnels() {
        for (SshTunnel tunnel : sshTunnels.values()) {
            tunnel.close();
        }
        sshTunnels.clear();
    }
}
