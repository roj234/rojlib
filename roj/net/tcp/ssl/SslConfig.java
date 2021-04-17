package roj.net.tcp.ssl;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/2/5 0:28
 */
public interface SslConfig {
    boolean isServerSide();
    boolean isNeedClientAuth();

    String getPkPath();
    String getCaPath();
    char[] getPasswd();

    default boolean preferNetty() {
        return false;
    }
    default Object getAllocator() {
        return null;
    }
}
