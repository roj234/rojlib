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

package roj.asm.tree.insn;

import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.collect.SimpleList;

import java.util.List;

/**
 * 抽象，方法执行
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public abstract class IInvokeInsnNode extends InsnNode {
    public IInvokeInsnNode(byte code) {
        super(code);
    }

    public String name;
    String rawDesc;
    List<Type> params;
    Type returnType;

    final void initPar() {
        if (params == null) {
            if (rawDesc.startsWith("()")) {
                params = new SimpleList<>();
                returnType = ParamHelper.parseReturn(rawDesc);
            } else {
                params = ParamHelper.parseMethod(rawDesc);
                returnType = params.remove(params.size() - 1);
            }
        }
    }

    public final Type returnType() {
        initPar();
        return returnType;
    }

    public final List<Type> parameters() {
        initPar();
        return params;
    }

    public final String rawDesc() {
        if (params != null) {
            params.add(returnType);
            rawDesc = ParamHelper.getMethod(params, rawDesc);
            params.remove(params.size() - 1);
        }
        return rawDesc;
    }

    /**
     * (I)Lasm/util/ByteWriter;
     *
     * @param param java规范中的方法参数描述符
     */
    public final void rawDesc(String param) {
        this.rawDesc = param;
        if (params != null) {
            params.clear();
            ParamHelper.parseMethod(param, params);
            returnType = params.remove(params.size() - 1);
        }
    }

    /**
     * asm/util/ByteWriter.putShort:(I)Lasm/util/ByteWriter;
     *
     * @param desc javap格式的描述符
     */
    public abstract void fullDesc(String desc);
}