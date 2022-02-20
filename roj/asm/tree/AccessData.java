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
package roj.asm.tree;

import roj.asm.Parser;
import roj.util.ByteList;

import java.util.List;

/**
 * Class LOD 1
 *
 * @author Roj234
 * @since 2021/5/12 0:23
 */
public final class AccessData implements IClass {
    public final String name, superName;
    public final List<String> itf;
    public final List<MOF>    methods, fields;
    public char acc;

    private final int cao;
    private final byte[] byteCode;

    public AccessData(byte[] byteCode, List<MOF> fields, List<MOF> methods, int cao, String name, String superName, List<String> itf) {
        this.byteCode = byteCode;
        this.methods = methods;
        this.fields = fields;
        this.cao = cao;
        this.name = name;
        this.superName = superName;
        this.itf = itf;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String parent() {
        return superName;
    }

    @Override
    public List<String> interfaces() {
        return itf;
    }

    @Override
    public List<? extends MoFNode> methods() {
        return methods;
    }

    @Override
    public List<? extends MoFNode> fields() {
        return fields;
    }

    @Override
    public byte type() {
        return Parser.CTYPE_LOD_1;
    }

    public static final class MOF implements MoFNode {
        public final String name, desc;
        /**
         * Read only
         */
        public char acc;
        private final int dao;

        public MOF(String name, String desc, int dao) {
            this.name = name;
            this.desc = desc;
            this.dao = dao;
        }

        // 不想用非静态class
        @Override
        public void accessFlag(int flag) {
            throw new IllegalStateException("Use AccessData.setFlagFor()");
        }

        @Override
        public char accessFlag() {
            return acc;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String rawDesc() {
            return desc;
        }

        @Override
        public int type() {
            return Parser.MFTYPE_LOD1;
        }

        @Override
        public String toString() {
            return "AccessD{" + name + ' ' + desc + '}';
        }
    }

    public char accessFlag() {
        return (char) ((byteCode[cao] & 0xff) << 8 | (byteCode[cao + 1] & 0xff));
    }

    public void accessFlag(int flag) {
        acc = (char) flag;
        byteCode[cao] = (byte) (flag >>> 8);
        byteCode[cao + 1] = (byte) flag;
    }

    public AccessData setFlagFor(MOF node, int flag) {
        node.acc = (char) flag;
        byteCode[node.dao] = (byte) (flag >>> 8);
        byteCode[node.dao + 1] = (byte) flag;
        return this;
    }

    @Override
    public ByteList getBytes(ByteList buf) {
        return buf.put(byteCode);
    }

    public byte[] toByteArray() {
        return this.byteCode;
    }

    @Override
    public String toString() {
        return "AccessData{" +
                "name='" + name + '\'' +
                ", extend='" + superName + '\'' +
                ", impl=" + itf +
                ", methods=" + methods +
                ", fields=" + fields +
                '}';
    }
}
