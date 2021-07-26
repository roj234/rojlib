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

package roj.asm.tree.attr;

import roj.asm.cst.CstUTF;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public final class AttrStringList extends Attribute {
    public static final String EXCEPTIONS = "Exceptions";
    public static final String NEST_MEMBERS = "NestMembers";

    public final byte isMethod;

    public AttrStringList(String name, int isMethod) {
        super(name);
        classes = new ArrayList<>();
        this.isMethod = (byte) isMethod;
    }

    /**
     * @param isMethod true: {@link roj.asm.tree.Method} false: {@link AttrCode}
     */
    public AttrStringList(String name, ByteReader r, ConstantPool pool, int isMethod) {
        super(name);
        this.isMethod = (byte) isMethod;

        int len = r.readUnsignedShort();
        classes = new ArrayList<>(len);

        int i = 0;
        switch (isMethod) {
            case 0:
                for (; i < len; i++) {
                    classes.add(pool.getName(r));
                }
                break;
            case 1:
                for (; i < len; i++) {
                    classes.add(((CstUTF) pool.get(r)).getString());
                }
                break;
        }
    }

    public final List<String> classes;

    @Override
    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        final List<String> ex = this.classes;

        w.writeShort(ex.size());
        int i = 0;
        switch (isMethod) {
            case 0:
                for (; i < ex.size(); i++) {
                    w.writeShort(pool.getClassId(ex.get(i)));
                }
                break;
            case 1:
                for (; i < ex.size(); i++) {
                    w.writeShort(pool.getUtfId(ex.get(i)));
                }
                break;
        }
    }

    public String toString() {
        return name + ": " + classes;
    }
}