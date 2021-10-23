/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.net;

import roj.math.MathUtils;
import roj.text.CharList;
import roj.text.TextUtil;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/30 23:05
 */
public final class NetworkUtil {
    public static void trustAllCertificates() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext context = SSLContext.getInstance("SSL");
        context.init(null, new TrustManager[]{new TrustAllManager()}, new SecureRandom());

        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        SSLContext.setDefault(context);
    }

    public static void trustAllHosts() {
        HttpsURLConnection.setDefaultHostnameVerifier((host, session) -> true);
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

    // IP Conservation

    public static byte[] IPv42int(CharSequence ip) {
        byte[] arr = new byte[4];

        int found = 0;
        CharList fl = new CharList(5);
        for (int i = 0; i < ip.length(); i++) {
            char c = ip.charAt(i);
            if(c == '.') {
                arr[found++] = (byte) MathUtils.parseInt(fl);
                if(found == 4)
                    throw new RuntimeException("IP format error " + ip);
                fl.clear();
            } else {
                fl.append(c);
            }
        }

        if(fl.length() == 0 || found != 3)
            throw new RuntimeException("IP format error " + ip);
        arr[3] = (byte) MathUtils.parseInt(fl);
        return arr;
    }

    //todo support ::
    public static byte[] IPv62int(CharSequence ip) {
        byte[] arr = new byte[16];

        int found = 0;
        CharList fl = new CharList(5);
        for (int i = 0; i < ip.length(); i++) {
            char c = ip.charAt(i);
            if(c == ':') {
                int st = MathUtils.parseInt(fl);
                arr[found++] = (byte) (st >> 8);
                arr[found++] = (byte) st;

                if(found == 16)
                    throw new RuntimeException("IP format error " + ip);
                fl.clear();
            } else {
                fl.append(c);
            }
        }

        if(fl.length() == 0 || found != 14)
            throw new RuntimeException("IP format error " + ip);
        int st = MathUtils.parseInt(fl);
        arr[14] = (byte) (st >> 8);
        arr[15] = (byte) st;
        return arr;
    }

    public static byte[] ip2bytes(CharSequence ip) {
        return TextUtil.lastIndexOf(ip, '.') != -1 ? IPv42int(ip) : IPv62int(ip);
    }

    public static String bytes2ip(byte[] bytes) {
        if(bytes.length == 4) {
            // IPv4
            return bytes2ipv4(bytes, 0);
        } else {
            // IPv6
            assert bytes.length == 16;

            return bytes2ipv6(bytes, 0);
        }
    }

    public static String bytes2ipv6(byte[] bytes, int off) {
        // xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx
        CharList sb = new CharList();
        for (int i = 0; i < 8; i ++) {
            sb.append(Integer.toHexString((0xFF & bytes[off++]) << 8 | (bytes[off++] & 0xFF))).append(':');
        }
        sb.setIndex(sb.length() - 1);

        return sb.toString();
    }

    public static String bytes2ipv4(byte[] bytes, int off) {
        return String.valueOf(bytes[off++] & 0xFF) +
                '.' +
                (bytes[off++] & 0xFF) +
                '.' +
                (bytes[off++] & 0xFF) +
                '.' +
                (bytes[off] & 0xFF);
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
