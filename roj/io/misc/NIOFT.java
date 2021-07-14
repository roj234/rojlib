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
package roj.io.misc;

import roj.io.NonblockingUtil;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/12/19 19:25
 */
public class NIOFT {
    public static void main(String[] args) throws IOException {
        if (args.length != 2)
            System.out.println("NIOFileTransport <source> <dest> ");

        File src = new File(args[0]);
        if (!src.isFile()) {
            System.err.println("Source not exist");
            System.exit(-1);
        }
        FileChannel from = FileChannel.open(src.toPath(), StandardOpenOption.READ);

        src = new File(args[1]);
        if (!src.isFile() && !src.createNewFile()) {
            System.err.println("Dst not exist and unable create");
            System.exit(-1);
        }
        FileChannel to = FileChannel.open(src.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.READ);

        FileDescriptor fd = NonblockingUtil.fd(to);
        System.out.println("Target " + to + " @fd=" + fd);

        System.out.println("sendfile()");
        long remain = from.size(), pos = 0, got;
        long t = System.currentTimeMillis();
        do {
            got = NonblockingUtil.transferInto_sendfile(from, pos, remain, fd);
            if (got < 0)
                break;
            pos += got;
            remain -= got;
            System.out.println("Transferred " + got);
        } while (remain > 0);

        if (got == -6) {
            System.out.println("You're not unix...");
        }
        System.out.println(got);

        do {
            got = NonblockingUtil.transferInto_mmap(from, pos, remain, fd, 4);
            if (got < 0)
                break;
            pos += got;
            remain -= got;
            System.out.println("Transferred " + got);
        } while (remain > 0);

        System.out.println("Done " + (System.currentTimeMillis() - t));
    }
}
