/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roj234
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

import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.crypt.Base64;
import roj.io.IOUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Har file exporter
 * @author solo6975
 * @since 2022/1/1 19:20
 */
public class HarExporter {
    public static void main(String[] args) throws IOException, ParseException {
        if (args.length < 2) {
            System.out.println("HarExporter file store");
            return;
        }
        File base = new File(args[1]);
        CMapping har = JSONParser.parse(IOUtil.readUTF(new File(args[0]))).asMap().getOrCreateMap("log");
        har.dot(true);
        System.out.println("Version " + har.getString("version") + " Created by " + har.getString("creator.name") + " " + har.getString("creator.version"));
        CList entries = har.getOrCreateList("entries");
        ByteList tmp = new ByteList();
        for (int i = 0; i < entries.size(); i++) {
            CMapping entry = entries.get(i).asMap();
            entry.dot(true);
            String url = entry.getString("request.url");
            try {
                URL url1 = new URL(url);
                url = url1.getHost() + url1.getPath();
                if (url.endsWith("/"))
                    url += "index.html";
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            String content = entry.getString("response.content.text");
            String encoding = entry.getString("response.content.encoding");

            tmp.clear();
            if (encoding.equalsIgnoreCase("base64")) {
                Base64.decode(content, tmp);
            } else if (encoding.isEmpty()) {
                ByteList.writeUTF(tmp, content, 2);
            } else {
                System.err.println("未知的编码方式for " + url + ": " + encoding);
            }
            File file = new File(base, url);
            file.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                tmp.writeToStream(fos);
                System.out.println("OK " + url);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
