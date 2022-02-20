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
package roj.net.http.serv;

import roj.util.ByteList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileResponse extends StreamResponse {
    protected final File file;

    public FileResponse(File absolute) {
        file = absolute;
    }

    @Override
    public void writeHeader(ByteList list) {
        String mime = file.getName();
        mime = mime.substring(mime.lastIndexOf('.') + 1).toLowerCase();
        String type;
        switch (mime) {
            case "html":
                type = "text/html; charset=UTF-8";
                break;
            case "css":
                type = "text/css; charset=UTF-8";
                break;
            case "js":
                type = "text/javascript; charset=UTF-8";
                break;
            default:
                type = "application/octet-stream";
        }

        //list.putAscii("Content-Disposition: attachment; filename=\"" + file.getName() + '"').putAscii(CRLF)
        list.putAscii("Content-Type: ").putAscii(type).putAscii(CRLF)
            .putAscii("Content-Length: ").putAscii(Long.toString(file.length())).putAscii(CRLF);
    }

    @Override
    protected InputStream getStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public boolean wantCompress() {
        return file.length() > 100;
    }
}
