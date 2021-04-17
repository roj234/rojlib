package roj.io.misc;

import roj.io.NonblockingUtil;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/19 19:25
 */
public class NIOFT {
    public static void main(String[] args) throws IOException {
        if (args.length != 2)
            System.out.println("NIOFileTransport <source> <dest> ");

        File src = new File(args[0]);
        if (!src.isFile()) {
            System.err.println("Source not exist");
            System.exit(-1);
        }
        FileChannel from = FileChannel.open(src.toPath(), StandardOpenOption.READ);

        src = new File(args[1]);
        if (!src.isFile() && !src.createNewFile()) {
            System.err.println("Dst not exist and unable create");
            System.exit(-1);
        }
        FileChannel to = FileChannel.open(src.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.READ);

        FileDescriptor fd = NonblockingUtil.fd(to);
        System.out.println("Target " + to + " @fd=" + fd);

        System.out.println("sendfile()");
        long remain = from.size(), pos = 0, got;
        long t = System.currentTimeMillis();
        do {
            got = NonblockingUtil.transferInto_sendfile(from, pos, remain, fd);
            if (got < 0)
                break;
            pos += got;
            remain -= got;
            System.out.println("Transferred " + got);
        } while (remain > 0);

        if (got == -6) {
            System.out.println("You're not unix...");
        }
        System.out.println(got);

        do {
            got = NonblockingUtil.transferInto_mmap(from, pos, remain, fd, 4);
            if (got < 0)
                break;
            pos += got;
            remain -= got;
            System.out.println("Transferred " + got);
        } while (remain > 0);

        System.out.println("Done " + (System.currentTimeMillis() - t));
    }
}
