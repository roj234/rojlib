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

import roj.asm.util.AccessFlag;
import roj.asm.util.ConstantPool;
import roj.asm.util.FlagList;
import roj.util.ByteList;

import java.util.Collections;
import java.util.List;

/**
 * Class LOD 1
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/12 0:23
 */
public final class AccessData implements IClass {
    public final String name, superName;
    public final List<String> itf;
    public final List<MOF>    methods, fields;
    /**
     * Read only
     */
    public char acc;
    private final int cao;
    private byte[] byteCode;

    public AccessData releaseBCI() {
        byteCode = null;
        return this;
    }

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
    public String className() {
        return name;
    }

    @Override
    public String parentName() {
        return superName;
    }

    @Override
    public List<String> interfaces() {
        return Collections.unmodifiableList(itf);
    }

    @Override
    public List<? extends MoFNode> methods() {
        return methods;
    }

    @Override
    public List<? extends MoFNode> fields() {
        return fields;
    }

    public int getMethodByName(String key) {
        for (int i = 0; i < methods.size(); i++) {
            MoFNode ms = methods.get(i);
            if (ms.name().equals(key)) return i;
        }
        return -1;
    }

    public int getFieldByName(String key) {
        for (int i = 0; i < fields.size(); i++) {
            MoFNode fs = fields.get(i);
            if (fs.name().equals(key)) return i;
        }
        return -1;
    }

    @Override
    public byte type() {
        return 2;
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

        @Override
        public void toByteArray(ConstantPool pool, ByteList w) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FlagList accessFlag() {
            return AccessFlag.of(acc);
        }

        @Override
        public char accessFlag2() {
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
            return 0;
        }

        @Override
        public String toString() {
            return "AccessD{" + name + ' ' + desc + '}';
        }
    }

    public FlagList accessFlag() {
        return AccessFlag.of((short) ((byteCode[cao] & 0xff) << 8 | (byteCode[cao + 1] & 0xff)));
    }

    public void accessFlag(FlagList list) {
        acc = list.flag;
        int val = list.flag;
        byteCode[cao] = (byte) (val >>> 8);
        byteCode[cao + 1] = (byte) val;
    }

    public AccessData setFlagFor(MOF node, FlagList list) {
        node.acc = list.flag;
        int val = list.flag;
        byteCode[node.dao] = (byte) (val >>> 8);
        byteCode[node.dao + 1] = (byte) val;
        return this;
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
