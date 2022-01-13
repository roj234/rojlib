/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roj234
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
package roj.config;

import roj.config.data.CEntry;
import roj.config.data.CNull;
import roj.config.serial.Serializer;
import roj.config.serial.Serializers;

/**
 * @author Roj233
 * @version 0.1
 * @since 2022/1/6 13:46
 */
public abstract class Parser {
    protected Serializers ser;

    public CEntry Parse(CharSequence string) throws ParseException {
        return Parse(string, 0);
    }
    public abstract CEntry Parse(CharSequence string, int flag) throws ParseException;

    public CEntry serialize(Object o) {
        if (o == null || ser == null) return CNull.NULL;
        return ser.find(o.getClass().getName()).serializeRc(o);
    }

    @SuppressWarnings("unchecked")
    public <T> T deserialize(Class<T> clazz, CEntry entry) {
        if (entry == CNull.NULL || ser == null) return null;
        return (T) ser.find(clazz.getName()).deserializeRc(entry);
    }

    public Serializers getSerializers() {
        return ser;
    }

    public abstract String format();

    public static final class Builder<T extends Parser> {
        T      parser;
        Serializers ser;

        public Builder(T parser) {
            this.parser = parser;
            this.ser = parser.ser;
        }

        public Builder<T> defaultFlag(int flag) {
            ser.defaultFlag = flag;
            return this;
        }

        public Builder<T> register(Class<?> cls, int flag) {
            ser.register(cls, flag);
            return this;
        }

        public Builder<T> register(Class<?> cls, Serializer<?> ser) {
            this.ser.register(cls, ser);
            return this;
        }

        public T build() {
            return parser;
        }
    }
}
