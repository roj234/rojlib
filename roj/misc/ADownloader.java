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
import roj.util.ByteList;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Adnmb HTML Parser
 *
 * @author Roj233
 * @since 2021/9/12 23:16
 */
public class ADownloader {
    public static final MyHashSet<String> HTML_CLOSE_TAGS;
    static {
        HTML_CLOSE_TAGS = new MyHashSet<>("meta", "link", "input", "img");
    }

    public static void main(String[] args) throws IOException, ParseException {
        if (args.length < 2) {
            System.out.println("ADownloader <dst> <src> [cookie-id]...");
            System.out.println("  用途：处理A岛指定cookie的回复为txt格式 src为保存的html");
            return;
        }

        MyHashSet<String> targetCookie = new MyHashSet<>();
        for (int i = 2; i < args.length; i++) {
            targetCookie.add(args[i]);
        }
        FileOutputStream fos = new FileOutputStream(args[0], true);
        XMLexer init = new XMLexer();
        init.init(IOUtil.readUTF(new FileInputStream(args[1])));
        init.hasCloseTags(s -> !HTML_CLOSE_TAGS.contains(s.toLowerCase()));
        XElement elm = XMLParser.xmlElement(init, XMLParser.KEEP_ENTITY | XMLParser.LENIENT);

        ArrayList<XElement> threads = new ArrayList<>();
        elm.iterate(xml -> {
            if(xml.isString()) return;
            XElement e = xml.asElement();
            if(e.getAttribute("class").asString().equals("h-threads-info")) {
                String cookie = e.children(3).asElement().children(0).asString();
                if(targetCookie.isEmpty() || targetCookie.contains(cookie))
                    threads.add(e);
            }
        });
        CharList tmp = new CharList();
        for (XElement e : threads) {
            String title = e.children(0).asElement().children(0).asString();
            if(e.children(1).childElementCount() == 0)  {
                System.out.println("跳广告: " + e);
                continue;
            }
            String author = e.children(1).asElement().children(0).asString();
            String time = e.children(2).asElement().children(0).asString();
            String cookie = e.children(3).asElement().children(0).asString();
            tmp.clear();
            tmp.append(title).append(" -- ").append(author).append(" (").append(cookie).append(")\r\n")
               .append("  --- ").append(time).append("\r\n");
            int l = tmp.length();
            XElement content = e.getXS("span.div(1)").get(0).asElement();
            for (AbstXML x : content) {
                if(x.isString())
                    tmp.append(x.asString());
            }
            if(tmp.length() - l < 64) {
                System.out.println("Skip " + tmp);
                continue;
            }
            ByteList.encodeUTF(tmp.trim().append("\r\n\r\n\r\n")).writeToStream(fos);
        }

        fos.close();
    }
}
