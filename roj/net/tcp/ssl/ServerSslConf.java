package roj.net.tcp.ssl;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/2/6 22:11
 */
public class ServerSslConf implements SslConfig {
    private final String keyStore;
    private final char[] password;

    public ServerSslConf(String keyStore, char[] password) {
        this.keyStore = keyStore;
        this.password = password;
    }

    @Override
    public boolean isServerSide() {
        return true;
    }

    @Override
    public boolean isNeedClientAuth() {
        return false;
    }

    @Override
    public String getPkPath() {
        return keyStore;
    }

    @Override
    public String getCaPath() {
        return keyStore;
    }

    @Override
    public char[] getPasswd() {
        return password;
    }
}
