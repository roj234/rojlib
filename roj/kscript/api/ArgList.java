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
package roj.kscript.api;

import roj.kscript.Constants;
import roj.kscript.func.KFunction;
import roj.kscript.type.KInt;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;
import roj.kscript.type.Type;
import roj.kscript.util.JavaException;
import roj.kscript.vm.KScriptVM;
import roj.math.MathUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/27 22:44
 */
public abstract class ArgList implements IObject {
    public List<KType> argv;

    @Nonnull
    public final KType get(int i) {
        return i < 0 || i >= argv.size() ? KUndefined.UNDEFINED : argv.get(i);
    }

    @Nullable
    public abstract KFunction caller();

    public final int getOr(int i, int def) {
        return i < 0 || i >= argv.size() ? def : argv.get(i).asInt();
    }

    public final double getOr(int i, double def) {
        return i < 0 || i >= argv.size() ? def : argv.get(i).asDouble();
    }

    public final String getOr(int i, String def) {
        return i < 0 || i >= argv.size() ? def : argv.get(i).asString();
    }

    public final boolean getOr(int i, boolean def) {
        return i < 0 || i >= argv.size() ? def : argv.get(i).asBool();
    }

    public final KType getOr(int i, KType def) {
        return i < 0 || i >= argv.size() ? def : argv.get(i);
    }

    public final <T> T getObject(int i, Class<T> t, T def) {
        return i < 0 || i >= argv.size() ? def : argv.get(i).asJavaObject(t).getOr(def);
    }

    public final <T> T getObject(int i, Class<T> t) {
        return getObject(i, t, null);
    }

    public List<StackTraceElement> trace() {
        ArrayList<StackTraceElement> collector = new ArrayList<>();
        trace(collector);
        return collector;
    }

    public abstract void trace(List<StackTraceElement> collector);

    public final int size() {
        return argv.size();
    }

    // just ignore it
    public void put(@Nonnull String key, KType entry) {}

    public boolean isInstanceOf(IObject obj) {
        return obj instanceof ArgList;
    }

    public final IObject getProto() {
        return Constants.OBJECT;
    }

    public KType getOr(String key, KType def) {
        if("length".equals(key)) {
            return KInt.valueOf(argv.size());
        }

        int[] arr = KScriptVM.retainNumParseTmp(10);
        if(MathUtils.parseIntErrorable(key, arr)) {
            int i = arr[0];
            return i < 0 || i >= argv.size() ? KUndefined.UNDEFINED : argv.get(i);
        }
        throw new JavaException("无效的参数, 需要'length'或int32数字");
    }

    public final List<KType> getInternal() {
        return argv;
    }

    @Override
    public final boolean asBool() {
        return true;
    }

    public final Type getType() {
        return Type.OBJECT;
    }

    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append("[arguments: ").append(argv).append("]");
    }

    public boolean canCastTo(Type type) {
        return type == Type.OBJECT || type == Type.STRING;
    }
}
