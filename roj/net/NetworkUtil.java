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

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

/**
 * @author Roj234
 * @version 0.1
 * @since  2020/10/30 23:05
 */
public final class NetworkUtil {
    public static void trustAllCertificates() {
        try {
            SSLContext.getDefault().init(null, new TrustManager[] {new TrustAllManager()}, null);
        } catch (NoSuchAlgorithmException | KeyManagementException ignored) {}
        // should not happen
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

    public static final class TrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
