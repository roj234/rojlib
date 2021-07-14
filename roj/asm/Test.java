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

package roj.asm;

import roj.asm.struct.Clazz;
import roj.io.IOUtil;

import java.io.*;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/30 19:27
 */
public class Test implements Runnable {
    public Test(byte[] b) {
        this.b = b;
    }

    private byte[] b;

    public static void main(String[] args) {
        try (InputStream s = new FileInputStream(new File(args[0]))) {
            byte[] b = IOUtil.readFully(s);

            for (int i = 0; i < 10; i++) {
                Clazz a = Parser.parse(b, Parser.SKIP_DEBUG);
                if (i == 0)
                    a.methods.get(args.length > 2 ? Integer.parseInt(args[2]) : 0).code.computeFrames = true;
                //    System.out.println(a);
                b = Parser.toByteArray(a);

                System.out.println();
                System.out.println();
                System.out.println();
            }

            if (args.length > 1)
                try (OutputStream s2 = new FileOutputStream(new File(args[1]))) {
                    s2.write(b);
                } catch (IOException ignored) {
                }

            System.err.println("OK");
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void run() {
        long t = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            Clazz a = Parser.parse(b, Parser.SKIP_DEBUG);
            b = Parser.toByteArray(a);
        }
        System.err.println("[子进程]1000次读写耗时: " + (System.currentTimeMillis() - t));
    }
}