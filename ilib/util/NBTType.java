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

import roj.config.word.AbstLexer;

import net.minecraft.nbt.*;

import java.util.Arrays;
import java.util.Set;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class NBTType {
    public static final int END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11;

    public static String betterRender(NBTBase tag) {
        StringBuilder sb;
        betterRender(tag, sb = new StringBuilder(100));
        return sb.toString();
    }

    public static void betterRender(NBTBase tag, StringBuilder sb) {
        switch (tag.getId()) {
            case BYTE:
            case SHORT:
            case INT:
            case LONG:
            case FLOAT:
            case DOUBLE:
                sb.append("\u00a7b").append(tag);
                break;
            case STRING:
                sb.append("\u00a7a").append(tag);
                break;
            case BYTE_ARRAY: {
                byte[] arr = ((NBTTagByteArray) tag).getByteArray();
                sb.append("\u00a7c[");
                if (arr.length != 0) {
                    for (int i : arr) {
                        sb.append("\u00a7d").append(i).append("\u00a7f,");
                    }
                    sb.delete(sb.length() - 3, sb.length());
                }
                sb.append("\u00a7c]");
            }
            break;
            case LIST: { // list
                NBTTagList list = (NBTTagList) tag;
                sb.append("\u00a7f[");
                if (list.tagCount() != 0) {
                    for (int i = 0; i < list.tagCount(); i++) {
                        betterRender(list.get(i), sb);
                        sb.append("\u00a7f,");
                    }
                    sb.delete(sb.length() - 3, sb.length());
                }
                sb.append("\u00a7f]");
            }
            break;
            case COMPOUND: { // compound
                NBTTagCompound compound = (NBTTagCompound) tag;
                sb.append("\u00a7f{");
                if (!compound.isEmpty()) {
                    Set<String> set = compound.getKeySet();
                    for (String s : set) {
                        AbstLexer.addSlashes(s, sb.append("\u00a7b\"")).append("\"\u00a7e: ");
                        betterRender(compound.getTag(s), sb);
                        sb.append("\u00a7a,");
                    }
                    sb.delete(sb.length() - 3, sb.length());
                }
                sb.append("\u00a7f}");
            }
            break;
            case INT_ARRAY: { // ia
                int[] arr = ((NBTTagIntArray) tag).getIntArray();
                sb.append("\u00a7d[");
                if (arr.length != 0) {
                    for (int i : arr) {
                        sb.append("\u00a7e").append(i).append("\u00a7f,");
                    }
                    sb.delete(sb.length() - 3, sb.length());
                }
                sb.append("\u00a7d]");
            }
            break;
            default:
                throw new IllegalStateException("Unexpected value: " + tag.getId());
        }
    }

    public static int[] getFixedArray(int[] array, int length) {
        if (array.length == length) return array;
        return Arrays.copyOf(array, length);
    }

    public static byte[] getFixedArray(byte[] array, int length) {
        if (array.length == length) return array;
        return Arrays.copyOf(array, length);
    }
}
