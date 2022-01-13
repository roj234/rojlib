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
package roj.kscript.parser;

import roj.config.ParseException;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;

import javax.annotation.Nullable;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/17 16:34
 */
public interface ParseContext {
    JSLexer getLexer();

    /**
     * 解析嵌套函数
     * @param type
     */
    default KFunction parseInnerFunc(short type) throws ParseException {
        return null;
    }

    /**
     * 使用变量
     */
    default void useVariable(String name) {}

    /**
     * 注册变量
     */
    default void assignVariable(String name) {}

    @Nullable
    default KType maybeConstant(String name) {
        return null;
    }
}
