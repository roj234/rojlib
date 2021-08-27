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

import java.util.Set;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public enum NBTType {
    END(NBTTagEnd.class),
    BYTE(NBTTagByte.class),
    SHORT(NBTTagShort.class),
    INT(NBTTagInt.class),
    LONG(NBTTagLong.class),
    FLOAT(NBTTagFloat.class),
    DOUBLE(NBTTagDouble.class),
    BYTE_ARRAY(NBTTagByteArray.class),
    STRING(NBTTagString.class),
    LIST(NBTTagList.class),
    COMPOUND(NBTTagCompound.class),
    INT_ARRAY(NBTTagIntArray.class);

    private final Class<? extends NBTBase> clazz;

    NBTType(NBTBase nbtBase) {
        this(nbtBase.getClass());
    }

    NBTType(Class<? extends NBTBase> clazz) {
        this.clazz = clazz;
    }

    public static String betterRender(NBTBase tag) {
        StringBuilder sb;
        betterRender(tag, sb = new StringBuilder(100));
        return sb.toString();
    }

    public static void betterRender(NBTBase tag, StringBuilder sb) {
        switch (tag.getId()) {
            case 1: // b
            case 2: // s
            case 3: // i
            case 4: // l
            case 5: // f
            case 6: // d
                sb.append("\u00a7b").append(tag);
                break;
            case 8: // s
                sb.append("\u00a7a").append(tag);
                break;

            case 7: {// ba
                byte[] byteArray = ((NBTTagByteArray) tag).getByteArray();
                sb.append("\u00a7c[");
                if (byteArray.length != 0) {
                    for (int i : byteArray) {
                        sb.append("\u00a7d").append(i).append("\u00a7f,");
                    }
                    sb.delete(sb.length() - 3, sb.length());
                }
                sb.append("\u00a7c]");
            }
            break;
            case 9: { // list
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
            case 10: { // compound
                NBTTagCompound compound = (NBTTagCompound) tag;
                sb.append("\u00a7f{");
                if (!compound.isEmpty()) {
                    Set<String> set = compound.getKeySet();
                    for (String s : set) {
                        sb.append("\u00a7b\"").append(AbstLexer.addSlashes(s)).append("\"\u00a7e: ");
                        betterRender(compound.getTag(s), sb);
                        sb.append("\u00a7a,");
                    }
                    sb.delete(sb.length() - 3, sb.length());
                }
                sb.append("\u00a7f}");
            }
            break;
            case 11: { // ia
                int[] intArray = ((NBTTagIntArray) tag).getIntArray();
                sb.append("\u00a7d[");
                if (intArray.length != 0) {
                    for (int i : intArray) {
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

    public Class<? extends NBTBase> getClazz() {
        return this.clazz;
    }

    public boolean equals(int i) {
        return i == this.ordinal();
    }

    public static Class<? extends NBTBase> getClass(int ord) {
        try {
            return values()[ord].getClazz();
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("No NBTType for " + ord);
        }
    }
}
