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
package roj.asm.struct;

import roj.asm.struct.simple.MoFNode;
import roj.asm.util.AccessFlag;
import roj.asm.util.ConstantWriter;
import roj.asm.util.FlagList;
import roj.util.ByteWriter;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/12 0:23
 */
public final class AccessData {
    public final String name, superName;
    public final List<String> itf;
    public final List<D> methods;
    public final List<D> fields;
    /**
     * Read only
     */
    public short acc;
    private final int cao;
    private byte[] byteCode;

    public AccessData releaseBCI() {
        byteCode = null;
        return this;
    }

    public AccessData(byte[] byteCode, List<D> methods, List<D> fields, int cao, String name, String superName, List<String> itf) {
        this.byteCode = byteCode;
        this.methods = methods;
        this.fields = fields;
        this.cao = cao;
        this.name = name;
        this.superName = superName;
        this.itf = itf;
    }

    public static final class D implements MoFNode {
        public final String name, desc;
        /**
         * Read only
         */
        public short acc;
        private final int dao;

        public D(String name, String desc, int dao) {
            this.name = name;
            this.desc = desc;
            this.dao = dao;
        }

        @Override
        public void toByteArray(ConstantWriter pool, ByteWriter w) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FlagList accessFlag() {
            return AccessFlag.of(acc);
        }

        @Override
        public short accessFlag2() {
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
            return "D{" + name + ' ' + desc + '}';
        }
    }

    public FlagList getClassFlag() {
        return AccessFlag.of((short) ((byteCode[cao] & 0xff) << 8 | (byteCode[cao + 1] & 0xff)));
    }

    public AccessData setClassFlag(FlagList list) {
        acc = list.flag;
        int val = list.flag & 65535;
        byteCode[cao] = (byte) (val >>> 8);
        byteCode[cao + 1] = (byte) val;
        return this;
    }

    public FlagList getFlagFor(D field) {
        return AccessFlag.of((short) ((byteCode[field.dao] & 0xff) << 8 | (byteCode[field.dao + 1] & 0xff)));
    }

    public AccessData setFlagFor(D field, FlagList list) {
        field.acc = list.flag;
        int val = list.flag & 65535;
        byteCode[field.dao] = (byte) (val >>> 8);
        byteCode[field.dao + 1] = (byte) val;
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
