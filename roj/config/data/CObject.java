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
import roj.config.serial.Structs;
import roj.util.ByteList;

import java.util.Map;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class CObject<T> extends CMapping {
    public T value;
    public Serializers ser;

    public CObject(T object) {
        this.value = object;
        ser = Serializers.DEFAULT;
    }

    public CObject(T object, Serializers ser) {
        this.value = object;
        this.ser = ser;
    }

    @SuppressWarnings("unchecked")
    public CObject(Map<String, CEntry> map, Serializers ser, Serializer<?> deser) {
        super(map);
        this.ser = ser;
        // todo add ser putfield
        this.value = (T) deser.deserialize(this);
    }

    @Override
    public Type getType() {
        return Type.OBJECT;
    }

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
            Serializer<Object> s = ser.find(value.getClass().getName());
            if (s == null) throw new IllegalArgumentException("Serializers " + ser + " unable to find a serializer for " + value);
            s.serialize(this, value);
        }
    }

    @SuppressWarnings("unchecked")
    public void deserialize() {
        value = (T) ser.find(getString("==")).deserialize(this);
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
    public void toBinary(ByteList w, Structs struct) {
        if (this.value == null) {
            w.put((byte) Type.NULL.ordinal());
        } else {
            int i = w.wIndex();
            serialize();
            super.toBinary(w, struct);
            if (w.get(i) == Type.MAP.ordinal())
                w.put(i, (byte) Type.OBJECT.ordinal());
        }
    }
}
