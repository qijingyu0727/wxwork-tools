package com.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class JdbcUtils {

    private static final String DRIVER_CLASS_NAME;
    private static final String URL;
    private static final String USERNAME;
    private static final String PASSWORD;

    static {
        Properties props = new Properties();
        try (InputStream input = new java.io.FileInputStream("/opt/wxwork-tools/wxwork-tools.properties")) {
            props.load(input);
            DRIVER_CLASS_NAME = props.getProperty("cscrm.datasource.driver-class-name");
            URL = props.getProperty("cscrm.datasource.url");
            USERNAME = props.getProperty("cscrm.datasource.username");
            PASSWORD = props.getProperty("cscrm.datasource.password");
            
            try {
                Class.forName(DRIVER_CLASS_NAME);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("数据库驱动加载失败", e);
            }
        } catch (IOException e) {
            throw new RuntimeException("加载配置文件失败，请确保 /opt/wxwork-tools/wxwork-tools.properties 文件存在", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
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
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        List<Object[]> result = new ArrayList<>();

        try {
            conn = getConnection();
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
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Object result = null;

        try {
            conn = getConnection();
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
        Connection conn = null;
        PreparedStatement pstmt = null;
        int result = 0;

        try {
            conn = getConnection();
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
        return update(sql, params);
    }

    public static int delete(String sql, Object... params) {
        return update(sql, params);
    }

    public static int[] batchUpdate(String sql, List<Object[]> paramsList) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        int[] result = null;

        try {
            conn = getConnection();
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
        Connection conn = null;
        PreparedStatement pstmt = null;
        boolean result = false;

        try {
            conn = getConnection();
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
}