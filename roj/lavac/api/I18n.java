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
package roj.lavac.api;

import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Localization Util
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/6/17 23:43
 */
public class I18n {
    public static final String at;

    static final Map<String, String> translateMap;

    public static String translate(String s) {
        String v = translateMap.get(s);
        if(v != null)
            return v;
        // a:b:c
        CharList cl = new CharList();
        List<String> tmp = TextUtil.split(new ArrayList<>(), cl, s, ':');
        if(tmp.size() <= 1)
            return s;
        cl.clear();

        v = translateMap.get(tmp.get(0));
        if(v == null)
            return s;

        cl.append(v);
        CharList cl2 = new CharList(4);
        for (int i = 1; i < tmp.size(); i++) {
            cl.replace(cl2.append('%').append(Integer.toString(i)), translate(tmp.get(i)));
            cl2.clear();
        }

        return cl.toString();
    }

    static {
        String path = System.getProperty("lavac.translate");
        Map<String, String> m;
        try {
            m = TextUtil.loadLang(path == null ? IOUtil.readUTF("META-INF/lavac.lang") : IOUtil.readUTF(new FileInputStream(path)));
        } catch (IOException e) {
            e.printStackTrace();
            m = Collections.emptyMap();
        }
        translateMap = m;
        at = translateMap.getOrDefault("at", " 在 ");
    }
}
