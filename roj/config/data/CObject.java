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
package roj.config.data;

import roj.config.serial.Serializer;
import roj.config.serial.Serializers;
import roj.util.ByteList;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Config Java Object
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class CObject<T> extends CMapping {
    public T value;

    public CObject(T object) {
        this.value = object;
    }

    @SuppressWarnings("unchecked")
    public CObject(Map<String, CEntry> map, Serializer<?> deser) {
        super(map);
        this.value = (T) deser.deserialize(this);
    }

    @Nonnull
    @Override
    public Type getType() {
        return Type.OBJECT;
    }

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public <O> CObject<O> asObject(Class<O> clazz) {
        if (this.value == null)
            return (CObject<O>) this;
        else {
            if (clazz.isInstance(value)) {
                return (CObject<O>) this;
            }
            throw new ClassCastException(value.getClass() + " to " + clazz.getName());
        }
    }

    public void serialize() {
        map.clear();
        if (null != value) {
            map.put("==", new CString(value.getClass().getName()));
            Serializers.DEFAULT.find(value.getClass().getName()).serialize(this, value);
        }
    }

    @SuppressWarnings("unchecked")
    public void deserialize() {
        value = (T) Serializers.DEFAULT.find(getString("==")).deserialize(this);
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        if (this.value == null) return sb.append("null");
        serialize();

        return super.toJSON(sb, depth);
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        if (this.value == null) return sb.append("null");
        serialize();

        return super.toYAML(sb, depth);
    }

    @Override
    public StringBuilder toINI(StringBuilder sb, int depth) {
        if (this.value == null) return sb.append("null");
        serialize();

        return super.toINI(sb, depth);
    }

    @Override
    public StringBuilder toTOML(StringBuilder sb, int depth, CharSequence chain) {
        if (this.value == null) return sb.append("null");
        serialize();

        return super.toTOML(sb, depth, chain);
    }

    @Override
    public Object unwrap() {
        return this.value;
    }

    @Override
    public void toBinary(ByteList w) {
        if (this.value == null) {
            w.put((byte) Type.NULL.ordinal());
        } else {
            int i = w.wIndex();
            super.toBinary(w);
            w.put(i, (byte) Type.OBJECT.ordinal());
        }
    }
}
