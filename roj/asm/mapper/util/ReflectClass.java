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
package roj.asm.mapper.util;

import roj.asm.tree.IClass;
import roj.asm.tree.MoFNode;
import roj.asm.util.FlagList;
import roj.reflect.ReflectionUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/29 12:58
 */
public class ReflectClass implements IClass {
    private final Class<?> owner;
    private final String className;
    private final List<MoFNode> nodes;
    private List<String> ci, cs;

    public ReflectClass(Class<?> owner, List<MoFNode> nodes) {
        this.owner = owner;
        this.className = owner.getName().replace('.', '/');
        this.nodes = nodes;
    }

    public ReflectClass(String owner, List<MoFNode> nodes) {
        this.owner = null;
        this.className = owner;
        this.nodes = nodes;
    }

    @Override
    public String className() {
        return className;
    }

    @Override
    public String parentName() {
        return owner.getSuperclass().getName().replace('.', '/');
    }

    @Override
    public FlagList accessFlag() {
        return new FlagList(owner.getModifiers());
    }

    @Override
    public List<String> interfaces() {
        if(ci != null) return ci;
        Class<?>[] itf = owner.getInterfaces();
        List<String> arr = Arrays.asList(new String[itf.length]);
        for (int i = 0; i < itf.length; i++) {
            arr.set(i, itf[i].getName().replace('.', '/'));
        }
        return ci = arr;
    }

    @Override
    public List<? extends MoFNode> methods() {
        return nodes;
    }

    @Override
    public List<? extends MoFNode> fields() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public int getMethodByName(String name) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public int getFieldByName(String name) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public byte type() {
        return 12;
    }

    public List<String> i_superClassAll() {
        if(cs != null) return cs;
        List<Class<?>> t = ReflectionUtils.getFathersAndItfOrdered(owner);
        List<String> names = Arrays.asList(new String[t.size()]);
        for (int i = 0; i < t.size(); i++) {
            names.set(i, t.get(i).getName().replace('.', '/'));
        }
        return cs = names;
    }
}
