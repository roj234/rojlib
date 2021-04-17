package roj.net.tcp.ssl;

import javax.net.ssl.SSLEngine;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/2/5 0:33
 */
public abstract class EngineAllocator {
    protected final SslConfig config;

    public EngineAllocator(SslConfig config) {
        this.config = config;
    }

    protected static void config(SSLEngine sslEngine, SslConfig cfg) {
        sslEngine.setUseClientMode(!cfg.isServerSide());
        sslEngine.setEnabledProtocols(new String[] { "TLSv1", "TLSv1.1", "TLSv1.2" });
        // false为单向认证，true为双向认证
        sslEngine.setNeedClientAuth(cfg.isNeedClientAuth());
    }

    public abstract SSLEngine allocate();
}
