package roj.misc;

import roj.asm.cst.CstRef;
import roj.asm.util.Context;
import roj.io.ZipUtil;
import roj.util.ByteList;

import java.io.File;

/**
 * @author Roj233
 * @since 2021/7/8 12:35
 */
public class BugHelper {
    public static void main(String[] args) throws Exception {
        ByteList bl = new ByteList();

        ZipUtil.ICallback cb = (fileName, s) -> {
            Context ctx = new Context(fileName, s);
            for (CstRef ref : ctx.getMethodConstants()) {
                if (ref.desc().getName().getString().equals("getPotentialSpawns")) {
                    System.err.println(fileName);
                    break;
                }
            }
        };
        ZipUtil.unzip(new File(args[0]), cb, (ze) -> ze.getName().endsWith(".class"));
    }
}
