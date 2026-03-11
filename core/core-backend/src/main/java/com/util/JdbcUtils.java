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
        config.setConnectTimeoutMs(getIntProperty("cordyscrm.jdbc.connect-timeout-ms", 8000));
        config.setSocketTimeoutMs(getIntProperty("cordyscrm.jdbc.socket-timeout-ms", 30000));
        config.setQueryTimeoutSec(getIntProperty("cordyscrm.jdbc.query-timeout-sec", 45));
        config.setSshReconnectRetryTimes(getIntProperty("cordyscrm.ssh.reconnect-retry-times", 1));

        config.setUseSshTunnel(true);
        config.setSshHost(props.getProperty("cordyscrm.ssh.host"));
        config.setSshPort(getIntProperty("cordyscrm.ssh.port", 22));
        config.setSshUsername(props.getProperty("cordyscrm.ssh.username"));
        config.setSshPrivateKeyPath(props.getProperty("cordyscrm.ssh.privateKeyPath"));
        config.setSshLocalHost(props.getProperty("cordyscrm.ssh.local.host"));
        config.setSshLocalPort(getIntProperty("cordyscrm.ssh.local.port", 3307));
        config.setSshRemoteHost(props.getProperty("cordyscrm.ssh.remote.host"));
        config.setSshRemotePort(getIntProperty("cordyscrm.ssh.remote.port", 3406));

        return config;
    }

    public static DatabaseConfig getCordyscrmFastLookupDatabaseConfig() {
        DatabaseConfig config = copyDatabaseConfig(getCordyscrmDatabaseConfig());
        config.setConnectTimeoutMs(getIntProperty("cordyscrm.lookup.fast-connect-timeout-ms",
                clampPositiveInt(config.getConnectTimeoutMs(), 2500)));
        config.setSocketTimeoutMs(getIntProperty("cordyscrm.lookup.fast-socket-timeout-ms",
                clampPositiveInt(config.getSocketTimeoutMs(), 5000)));
        config.setQueryTimeoutSec(getIntProperty("cordyscrm.lookup.fast-query-timeout-sec",
                clampPositiveInt(config.getQueryTimeoutSec(), 3)));
        config.setSshReconnectRetryTimes(getIntProperty("cordyscrm.lookup.fast-ssh-reconnect-retry-times", 0));
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
        int maxAttempts = config.isUseSshTunnel() ? Math.max(config.getSshReconnectRetryTimes(), 0) + 1 : 1;
        SQLException lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            TunnelPreparationResult tunnelResult = prepareJdbcUrl(config, attempt > 1);
            String url = applyJdbcTimeouts(tunnelResult.url, config);
            long connectStartNs = System.nanoTime();
            try {
                Connection connection = DriverManager.getConnection(url, config.getUsername(), config.getPassword());
                long connectCostMs = (System.nanoTime() - connectStartNs) / 1_000_000;
                LOGGER.info("jdbc getConnection timing useSshTunnel={}, attempt={}/{}, connectMs={}, totalMs={}, url={}",
                        config.isUseSshTunnel(),
                        attempt,
                        maxAttempts,
                        connectCostMs,
                        (System.nanoTime() - totalStartNs) / 1_000_000,
                        maskJdbcUrl(url));
                return connection;
            } catch (SQLException e) {
                lastException = e;
                boolean retryable = config.isUseSshTunnel() && attempt < maxAttempts && isRetryableTunnelException(e);
                LOGGER.warn("jdbc getConnection failed useSshTunnel={}, attempt={}/{}, retryable={}, errType={}, sqlState={}, vendorCode={}, url={}",
                        config.isUseSshTunnel(),
                        attempt,
                        maxAttempts,
                        retryable,
                        classifySqlException(e),
                        safeSqlState(e),
                        e.getErrorCode(),
                        maskJdbcUrl(url));
                if (retryable) {
                    LOGGER.warn("jdbc connection event=full_lookup_retry useSshTunnel={}, attempt={}/{}, url={}",
                            config.isUseSshTunnel(),
                            attempt,
                            maxAttempts,
                            maskJdbcUrl(url));
                    resetSshTunnel(config, "connect-failed-retry");
                    continue;
                }
                throw e;
            }
        }

        throw lastException != null ? lastException : new SQLException("数据库连接失败");
    }

    private static TunnelPreparationResult prepareJdbcUrl(DatabaseConfig config, boolean forceReset) {
        String url = config.getUrl();
        if (!config.isUseSshTunnel()) {
            return new TunnelPreparationResult(url);
        }

        long tunnelStartNs = System.nanoTime();
        String tunnelKey = buildTunnelKey(config);
        String localEndpoint = config.getSshLocalHost() + ":" + config.getSshLocalPort();
        boolean tunnelExistsBefore = sshTunnels.containsKey(tunnelKey);
        boolean localEndpointReachable = isPortReachable(config.getSshLocalHost(), config.getSshLocalPort(), 200);
        boolean reusedExistingForward = false;
        boolean staleTunnelRemoved = false;
        boolean tunnelRebuilt = false;

        if (forceReset) {
            staleTunnelRemoved = closeAndRemoveTunnel(tunnelKey, "force-reset-before-retry") || staleTunnelRemoved;
            localEndpointReachable = isPortReachable(config.getSshLocalHost(), config.getSshLocalPort(), 200);
        }

        if (tunnelExistsBefore && !localEndpointReachable) {
            staleTunnelRemoved = closeAndRemoveTunnel(tunnelKey, "stale-local-endpoint") || staleTunnelRemoved;
        }

        localEndpointReachable = isPortReachable(config.getSshLocalHost(), config.getSshLocalPort(), 200);
        if (localEndpointReachable) {
            reusedExistingForward = true;
        } else {
            try {
                sshTunnels.compute(tunnelKey, (key, existingTunnel) -> {
                    if (existingTunnel != null) {
                        existingTunnel.close();
                    }
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
                tunnelRebuilt = true;
            } catch (RuntimeException e) {
                if (isAddressInUseError(e) && isPortReachable(config.getSshLocalHost(), config.getSshLocalPort(), 600)) {
                    reusedExistingForward = true;
                    LOGGER.warn("SSH tunnel bind failed but local endpoint is reachable, reuse existing forwarding endpoint={}", localEndpoint);
                } else {
                    throw e;
                }
            }
        }

        String tunneledUrl = url.replaceFirst("jdbc:mysql://[^/]+", "jdbc:mysql://" + localEndpoint);
        long tunnelCostMs = (System.nanoTime() - tunnelStartNs) / 1_000_000;
        LOGGER.info("jdbc ssh timing endpoint={}, tunnelExistsBefore={}, localEndpointReachable={}, reusedExistingForward={}, staleTunnelRemoved={}, tunnelRebuilt={}, tunnelMs={}",
                localEndpoint,
                tunnelExistsBefore,
                localEndpointReachable,
                reusedExistingForward,
                staleTunnelRemoved,
                tunnelRebuilt,
                tunnelCostMs);
        return new TunnelPreparationResult(tunneledUrl);
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

    private static boolean isRetryableTunnelException(SQLException e) {
        String type = classifySqlException(e);
        return "connect_refused".equals(type)
                || "communications".equals(type)
                || "handshake_eof".equals(type)
                || "socket_closed".equals(type);
    }

    private static String classifySqlException(SQLException e) {
        Throwable root = rootCause(e);
        String message = root != null && root.getMessage() != null ? root.getMessage().toLowerCase() : "";
        if (message.contains("connection refused")) {
            return "connect_refused";
        }
        if (message.contains("unexpectedly lost") || message.contains("ssl peer shut down incorrectly") || message.contains("can not read response from server")) {
            return "handshake_eof";
        }
        if (message.contains("communications link failure")) {
            return "communications";
        }
        if (message.contains("socket") && message.contains("closed")) {
            return "socket_closed";
        }
        if (message.contains("statement cancelled") || message.contains("query timeout") || message.contains("timeout")) {
            return "timeout";
        }
        return "sql_exception";
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String safeSqlState(SQLException e) {
        return e == null || e.getSQLState() == null ? "" : e.getSQLState();
    }

    private static boolean closeAndRemoveTunnel(String tunnelKey, String reason) {
        SshTunnel removed = sshTunnels.remove(tunnelKey);
        if (removed != null) {
            removed.close();
            LOGGER.warn("jdbc connection event=ssh_tunnel_reset key={}, reason={}", tunnelKey, reason);
            return true;
        }
        return false;
    }

    private static void resetSshTunnel(DatabaseConfig config, String reason) {
        if (config == null || !config.isUseSshTunnel()) {
            return;
        }
        closeAndRemoveTunnel(buildTunnelKey(config), reason);
    }

    private static String buildTunnelKey(DatabaseConfig config) {
        return config.getSshHost() + ":" + config.getSshPort() + "->" +
                config.getSshLocalHost() + ":" + config.getSshLocalPort();
    }

    private static String applyJdbcTimeouts(String url, DatabaseConfig config) {
        String result = url;
        result = appendJdbcParameter(result, "connectTimeout", config.getConnectTimeoutMs());
        result = appendJdbcParameter(result, "socketTimeout", config.getSocketTimeoutMs());
        return result;
    }

    private static String appendJdbcParameter(String url, String name, int value) {
        if (url == null || url.isEmpty() || value <= 0 || url.contains(name + "=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + name + "=" + value;
    }

    private static int getIntProperty(String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("invalid integer property key={}, value={}, useDefault={}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private static int clampPositiveInt(int configuredValue, int upperBound) {
        if (configuredValue <= 0) {
            return upperBound;
        }
        return Math.min(configuredValue, upperBound);
    }

    private static DatabaseConfig copyDatabaseConfig(DatabaseConfig source) {
        DatabaseConfig copy = new DatabaseConfig();
        copy.setDriverClassName(source.getDriverClassName());
        copy.setUrl(source.getUrl());
        copy.setUsername(source.getUsername());
        copy.setPassword(source.getPassword());
        copy.setConnectTimeoutMs(source.getConnectTimeoutMs());
        copy.setSocketTimeoutMs(source.getSocketTimeoutMs());
        copy.setQueryTimeoutSec(source.getQueryTimeoutSec());
        copy.setSshReconnectRetryTimes(source.getSshReconnectRetryTimes());
        copy.setUseSshTunnel(source.isUseSshTunnel());
        copy.setSshHost(source.getSshHost());
        copy.setSshPort(source.getSshPort());
        copy.setSshUsername(source.getSshUsername());
        copy.setSshPrivateKeyPath(source.getSshPrivateKeyPath());
        copy.setSshLocalHost(source.getSshLocalHost());
        copy.setSshLocalPort(source.getSshLocalPort());
        copy.setSshRemoteHost(source.getSshRemoteHost());
        copy.setSshRemotePort(source.getSshRemotePort());
        return copy;
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
            applyStatementSettings(pstmt, config);
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
            LOGGER.warn("jdbc query failed useSshTunnel={}, errType={}, sqlState={}, vendorCode={}, sql={}",
                    config.isUseSshTunnel(),
                    classifySqlException(e),
                    safeSqlState(e),
                    e.getErrorCode(),
                    summarizeSql(sql),
                    e);
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
            applyStatementSettings(pstmt, config);
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
            applyStatementSettings(pstmt, config);
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
            applyStatementSettings(pstmt, config);

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
            applyStatementSettings(pstmt, config);
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

    private static void applyStatementSettings(PreparedStatement pstmt, DatabaseConfig config) throws SQLException {
        if (pstmt == null || config == null) {
            return;
        }
        if (config.getQueryTimeoutSec() > 0) {
            pstmt.setQueryTimeout(config.getQueryTimeoutSec());
        }
    }

    private static class TunnelPreparationResult {
        private final String url;

        private TunnelPreparationResult(String url) {
            this.url = url;
        }
    }
}
