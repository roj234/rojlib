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
package roj.kscript.func;

import roj.kscript.Constants;
import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.asm.Frame;
import roj.kscript.type.KInstance;
import roj.kscript.type.KObject;
import roj.kscript.type.KType;
import roj.kscript.type.Type;
import roj.kscript.util.opm.ObjectPropMap;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/16 20:28
 */
public abstract class KFunction extends KObject {
    protected String name, source, clazz;

    public KFunction() {
        super(new ObjectPropMap(), Constants.FUNCTION);
        this.put("prototype", new KObject(Constants.OBJECT));
        this.chmod("prototype", false, true, null, null);
    }

    @Override
    public Type getType() {
        return Type.FUNCTION;
    }

    @Override
    public KFunction asFunction() {
        return this;
    }

    public abstract KType invoke(@Nonnull IObject $this, ArgList param);

    /**
     * 函数名称 <BR>
     *     eg: func
     */
    public String getName() {
        return name == null ? "<anonymous>" : name;
    }

    /**
     * 函数全称 - name <BR>
     *     eg: <global>.funcA.funcB.funcC.
     */
    public String getClassName() {
        return clazz;
    }

    public KFunction set(String source, String name, String clazz) {
        this.source = source;
        this.name = name;
        this.clazz = clazz;
        return this;
    }

    @Override
    public KType copy() {
        return this;
    }

    /**
     * 文件名 <BR>
     *     eg: test.js
     */
    public String getSource() {
        return source == null ? getClass().getSimpleName() + ".java" : source;
    }

    public KType createInstance(ArgList args) {
        return new KInstance(this, get("prototype").asKObject());
    }

    public KFunction onReturn(Frame frame) {
        return this;
    }
}
