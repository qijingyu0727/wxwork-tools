package com.util;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class SshTunnel {
    private Session session;
    private String localHost;
    private int localPort;
    private String remoteHost;
    private int remotePort;

    public SshTunnel(String sshHost, int sshPort, String sshUsername, String sshPrivateKeyPath,
                     String localHost, int localPort, String remoteHost, int remotePort) {
        this.localHost = localHost;
        this.localPort = localPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;

        try {
            JSch jsch = new JSch();
            jsch.addIdentity(sshPrivateKeyPath);
            session = jsch.getSession(sshUsername, sshHost, sshPort);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            session.setPortForwardingL(localHost, localPort, remoteHost, remotePort);
        } catch (Exception e) {
            throw new RuntimeException("SSH隧道连接失败", e);
        }
    }

    public void close() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    public String getTunnelUrl(String originalUrl) {
        return originalUrl.replace(remoteHost + ":" + remotePort, localHost + ":" + localPort);
    }
}
