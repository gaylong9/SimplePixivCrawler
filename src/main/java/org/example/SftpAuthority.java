package org.example;

public class SftpAuthority {
    private String host;
    private int port;
    private String user;
    private String password;

    SftpAuthority(String host, int port, String user, String password) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
    }

    String getHost() {
        return host;
    }

    void setHost(String host) {
        this.host = host;
    }

    int getPort() {
        return port;
    }

    void setPort(int port) {
        this.port = port;
    }

    String getUser() {
        return user;
    }

    void setUser(String user) {
        this.user = user;
    }

    String getPassword() {
        return password;
    }

    void setPassword(String password) {
        this.password = password;
    }
}
