/*
 * This file is a part of MoreItems
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
package roj.asm.util;

import roj.asm.cst.Constant;
import roj.asm.cst.CstUTF;

/**
 * 空着的ConstWriterr
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/10/3 23:02
 */
public final class ConstantPoolEmpty extends ConstantPool {
    @Override
    public int getClassId(String className) {
        return 1;
    }

    @Override
    public int getDescId(String name, String desc) {
        return 1;
    }

    @Override
    public int getDoubleId(double i) {
        return 1;
    }

    @Override
    public int getDynId(int bootstrapTableIndex, String name, String desc) {
        return 1;
    }

    @Override
    public int getFieldRefId(String className, String name, String desc) {
        return 1;
    }

    @Override
    public int getFloatId(float i) {
        return 1;
    }

    @Override
    public int getIntId(int i) {
        return 1;
    }

    @Override
    public int getInvokeDynId(int bootstrapTableIndex, String name, String desc) {
        return 1;
    }

    @Override
    public int getItfRefId(String className, String name, String desc) {
        return 1;
    }

    @Override
    public int getLongId(long i) {
        return 1;
    }

    @Override
    public int getMethodHandleId(String className, String name, String desc, byte kind, byte type) {
        return 1;
    }

    @Override
    public int getMethodRefId(String className, String name, String desc) {
        return 1;
    }

    @Override
    public int getModuleId(String className) {
        return 1;
    }

    @Override
    public int getPackageId(String className) {
        return 1;
    }

    @Override
    public int getUtfId(String msg) {
        return 1;
    }

    @Override
    public <T extends Constant> T reset(T c) {
        return c;
    }

    @Override
    public CstUTF getUtf(String msg) {
        return super.getUtf(msg);
    }
}
