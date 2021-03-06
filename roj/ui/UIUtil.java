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
package roj.ui;

import roj.text.TextUtil;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2021/5/29 18:40
 */
public final class UIUtil {
    public static final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static int selectOneFile(List<File> files, String name1) throws IOException {
        if (files.size() == 1)
            return 0;
        CmdUtil.info("有多个 " + name1 + " , 请选择(输入编号)");

        for (int i = 0, forgeVersionsSize = files.size(); i < forgeVersionsSize; i++) {
            String name = files.get(i).getName();
            int k = name.lastIndexOf(File.separatorChar);
            CmdUtil.fg(CmdUtil.Color.WHITE, (i & 1) == 1);
            System.out.println(i + ". " + (k == -1 ? name : name.substring(0, k)));
            CmdUtil.reset();
        }

        return getNumberInRange(0, files.size());
    }

    /**
     * @param min 最小值(包括)
     * @param max 最大值(不包括)
     */
    public static int getNumberInRange(int min, int max) throws IOException {
        String s;
        int i;
        do {
            do {
                CmdUtil.info("您的选择: ", false);
                CmdUtil.fg(CmdUtil.Color.YELLOW, true);
                s = br.readLine();
                CmdUtil.reset();
                if (s == null)
                    System.exit(-2);
                if (TextUtil.isNumber(s) == 0)
                    break;
                CmdUtil.warning("输入的不是数字!", true);
            } while (true);
            try {
                i = Integer.parseInt(s);
                if (i >= min && i < max) {
                    break;
                }
            } catch (NumberFormatException ignored) {
            }
            CmdUtil.warning("输入的数太大或者太小!", true);
        } while (true);
        return i;
    }

    public static File readFile(String name) throws IOException {
        CmdUtil.info("请键入, 粘贴或者拖动" + name + "到这里并按下回车!");
        File file;
        do {
            CmdUtil.fg(CmdUtil.Color.YELLOW, true);
            String s = br.readLine();
            CmdUtil.reset();
            if (s == null)
                System.exit(-2);
            if (s.startsWith("\"") && s.endsWith("\""))
                s = s.substring(1, s.length() - 1);
            file = new File(s);
            if (file.exists())
                return file;
            CmdUtil.warning("文件不存在，请重试!", true);
        } while (!file.exists());
        return file;
    }

    public static boolean readBoolean(String info) throws IOException {
        boolean enableAt;
        do {
            CmdUtil.info(info, false);
            CmdUtil.fg(CmdUtil.Color.YELLOW, true);
            String s = br.readLine();
            if (s == null)
                System.exit(-2);
            CmdUtil.reset();
            Boolean b = null;
            switch (s.toLowerCase()) {
                case "y":
                case "yes":
                case "ok":
                case "t":
                case "true":
                case "是":
                    b = true;
                    break;
                case "n":
                case "no":
                case "not":
                case "false":
                case "cancel":
                case "f":
                case "否":
                    b = false;
                    break;
            }
            if (b != null) {
                enableAt = b;
                break;
            }
            CmdUtil.warning("不是true或false");
        } while (true);
        return enableAt;
    }

    public static String userInput(String info, Function<String, Boolean> verifier) throws IOException {
        CmdUtil.info(info, false);
        String string;
        do {
            CmdUtil.fg(CmdUtil.Color.YELLOW, true);
            string = br.readLine();
            if (string == null)
                System.exit(-2);
            if (verifier.apply(string) == Boolean.TRUE) {
                break;
            }
            System.out.println("输入不正确!");
            CmdUtil.reset();
        } while (true);
        return string.trim();
    }

    public static String userInput(String info) throws IOException {
        CmdUtil.info(info, false);
        CmdUtil.fg(CmdUtil.Color.YELLOW, true);
        String string = br.readLine();
        CmdUtil.reset();
        if (string == null)
            System.exit(-2);
        return string.trim();
    }

    public static void setLogo(JFrame frame, String fileName) {
        InputStream stream = UIUtil.class.getClassLoader().getResourceAsStream(fileName);
        if (stream != null) {
            try {
                frame.setIconImage(ImageIO.read(stream));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void systemLook() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }
    }

    public static void center(Window frame) {
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle bounds = frame.getBounds();
        frame.setBounds((dim.width - bounds.width) / 2, (dim.height - bounds.height) / 2, bounds.width, bounds.height);
    }

    public static char[] readPassword() {
        return System.console().readPassword("");
    }
}
