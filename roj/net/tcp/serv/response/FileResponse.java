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
package roj.net.tcp.serv.response;

import roj.text.CharList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class FileResponse extends StreamResponse {
    private static final File ROOT = new File("root");

    protected final File file;

    public FileResponse(File absolute) {
        file = absolute;
    }

    public FileResponse(URI relative) {
        file = new File(ROOT,
                relative.getPath()
                        .replace('/',
                                File.separatorChar));
    }

    @Override
    public void writeHeader(CharList list) {
        String type;
        String name = file.getName();
        if (name.endsWith(".html"))
            type = "text/html; charset=UTF-8";
        else
            type = "application/octet-stream";

        list.append("Content-Type: ").append(type).append(CRLF)
                .append("Content-Length: ").append(Long.toString(length)).append(CRLF);
    }

    @Override
    protected InputStream getStream() throws IOException {
        FileInputStream stream = new FileInputStream(file);
        length = stream.available();
        return stream;
    }
}
