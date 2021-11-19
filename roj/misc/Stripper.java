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
package roj.misc;

import roj.io.IOUtil;
import roj.util.ByteWriter;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/12/3 13:19
 */
public class Stripper {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Stripper <file> <line-start> <line-end> <encoding>");
            System.out.println("  用途： 截取小说指定行");
            return;
        }
        String ln = IOUtil.readAs(new FileInputStream(args[0]), args.length > 3 ? args[3] : "UTF-8");
        int lineBegin = Integer.parseInt(args[1]);
        int begin = lineOffset(ln, 0, lineBegin - 1);
        int end = args[2].equals("-1") ? ln.length() : lineOffset(ln, begin, Integer.parseInt(args[2]) - lineBegin);
        ByteWriter.encodeUTF(ln.substring(begin, end)).writeToStream(new FileOutputStream(args[0]));
    }

    @SuppressWarnings("fallthrough")
    public static int lineOffset(CharSequence keys, int i, int line) {
        while (i < keys.length()) {
            switch (keys.charAt(i)) {
                case '\r':
                    if (i + 1 >= keys.length() || keys.charAt(i + 1) != '\n') {
                        break;
                    } else {
                        i++;
                    }
                case '\n':
                    if(--line == 0) {
                        return i;
                    }
                    break;
            }
            i++;
        }

        return i;
    }
}
