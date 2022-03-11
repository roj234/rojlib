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

import roj.collect.MyHashMap;
import roj.text.SimpleLineReader;
import roj.text.TextUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class FileResponse extends StreamResponse {
    static MyHashMap<String, Mime> mimeTypes = new MyHashMap<>();
    static final class Mime {
        String type;
        boolean zip;

        public Mime(String type) {
            this.type = type;
        }
    }
    public static synchronized void loadMimeMap(CharSequence text) {
        ArrayList<String> arr = new ArrayList<>();
        for (String line : new SimpleLineReader(text)) {
            arr.clear();
            TextUtil.split(arr, line, ' ');
            Mime mime = new Mime(arr.get(1).toLowerCase());
            mime.zip = arr.size() > 2 && arr.get(2).equalsIgnoreCase("gz");
            mimeTypes.put(arr.get(0).toLowerCase(), mime);
        }
        mimeTypes.putIfAbsent("*", new Mime("text/plain"));
    }

    protected final File file;

    public FileResponse(File absolute) {
        file = absolute;
    }

    @Override
    public void writeHeader(ByteList list) {
        String ext = file.getName();
        ext = ext.substring(ext.lastIndexOf('.') + 1).toLowerCase();
        String type = mimeTypes.getOrDefault(ext, mimeTypes.get("*")).type;
        switch (ext) {
            case "html":
            case "css":
            case "js":
                type += "; charset=UTF-8";
                break;
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
        String ext = file.getName();
        ext = ext.substring(ext.lastIndexOf('.') + 1).toLowerCase();
        return file.length() > 100 && mimeTypes.getOrDefault(ext, mimeTypes.get("*")).zip;
    }
}
