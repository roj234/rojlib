package roj.net.tcp.util;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/5 19:53
 */
public class CRC32 {

    /**
     * #include<stdio.h>
     * #include<stdlib.h>
     * #include<string.h>
     * #include<stdint.h>
     * <p>
     * uint32_t crc32_table[256];
     * <p>
     * int make_crc32_table()
     * {
     * uint32_t c;
     * int i = 0;
     * int bit = 0;
     * <p>
     * for(i = 0; i < 256; i++)
     * {
     * c  = (uint32_t)i;
     * <p>
     * for(bit = 0; bit < 8; bit++)
     * {
     * if(c&1)
     * {
     * c = (c >> 1)^(0xEDB88320);
     * }
     * else
     * {
     * c =  c >> 1;
     * }
     * <p>
     * }
     * crc32_table[i] = c;
     * }
     * <p>
     * <p>
     * }
     * <p>
     * uint32_t make_crc(uint32_t crc, unsigned char *string, uint32_t size)
     * {
     * <p>
     * while(size--)
     * crc = (crc >> 8)^(crc32_table[(crc ^ *string++)&0xff]);
     * <p>
     * return crc;
     */

    static final int[] crc32_table = new int[256];

    static {
        int i;
        int bit;

        for (i = 0; i < 256; i++) {
            int c = i;

            for (bit = 0; bit < 8; bit++) {
                if ((c & 1) == 1) {
                    c = (c >>> 1) ^ 0xEDB88320;
                } else {
                    c = c >>> 1;
                }

            }
            crc32_table[i] = c;
        }
    }

    public static int crc(int crc, byte b) {
        return (crc >> 8) ^ (crc32_table[(crc ^ b) & 0xff]);
    }

    public static int crc(int crc, byte[] b, int off, int len) {
        len += off;
        final int[] table = crc32_table;
        for (int i = off; i < len; i++) {
            crc = (crc >> 8) ^ (table[(crc ^ b[i]) & 0xff]);
        }
        return crc;
    }
}
