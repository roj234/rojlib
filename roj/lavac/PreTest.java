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
package roj.lavac;

import roj.config.ParseException;
import roj.lavac.parser.ClassContext;
import roj.lavac.parser.IAccessor;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Lavac Preworking TEST!
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/11/6 23:09
 */
public class PreTest {
    public static void main(String[] args) throws IOException, ParseException {
        ClassContext ctx = new ClassContext(args[0], new FileInputStream(args[0]), new ACC());
        ctx.stage0Read();
        ctx.stage1Struct();
        System.out.println("STRUCT: " + ctx.getDest());
        ctx.stage2Compile();
        System.out.println("BYTECODE: " + ctx.getDest());
        ctx.stage3Optimize();
        System.out.println("OPTIMIZED: " + ctx.getDest());
    }

    static class ACC extends IAccessor {

    }
}
