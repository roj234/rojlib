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
package ilib.util;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class AESUtils {
    public static final byte[] EMPTY = new byte[0];

    public static Cipher getCipher(String type, String key, int mode) {
        try {
            KeyGenerator kgen = KeyGenerator.getInstance(type);
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            random.setSeed(key.getBytes());
            kgen.init(128, random);
            SecretKey secretKey = kgen.generateKey();
            byte[] enCodeFormat = secretKey.getEncoded();
            SecretKeySpec secretKeySpec = new SecretKeySpec(enCodeFormat, type);
            Cipher cipher = Cipher.getInstance(type);// 创建密码器
            cipher.init(mode, secretKeySpec);// 初始化
            return cipher; // 加密
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 加密
     *
     * @param content 需要加密的内容
     * @param key     加密密码
     * @return byte array
     */
    public static byte[] encrypt(byte[] content, String key) {
        Cipher cipher = getCipher("AES", key, Cipher.ENCRYPT_MODE);
        assert cipher != null;
        try {
            return cipher.doFinal(content); // 加密
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
        return EMPTY;
    }

    /**
     * 解密
     *
     * @param content 待解密内容
     * @param key     解密密钥
     * @return byte array
     */
    public static byte[] decrypt(byte[] content, String key) {
        Cipher cipher = getCipher("AES", key, Cipher.DECRYPT_MODE);
        assert cipher != null;
        try {
            return cipher.doFinal(content); // 加密
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
        return EMPTY;
    }

    /**
     * 字符串加密
     *
     * @param content 要加密的字符串
     * @param key     加密的AES Key
     */
    @Deprecated
    public static String encryptString(String content, String key) {
        return byte2hex(encrypt(content.getBytes(StandardCharsets.UTF_8), key));
    }

    /**
     * 字符串解密
     *
     * @param content 要解密的字符串
     * @param key     解密的AES Key
     */
    @Deprecated
    public static String decryptString(String content, String key) {
        byte[] decryptFrom = hex2byte(content);
        byte[] decryptResult = decrypt(decryptFrom, key);
        return new String(decryptResult);
    }


    /**
     * 将16进制转换为二进制
     *
     * @param hexStr hex 0x...
     */
    public static byte[] hex2byte(String hexStr) {
        if (hexStr.length() < 1)
            return null;
        byte[] result = new byte[hexStr.length() / 2];
        for (int i = 0; i < hexStr.length() / 2; i++) {
            int high = Integer.parseInt(hexStr.substring(i * 2, i * 2 + 1), 16);
            int low = Integer.parseInt(hexStr.substring(i * 2 + 1, i * 2 + 2), 16);
            result[i] = (byte) (high * 16 + low);
        }
        return result;
    }


    /**
     * 将二进制转换成16进制
     *
     * @param buf byte
     */
    public static String byte2hex(byte[] buf) {
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            sb.append(hex.toUpperCase());
        }
        return sb.toString();
    }

}
