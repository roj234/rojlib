package roj.asm.util;

import roj.asm.cst.Constant;
import roj.util.ByteWriter;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/1/16 23:33
 */
public final class ConstantWriterEmpty extends ConstantWriter {
    public ConstantWriterEmpty() {
    }

    void addConstant(Constant c) {}

    @Override
    public int getUtfId(String msg) {
        return 0;
    }

    @Override
    public int getDescId(String name, String desc) {
        return 0;
    }

    @Override
    public int getClassId(String className) {
        return 0;
    }

    @Override
    public int getMethodRefId(String className, String name, String desc) {
        return 0;
    }

    @Override
    public int getFieldRefId(String className, String name, String desc) {
        return 0;
    }

    @Override
    public int getItfRefId(String className, String name, String desc) {
        return 0;
    }

    @Override
    public int getMethodHandleId(String className, String name, String desc, byte kind, byte type) {
        return 0;
    }

    @Override
    public int getInvokeDynId(int bootstrapTableIndex, String name, String desc) {
        return 0;
    }

    @Override
    public int getDynId(int bootstrapTableIndex, String name, String desc) {
        return 0;
    }

    @Override
    public int getPackageId(String className) {
        return 0;
    }

    @Override
    public int getModuleId(String className) {
        return 0;
    }

    @Override
    public int getIntId(int i) {
        return 0;
    }

    @Override
    public int getDoubleId(double i) {
        return 0;
    }

    @Override
    public int getFloatId(float i) {
        return 0;
    }

    @Override
    public int getLongId(long i) {
        return 0;
    }

    @Override
    public <T extends Constant> T reset(T c) {
        return c;
    }

    @Override
    public void writeTo(ByteWriter writer) {}
}
