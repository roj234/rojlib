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
package roj.sound.source;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/18 13:38
 */
public class RandomAccessFileSource implements Source {
    private final RandomAccessFile file;
    private final long offset;

    public RandomAccessFileSource(RandomAccessFile file) {
        this.file = file;
        this.offset = 0;
    }

    public RandomAccessFileSource(File file) throws FileNotFoundException {
        this(file, 0);
    }

    public RandomAccessFileSource(String path) throws FileNotFoundException {
        this.file = new RandomAccessFile(path, "r");
        this.offset = 0;
    }

    public RandomAccessFileSource(File file, long offset) throws FileNotFoundException {
        this.file = new RandomAccessFile(file, "r");
        this.offset = 0;
    }

    @Override
    public void seek(long source) throws IOException {
        file.seek(source + offset);
    }

    @Override
    public int read(byte[] array, int offset, int length) throws IOException {
        return file.read(array, offset, length);
    }

    @Override
    public long length() throws IOException {
        return file.length() - offset;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}