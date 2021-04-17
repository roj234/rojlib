/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: Test.java
 */
package roj.asm;

import roj.asm.struct.Clazz;
import roj.io.IOUtil;

import java.io.*;

public class Test implements Runnable {
    public Test(byte[] b) {
        this.b = b;
    }

    private byte[] b;

    public static void main(String[] args) {
        try (InputStream s = new FileInputStream(new File(args[0]))) {
            byte[] b = IOUtil.readFully(s);

            for (int i = 0; i < 10; i++) {
                Clazz a = Parser.parse(b, Parser.SKIP_DEBUG);
                if (i == 0)
                    a.methods.get(args.length > 2 ? Integer.parseInt(args[2]) : 0).code.computeFrames = true;
                //    System.out.println(a);
                b = Parser.toByteArray(a);

                System.out.println();
                System.out.println();
                System.out.println();
            }

            if (args.length > 1)
                try (OutputStream s2 = new FileOutputStream(new File(args[1]))) {
                    s2.write(b);
                } catch (IOException ignored) {
                }

            System.err.println("OK");
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void run() {
        long t = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            Clazz a = Parser.parse(b, Parser.SKIP_DEBUG);
            b = Parser.toByteArray(a);
        }
        System.err.println("[子进程]1000次读写耗时: " + (System.currentTimeMillis() - t));
    }
}