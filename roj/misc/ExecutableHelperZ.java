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
import roj.io.MutableZipFile;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.zip.Deflater;
import java.util.zip.ZipException;

/**
 * xyz.exe which can be opened with a zip tool, however, read only
 *
 * @author solo6975
 * @since 2021/10/6 19:31
 */
public class ExecutableHelperZ {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("ExecutableHelperZ <exe-file> <zip-file> [begin-offset]");
            System.out.println("  用途：简易修改各种自解压zip文件");
            return;
        }

        File file = new File(args[0]);
        byte[] data = IOUtil.read(file);
        int offset = args.length < 3 ? 0 : Integer.parseInt(args[2]);
        try {
            while (true) {
                do {
                    while (data[offset++] != 'P') ;
                } while (data[offset++] != 'K');

                offset -= 2;
                try(MutableZipFile mzf = new MutableZipFile(file, Deflater.BEST_SPEED, MutableZipFile.FLAG_VERIFY, offset)) {
                    mzf.getEntries();
                    break;
                } catch (Throwable ignored) {}
                offset += 2;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new ZipException(file + " is not a valid executable zip file");
        }

        FileChannel cf = FileChannel.open(new File(args[1]).toPath(), StandardOpenOption.READ);
        FileChannel ct = FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.READ).position(offset);
        cf.transferTo(0, cf.size(), ct);
        cf.close();
        if (ct.size() != ct.position()) {
            ct.truncate(ct.position());
        }
        ct.close();
    }
}
