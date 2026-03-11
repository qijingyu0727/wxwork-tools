package com.util;

public class DatabaseConfig {
    private String driverClassName;
    private String url;
    private String username;
    private String password;
    private int connectTimeoutMs;
    private int socketTimeoutMs;
    private int queryTimeoutSec;
    private int sshReconnectRetryTimes;
    private boolean useSshTunnel;
    private String sshHost;
    private int sshPort;
    private String sshUsername;
    private String sshPrivateKeyPath;
    private String sshLocalHost;
    private int sshLocalPort;
    private String sshRemoteHost;
    private int sshRemotePort;

    public DatabaseConfig() {
    }

    public DatabaseConfig(String driverClassName, String url, String username, String password) {
        this.driverClassName = driverClassName;
        this.url = url;
        this.username = username;
        this.password = password;
        this.useSshTunnel = false;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isUseSshTunnel() {
        return useSshTunnel;
    }

    public void setUseSshTunnel(boolean useSshTunnel) {
        this.useSshTunnel = useSshTunnel;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    public void setSocketTimeoutMs(int socketTimeoutMs) {
        this.socketTimeoutMs = socketTimeoutMs;
    }

    public int getQueryTimeoutSec() {
        return queryTimeoutSec;
    }

    public void setQueryTimeoutSec(int queryTimeoutSec) {
        this.queryTimeoutSec = queryTimeoutSec;
    }

    public int getSshReconnectRetryTimes() {
        return sshReconnectRetryTimes;
    }

    public void setSshReconnectRetryTimes(int sshReconnectRetryTimes) {
        this.sshReconnectRetryTimes = sshReconnectRetryTimes;
    }

    public String getSshHost() {
        return sshHost;
    }

    public void setSshHost(String sshHost) {
        this.sshHost = sshHost;
    }

    public int getSshPort() {
        return sshPort;
    }

    public void setSshPort(int sshPort) {
        this.sshPort = sshPort;
    }

    public String getSshUsername() {
        return sshUsername;
    }

    public void setSshUsername(String sshUsername) {
        this.sshUsername = sshUsername;
    }

    public String getSshPrivateKeyPath() {
        return sshPrivateKeyPath;
    }

    public void setSshPrivateKeyPath(String sshPrivateKeyPath) {
        this.sshPrivateKeyPath = sshPrivateKeyPath;
    }

    public String getSshLocalHost() {
        return sshLocalHost;
    }

    public void setSshLocalHost(String sshLocalHost) {
        this.sshLocalHost = sshLocalHost;
    }

    public int getSshLocalPort() {
        return sshLocalPort;
    }

    public void setSshLocalPort(int sshLocalPort) {
        this.sshLocalPort = sshLocalPort;
    }

    public String getSshRemoteHost() {
        return sshRemoteHost;
    }

    public void setSshRemoteHost(String sshRemoteHost) {
        this.sshRemoteHost = sshRemoteHost;
    }

    public int getSshRemotePort() {
        return sshRemotePort;
    }

    public void setSshRemotePort(int sshRemotePort) {
        this.sshRemotePort = sshRemotePort;
    }
}
