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

import roj.collect.MyHashSet;
import roj.config.ParseException;
import roj.config.XMLParser;
import roj.config.XMLParser.XMLexer;
import roj.config.data.AbstXML;
import roj.config.data.XElement;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.ByteWriter;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/12 23:16
 */
public class ADownloader {
    public static void main(String[] args) throws IOException, ParseException {
        System.out.println("ADownloader -- ADnmb HTML Parser & Fetcher");
        MyHashSet<String> targetCookie = new MyHashSet<>();
        for (int i = 2; i < args.length; i++) {
            targetCookie.add(args[i]);
        }
        FileOutputStream fos = new FileOutputStream(args[0], true);
        XMLexer init = new XMLexer();
        init.init(IOUtil.readUTF(new FileInputStream(args[1])));
        init.noCloseTags(new AbstractSet<String>() {
            @Override
            public boolean contains(Object o) {
                return XMLParser.HTML_CLOSE_TAGS.contains(o.toString().toLowerCase());
            }

            @Override
            public Iterator<String> iterator() {
                return XMLParser.HTML_CLOSE_TAGS.iterator();
            }

            @Override
            public int size() {
                return XMLParser.HTML_CLOSE_TAGS.size();
            }
        }).keepAmp(true).lenient(true);
        XElement elm = XMLParser.xmlElement(init);

        ArrayList<XElement> threads = new ArrayList<>();
        elm.iterate(xml -> {
            if(xml.isString()) return;
            XElement e = xml.asElement();
            if(e.getAttribute("class").asString().equals("h-threads-info")) {
                String cookie = e.children(3).asElement().children(0).asText();
                if(targetCookie.isEmpty() || targetCookie.contains(cookie))
                    threads.add(e);
            }
        });
        CharList tmp = new CharList();
        for (XElement e : threads) {
            String title = e.children(0).asElement().children(0).asText();
            if(e.children(1).asElement().childElementCount() == 0)  {
                System.out.println("跳广告: " + e);
                continue;
            }
            String author = e.children(1).asElement().children(0).asText();
            String time = e.children(2).asElement().children(0).asText();
            String cookie = e.children(3).asElement().children(0).asText();
            tmp.clear();
            tmp.append(title).append(" -- ").append(author).append(" (").append(cookie).append(")\r\n")
               .append("  --- ").append(time).append("\r\n");
            int l = tmp.length();
            XElement content = e.getXS("span.div(1)").get(0).asElement();
            for (AbstXML x : content) {
                if(x.isString())
                    tmp.append(x.asText());
            }
            if(tmp.length() - l < 64) {
                System.out.println("Skip " + tmp);
                continue;
            }
            ByteWriter.encodeUTF(tmp.trim().append("\r\n\r\n\r\n")).writeToStream(fos);
        }

        fos.close();
    }
}
