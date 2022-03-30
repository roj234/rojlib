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

import roj.asm.type.Type;
import roj.collect.MyBitSet;
import roj.math.MathUtils;
import roj.reflect.DirectAccessor;
import roj.text.CharList;
import roj.text.TextUtil;
import sun.net.InetAddressCachePolicy;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;

/**
 * @author Roj234
 * @since  2020/10/30 23:05
 */
public final class NetworkUtil {
    public static void trustAllCertificates() {
        try {
            SSLContext.getDefault().init(null, new TrustManager[] {new TrustAllManager()}, null);
        } catch (NoSuchAlgorithmException | KeyManagementException ignored) {}
        // should not happen
    }

    static InetAddress any;
    public static InetAddress anyLocalAddress() {
        if (any != null) return any;
        return any = new InetSocketAddress(0).getAddress();
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

    static final MyBitSet HEX = MyBitSet.from("0123456789ABCDEFabcdef");
    public static byte[] IPv62int(CharSequence ip) {
        int len = TextUtil.limitedIndexOf(ip, '%', ip.length());
        byte[] arr = new byte[16];

        int j = 0, colon = -1;
        CharList fl = new CharList(5);
        for (int i = 0; i < len; i++) {
            char c = ip.charAt(i);
            if(c == ':') {
                if (fl.length() == 0) {
                    if (i == 0) throw new IllegalArgumentException("Not support :: at first");
                    if (ip.charAt(i-1) != ':') throw new IllegalArgumentException("Single ':': " + ip);
                    if (colon >= 0) throw new IllegalArgumentException("More than one ::");
                    colon = j;
                    continue;
                }
                int st = MathUtils.parseInt(fl, 16);
                arr[j++] = (byte) (st >> 8);
                arr[j++] = (byte) st;

                if(j == 16) throw new IllegalArgumentException("Address overflow: " + ip);
                fl.clear();
            } else if (HEX.contains(c)) {
                fl.append(c);
            } else {
                throw new IllegalArgumentException("Invalid character at " + i + ": " + ip);
            }
        }

        if((colon == -1 && (fl.length() == 0 || j != 14)) || j > 14)
            throw new IllegalArgumentException("Address overflow: " + ip);
        int st = MathUtils.parseInt(fl, 16);
        arr[j++] = (byte) (st >> 8);
        arr[j  ] = (byte) st;

        if (colon >= 0) {
            len = j - colon + 1;
            for (int i = 1; i <= len; i++) {
                arr[16 - i] = arr[j - i + 1];
                arr[j - i + 1] = 0;
            }
        }

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
        sb.setLength(sb.length() - 1);

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

    public static void putHostCache(boolean negative, String host, long expire, InetAddress... addresses) {
        initUtil();
        synchronized (CacheUtil.getHostCache()) {
            Object cache = CacheUtil.newCacheEntry(addresses, expire);
            CacheUtil.getInternalMap(negative ? CacheUtil.getNegativeHostCache() : CacheUtil.getHostCache()).put(host, cache);
        }
    }

    public static void setHostCachePolicy(boolean negative, int seconds) {
        if (seconds < 0) seconds = -1;
        if (negative) {
            PolicyUtil.setNegCachePolicy(seconds);
            PolicyUtil.setNegCacheSet(true);
        } else {
            PolicyUtil.setPosCachePolicy(seconds);
            PolicyUtil.setPosCacheSet(true);
        }
    }

    static void initUtil() {
        if (CacheUtil == null) {
            synchronized (NetworkUtil.class) {
                if (CacheUtil == null) {
                    try {
                        CacheUtil = DirectAccessor
                            .builder(H.class)
                            .i_construct("java.net.InetAddress$CacheEntry",
                                         "([Ljava/net/InetAddress;J)V", "newCacheEntry")
                            .access(InetAddress.class, new String[] {"addressCache", "negativeCache"},
                                    new String[] {"getHostCache", "getNegativeCache"}, null)
                            .i_access("java.net.InetAddress$Cache", "cache",
                                      new Type("java/util/LinkedHashMap"), "getInternalMap", null, false)
                            .build();
                    } catch (Throwable e) {
                        CacheUtil = (a, b) -> null;
                    }
                    try {
                        Class<?> pl = InetAddressCachePolicy.class;
                        String[] fieldName = new String[] {
                                "cachePolicy", "negativeCachePolicy",
                                "propertySet", "propertyNegativeSet"
                        };
                        try {
                            pl.getDeclaredField("propertySet");
                        } catch (NoSuchFieldException e) {
                            fieldName[2] = "set";
                            fieldName[3] = "negativeSet";
                        }
                        PolicyUtil = DirectAccessor
                                .builder(H.class)
                                .access(pl, fieldName, null,
                                        new String[] {"setPosCachePolicy", "setNegCachePolicy",
                                                      "setPosCacheSet", "setNegCacheSet"})
                                .build();
                    } catch (Throwable e) {
                        PolicyUtil = (a, b) -> null;
                    }
                }
            }
        }
    }
    static H CacheUtil, PolicyUtil;
    private interface H {
        Object newCacheEntry(InetAddress[] addresses, long expire);
        default Object getHostCache() {
            throw new UnsupportedOperationException("Failed to get the field");
        }
        default Object getNegativeHostCache() {
            throw new UnsupportedOperationException("Failed to get the field");
        }
        default LinkedHashMap<String, Object> getInternalMap(Object cache) {
            throw new UnsupportedOperationException("Failed to get the field");
        }

        default void setPosCachePolicy(int seconds) {
            throw new UnsupportedOperationException("Failed to get the field");
        }
        default void setNegCachePolicy(int seconds) {
            throw new UnsupportedOperationException("Failed to get the field");
        }
        default void setPosCacheSet(boolean set) {
            throw new UnsupportedOperationException("Failed to get the field");
        }
        default void setNegCacheSet(boolean set) {
            throw new UnsupportedOperationException("Failed to get the field");
        }
    }
}