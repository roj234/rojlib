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
package roj.fbt.result;

import roj.collect.MyHashMap;
import roj.fbt.FixedBinaryTag;

import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class TagCompound {
    public final FixedBinaryTag parent;
    private final MyHashMap<String, TagResult> result = new MyHashMap<>(1);

    public TagCompound(FixedBinaryTag parent) {
        this.parent = parent;
    }

    public Map<String, TagResult> getResultMap() {
        return this.result;
    }

    public TagResult getTag(String name) {
        return result.get(name);
    }

    public TagResult getTag(String name, TagResultType type) {
        TagResult r = result.get(name);
        return r != null && r.getType() == type ? r : null;
    }

    public boolean hasKey(String name) {
        return result.containsKey(name);
    }

    public boolean hasKey(String name, TagResultType type) {
        return result.containsKey(name) && result.get(name).getType() == type;
    }

    public double getDouble(String name) {
        TagResult result = getTag(name);
        return result == null ? 0 : result.getDouble();
    }

    public float getFloat(String name) {
        TagResult result = getTag(name);
        return result == null ? 0 : result.getFloat();
    }

    public short getShort(String name) {
        TagResult result = getTag(name);
        return result == null ? 0 : result.getShort();
    }

    public int getInt(String name) {
        TagResult result = getTag(name);
        return result == null ? 0 : result.getInt();
    }

    public long getLong(String name) {
        TagResult result = getTag(name);
        return result == null ? 0 : result.getLong();
    }

    public byte getByte(String name) {
        TagResult result = getTag(name);
        return result == null ? 0 : result.getByte();
    }

    public char getChar(String name) {
        TagResult result = getTag(name);
        return result == null ? 0 : result.getChar();
    }

    public String getString(String name) {
        TagResult result = getTag(name);
        return result == null ? "" : result.getString();
    }

    public byte[] getByteArray(String name) {
        TagResult result = getTag(name);
        return result == null ? TagResult.EMPTY_BYTE_ARRAY : result.getByteArray();
    }

    public TagCompound setString(String name, String value) {
        TagResult result = getTag(name);
        if (result != null && result.getType() == TagResultType.STRING) {
            ((TagResultString) result).result = value;
        }
        return this;
    }

    public TagCompound setByteArray(String name, byte[] value) {
        TagResult result = getTag(name);
        if (result != null && result.getType() == TagResultType.BYTE_ARRAY) {
            ((TagResultByteArray) result).result = value;
        }
        return this;
    }

    public TagCompound setInt(String name, int value) {
        TagResult result = getTag(name);
        if (result != null && result.getType() == TagResultType.INT) {
            ((TagResultInt) result).result = value;
        }
        return this;
    }

    public TagCompound setChar(String name, char value) {
        TagResult result = getTag(name);
        if (result != null && result.getType() == TagResultType.CHAR) {
            ((TagResultChar) result).result = value;
        }
        return this;
    }

    public TagCompound setShort(String name, short value) {
        TagResult result = getTag(name);
        if (result != null && result.getType() == TagResultType.SHORT) {
            ((TagResultShort) result).result = value;
        }
        return this;
    }

    public TagCompound setByte(String name, byte value) {
        TagResult result = getTag(name);
        if (result != null && result.getType() == TagResultType.BYTE) {
            ((TagResultByte) result).result = value;
        }
        return this;
    }

    public TagCompound setLong(String name, long value) {
        TagResult result = getTag(name);
        if (result != null && result.getType() == TagResultType.LONG) {
            ((TagResultLong) result).result = value;
        }
        return this;
    }

    public TagCompound setFloat(String name, float value) {
        TagResult result = getTag(name);
        if (result != null && result.getType() == TagResultType.FLOAT) {
            ((TagResultFloat) result).result = value;
        }
        return this;
    }

    public TagCompound setDouble(String name, double value) {
        TagResult result = getTag(name);
        if (result != null && result.getType() == TagResultType.DOUBLE) {
            ((TagResultDouble) result).result = value;
        }
        return this;
    }
}
