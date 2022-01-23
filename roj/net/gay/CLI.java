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
package roj.net.gay;

import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * 你猜猜这名字是怎么回事？
 * Gayhub
 * @author Roj233
 * @since 2022/1/4 0:20
 */
public class CLI {
    public static void main(String[] args) throws IOException {
        File cd = new File(args.length > 0 ? args[0] : ".");
        Repository repo = Repository.init(cd);
        repo.load();

        System.out.println("线性版本管理系统 Gay 1.0 欢淫使用!");
        System.out.println("指令: add remove(amount) set(i) get genPatch(fr,to,dst) save");

        Tokenizer tokenizer = new Tokenizer();

        ArrayList<String> tmp = new ArrayList<>(48);
        o:
        while (true) {
            String input = UIUtil.userInput("> ");
            tokenizer.init(input);
            try {
                while (tokenizer.hasNext()) {
                    Word w = tokenizer.readWord();
                    if(w.type() == WordPresets.EOF) {
                        CmdUtil.error("指令有误 " + tmp);
                        continue o;
                    }
                    tmp.add(w.val());
                    tokenizer.recycle(w);
                }
                switch (tmp.get(0).toLowerCase()) {
                    case "add":
                        repo.add();
                        break;
                    case "remove":
                        repo.remove(Integer.parseInt(tmp.get(1)));
                        break;
                    case "set":
                        repo.set(Integer.parseInt(tmp.get(1)));
                        break;
                    case "get":
                        System.out.println("当前版本: " + repo.get());
                        break;
                    case "save":
                        repo.save();
                        break;
                    case "diffcur":
                        ByteList w = new ByteList();
                        Ver last = repo.get(repo.get());
                        Ver cur = last.copy();
                        cur.deltaToCur(true, true);
                        Ver.genPatch(last, cur, w);
                        try (FileOutputStream fos = new FileOutputStream(tmp.get(3))) {
                            w.writeToStream(fos);
                        }
                        System.out.println("Patch写入 " + tmp.get(3));
                        break;
                    case "genpatch":
                        w = new ByteList();
                        Ver.genPatch(repo.get(Integer.parseInt(tmp.get(1))),
                                     repo.get(Integer.parseInt(tmp.get(2))), w);
                        try (FileOutputStream fos = new FileOutputStream(tmp.get(3))) {
                            w.writeToStream(fos);
                        }
                        System.out.println("Patch写入 " + tmp.get(3));
                        break;
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            tmp.clear();
        }
    }
}
