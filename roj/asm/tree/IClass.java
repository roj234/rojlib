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
package roj.asm.tree;

import roj.util.ByteList;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Abstract Class
 *
 * @author solo6975
 * @since 2021/7/22 18:20
 */
public interface IClass extends Attributed {
    String name();
    @Nullable
    String parent();

    List<String> interfaces();
    List<? extends MoFNode> methods();
    List<? extends MoFNode> fields();
    default int getMethodByName(String key) {
        List<? extends MoFNode> methods = methods();
        for (int i = 0; i < methods.size(); i++) {
            MoFNode ms = methods.get(i);
            if (ms.name().equals(key)) return i;
        }
        return -1;
    }
    default int getFieldByName(String key) {
        List<? extends MoFNode> fields = fields();
        for (int i = 0; i < fields.size(); i++) {
            MoFNode fs = fields.get(i);
            if (fs.name().equals(key)) return i;
        }
        return -1;
    }

    default ByteList getBytes(ByteList buf) {
        throw new UnsupportedOperationException(getClass().getName() + " does not support encoding");
    }
}
