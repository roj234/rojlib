package roj.net;

import roj.text.TextUtil;

import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/10/30 23:05
 */
public final class NetworkUtil {
    public static void trustAllCertificates() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext context = SSLContext.getInstance("SSL");
        context.init(null, new TrustManager[]{new TrustAllManager()}, new SecureRandom());

        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
    }

    public static void trustAllHosts() {
        HttpsURLConnection.setDefaultHostnameVerifier((HostnameVerifier) (host, session) -> true);
    }

    public static int number2hex(int i, byte[] buf) {
        int pos = 7;

        final byte[] digits = TextUtil.digits;

        while (i > 15) {
            buf[pos--] = digits[i & 15];
            i >>>= 4;
        }
        buf[pos] = digits[i & 15];

        return pos;
    }

    // javax.net.ssl.X509TrustManager
    public static final class TrustAllManager implements X509TrustManager {
        /**
         * //dummy
         *
         * @param chain    同位体的证书链
         * @param authType 基于客户端证书的验证类型
         * @throws IllegalArgumentException 如果 null 或长度为零的 chain 传递给 chain 参数，或者 null 或长度为零的字符串传递给 authType 参数
         * @throws CertificateException     如果证书链不受此 TrustManager 信任。
         */
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        /**
         * 给出同位体提供的部分或完整的证书链，构建到可信任的根的证书路径，并且返回是否可以确认和信任将其用于基于验证类型的服务器 SSL 验证。
         * 验证类型是表示为一个 String 的密码套件的密钥交换算法部分，例如 "RSA"、"DHE_DSS"。
         * 注：对于一些可输出的密码套件，密钥交换算法是在运行时的联络期间确定的。
         * 例如，对于 TLS_RSA_EXPORT_WITH_RC4_40_MD5，当临时的 RSA 密钥 用于密钥交换时 authType 应为 RSA_EXPORT，当使用来自服务器证书的密钥时 authType 应为 RSA。
         * 检查是否大小写敏感的。
         *
         * @param chain    同位体的证书链
         * @param authType 使用的密钥交换算法
         * @throws IllegalArgumentException 如果 null 或长度为零的 chain 传递给 chain 参数，或者 null 或长度为零的字符串传递给 authType 参数
         * @throws CertificateException     如果证书链不受此 TrustManager 信任。
         */
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        /**
         * 返回受验证同位体信任的认证中心的数组。
         *
         * @return 可接受的 CA 发行者证书的非 null（可能为空）的数组。
         */
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
