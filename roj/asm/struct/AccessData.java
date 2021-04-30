package roj.asm.struct;

import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;

import java.util.Collections;
import java.util.List;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: AccessData.java
 */
public final class AccessData {
    public final String name, superName;
    public final List<String> itf;
    public final List<D> methods;
    public final List<D> fields;
    private final int cao;
    private final byte[] byteCode;

    public AccessData(byte[] byteCode, List<D> methods, List<D> fields, int cao, String name, String superName, List<String> itf) {
        this.byteCode = byteCode;
        this.methods = methods;
        this.fields = fields;
        this.cao = cao;
        this.name = name;
        this.superName = superName;
        this.itf = Collections.unmodifiableList(itf);
    }

    public static final class D {
        public final String name, desc;
        private final int dao;

        public D(String name, String desc, int dao) {
            this.name = name;
            this.desc = desc;
            this.dao = dao;
        }

        @Override
        public String toString() {
            return "D{" + name + ' ' + desc + '}';
        }
    }

    public FlagList getClassFlag() {
        return AccessFlag.parse((short) ((byteCode[cao] & 0xff) << 8 | (byteCode[cao + 1] & 0xff)));
    }

    public AccessData setClassFlag(FlagList list) {
        int val = list.flag & 65535;
        byteCode[cao] = (byte) (val >>> 8);
        byteCode[cao + 1] = (byte) val;
        return this;
    }

    public FlagList getFlagFor(D field) {
        return AccessFlag.parse((short) ((byteCode[field.dao] & 0xff) << 8 | (byteCode[field.dao + 1] & 0xff)));
    }

    public AccessData setFlagFor(D field, FlagList list) {
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
