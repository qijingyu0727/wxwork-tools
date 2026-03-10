package com.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcUtils.class);
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

    public static void setLocalConfig() {
        currentConfig.set(getLocalDatabaseConfig());
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

    public static DatabaseConfig getLocalDatabaseConfig() {
        DatabaseConfig config = new DatabaseConfig();
        config.setDriverClassName(props.getProperty("spring.datasource.driver-class-name"));
        config.setUrl(props.getProperty("spring.datasource.url"));
        config.setUsername(props.getProperty("spring.datasource.username"));
        config.setPassword(props.getProperty("spring.datasource.password"));
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
        long totalStartNs = System.nanoTime();
        try {
            Class.forName(config.getDriverClassName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("数据库驱动加载失败", e);
        }

        String url = config.getUrl();
        long tunnelCostMs = 0L;
        boolean reusedExistingForward = false;

        if (config.isUseSshTunnel()) {
            long tunnelStartNs = System.nanoTime();
            String tunnelKey = config.getSshHost() + ":" + config.getSshPort() + "->" + 
                              config.getSshLocalHost() + ":" + config.getSshLocalPort();
            String localEndpoint = config.getSshLocalHost() + ":" + config.getSshLocalPort();
            String originalUrl = url;
            SshTunnel tunnel = null;
            boolean tunnelExistsBefore = sshTunnels.containsKey(tunnelKey);
            boolean localEndpointReachable = isPortReachable(config.getSshLocalHost(), config.getSshLocalPort(), 200);

            if (localEndpointReachable) {
                reusedExistingForward = true;
            } else {
                try {
                    tunnel = sshTunnels.computeIfAbsent(tunnelKey, k -> new SshTunnel(
                            config.getSshHost(),
                            config.getSshPort(),
                            config.getSshUsername(),
                            config.getSshPrivateKeyPath(),
                            config.getSshLocalHost(),
                            config.getSshLocalPort(),
                            config.getSshRemoteHost(),
                            config.getSshRemotePort()
                    ));
                } catch (RuntimeException e) {
                    if (isAddressInUseError(e) && isPortReachable(config.getSshLocalHost(), config.getSshLocalPort(), 600)) {
                        reusedExistingForward = true;
                        LOGGER.warn("SSH tunnel bind failed but local endpoint is reachable, reuse existing forwarding endpoint={}", localEndpoint);
                    } else {
                        throw e;
                    }
                }
            }

            if (tunnel != null) {
                url = tunnel.getTunnelUrl(url);
            }
            if (url.equals(originalUrl)) {
                url = originalUrl.replaceFirst("jdbc:mysql://[^/]+", "jdbc:mysql://" + localEndpoint);
            }
            tunnelCostMs = (System.nanoTime() - tunnelStartNs) / 1_000_000;
            LOGGER.info("jdbc ssh timing endpoint={}, tunnelExistsBefore={}, localEndpointReachable={}, reusedExistingForward={}, tunnelMs={}",
                    localEndpoint, tunnelExistsBefore, localEndpointReachable, reusedExistingForward, tunnelCostMs);
        }

        long connectStartNs = System.nanoTime();
        Connection connection = DriverManager.getConnection(url, config.getUsername(), config.getPassword());
        long connectCostMs = (System.nanoTime() - connectStartNs) / 1_000_000;
        LOGGER.info("jdbc getConnection timing useSshTunnel={}, connectMs={}, totalMs={}, url={}",
                config.isUseSshTunnel(),
                connectCostMs,
                (System.nanoTime() - totalStartNs) / 1_000_000,
                maskJdbcUrl(url));
        return connection;
    }

    private static boolean isAddressInUseError(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                String text = msg.toLowerCase();
                if (text.contains("address already in use") || text.contains("cannot be bound")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static boolean isPortReachable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception ignored) {
            return false;
        }
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
        long startNs = System.nanoTime();
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
            LOGGER.info("jdbc query timing useSshTunnel={}, rows={}, costMs={}, sql={}",
                    config.isUseSshTunnel(),
                    result.size(),
                    (System.nanoTime() - startNs) / 1_000_000,
                    summarizeSql(sql));
        }

        return result;
    }

    public static Object queryForObject(String sql, Object... params) {
        return queryForObject(getCurrentConfig(), sql, params);
    }

    private static String summarizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        String normalized = sql.replaceAll("\\s+", " ").trim();
        return normalized.length() > 180 ? normalized.substring(0, 180) + "...(truncated)" : normalized;
    }

    private static String maskJdbcUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("(//)([^/@]+@)?([^/?]+)", "$1***@$3");
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
