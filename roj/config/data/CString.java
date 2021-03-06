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
package roj.config.data;

import roj.config.YAMLParser;
import roj.config.serial.StreamSerializer;
import roj.config.serial.Structs;
import roj.config.word.AbstLexer;
import roj.math.MathUtils;
import roj.util.ByteList;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class CString extends CEntry {
    public String value;

    public CString(String string) {
        this.value = string;
    }

    @Nonnull
    @Override
    public Type getType() {
        return Type.STRING;
    }

    public static CString valueOf(String s) {
        return new CString(s);
    }

    @Nonnull
    @Override
    public String asString() {
        return value;
    }

    @Override
    public double asDouble() {
        return Double.parseDouble(value);
    }

    @Override
    public int asInteger() {
        return MathUtils.parseInt(value);
    }

    @Override
    public long asLong() {
        return Long.parseLong(value);
    }

    @Override
    public boolean asBool() {
        return Boolean.parseBoolean(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CString that = (CString) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return value == null ? 0 : value.hashCode();
    }

    @Override
    public boolean isSimilar(CEntry o) {
        return o.getType() == Type.STRING || (o.getType().isSimilar(Type.STRING) && o.asString().equals(value));
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        if(!NO_RAW_CHECK || !rawSafe(value))
            return toJSON(sb, depth);
        return sb.append(value);
    }

    static final boolean NO_RAW_CHECK = System.getProperty("roj.config.noRawCheck") == null;
    static boolean rawSafe(CharSequence value) {
        if(value.length() == 0)
            return false;
        if(value.length() >= 4 && value.length() <= 5) {
            switch (value.toString()) {
                case "null":
                case "Null":
                case "NULL":
                case "true":
                case "True":
                case "TRUE":
                case "false":
                case "False":
                case "FALSE":
                    return false;
            }
        }
        if((value.length() == 1 ? YAMLParser.YAML_ESCAPE_SINGLE : YAMLParser.YAML_ESCAPE_MULTI).indexOf(value.charAt(0)) != -1)
            return false;

        char last = value.charAt(value.length() - 1);
        if (AbstLexer.WHITESPACE.contains(last)) {
            return false;
        }

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\r':
                case '\n':
                    return false;
                case ':':
                    if(i != 0)
                        return false;
            }
        }
        return true;
    }

    @Override
    protected StringBuilder toXML(StringBuilder sb, int depth) {
        return value == null ? sb.append(value) : !value.startsWith("<![CDATA[") && value.indexOf('<') >= 0 ?
                sb.append("<![CDATA[").append(value).append("]]>") :
                sb.append(value);
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        return value == null ? sb.append(value) : AbstLexer.addSlashes(value, sb.append('"')).append('"');
    }

    @Override
    public Object unwrap() {
        return value;
    }

    @Override
    public void toBinary(ByteList w, Structs struct) {
        if (value == null) {
            w.put((byte) Type.NULL.ordinal());
        } else {
            w.put((byte) Type.STRING.ordinal()).putVIVIC(value);
        }
    }

    @Override
    public void serialize(StreamSerializer ser) {
        ser.value(value);
    }
}
