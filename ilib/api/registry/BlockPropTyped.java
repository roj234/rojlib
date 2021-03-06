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

package ilib.api.registry;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

import net.minecraft.block.properties.IProperty;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/2 23:42
 */
public class BlockPropTyped<T extends Propertied<T>> implements IProperty<T>, IRegistry<T> {
    protected final List<T> allowedValues = new ArrayList<>();
    protected T[] allowValues = null;
    protected final Map<String, T> nameToValue = Maps.newHashMap();
    private final String name;
    private final Class<T> propClass;

    protected BlockPropTyped(String name, Class<T> propClass) {
        this.name = name;
        this.propClass = propClass;
    }

    public BlockPropTyped(String name, IRegistry<T> wrapper) {
        this(name, wrapper.getValueClass(), wrapper.values());
    }

    public BlockPropTyped(IRegistry<T> wrapper) {
        this("type", wrapper.getValueClass(), wrapper.values());
    }

    @SuppressWarnings("unchecked")
    public BlockPropTyped(T... allowValues) {
        this("type", (Class<T>) (allowValues.length == 0 ? throwException() : allowValues[0].getClass()), allowValues);
    }

    public static Class<?> throwException() {
        throw new IllegalArgumentException("No allow values");
    }

    public BlockPropTyped(String name, Class<T> propClass, T[] allowValues) {
        this.name = name;
        this.propClass = propClass;

        if (allowValues.length == 0) throw new IllegalArgumentException("No allow values");
        this.allowValues = allowValues;
        for (T oneEnum : allowValues) {
            if (oneEnum == null)
                throw new NullPointerException("allowValues[i]");

            String _name = oneEnum.getName();
            if (this.nameToValue.containsKey(_name)) {
                throw new IllegalArgumentException("Multiple values have the same name '" + _name + "'");
            }
            this.allowedValues.add(oneEnum);
            this.nameToValue.put(_name, oneEnum);
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return this.name;
    }

    @Nonnull
    @Override
    public Class<T> getValueClass() {
        return this.propClass;
    }

    @Nonnull
    @Override
    public List<T> getAllowedValues() {
        return this.allowedValues;
    }

    @Override
    public T byId(int id) {
        if (id < 0 || id > allowedValues.size())
            return null;
        return allowedValues.get(id);
    }

    @Override
    public T[] values() {
        return allowValues;
    }

    @Nonnull
    @Override
    public Optional<T> parseValue(@Nonnull String p_parseValue_1_) {
        return Optional.fromNullable(this.nameToValue.get(p_parseValue_1_));
    }

    @Nonnull
    @Override
    public String getName(T p_getName_1_) {
        return p_getName_1_.getName();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append('[').append(getName()).append(']').append('{');
        for (T t : allowedValues) {
            sb.append(t.getName()).append(",");
        }

        return sb.append('}').toString();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof BlockPropTyped) {
            BlockPropTyped<?> lvt_2_1_ = (BlockPropTyped<?>) obj;
            return (this.name.equals(lvt_2_1_.name)) && (this.propClass == lvt_2_1_.propClass) && (this.allowedValues.equals(lvt_2_1_.allowedValues)) && (this.nameToValue.equals(lvt_2_1_.nameToValue));
        }
        return false;
    }

    public int hashCode() {
        int code = 31 * this.name.hashCode();
        code = 31 * code + this.propClass.hashCode();
        return code;
    }
}