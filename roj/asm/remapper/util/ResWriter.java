package roj.asm.remapper.util;

import roj.io.IOUtil;
import roj.util.ByteList;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: ResWriter.java
 */
public class ResWriter implements Runnable {
    public ResWriter(ZipOutputStream zos, Map<String, ?> resources) {
        this.zos = zos;
        this.resources = resources;
    }

    private final ZipOutputStream zos;
    private final Map<String, ?> resources;

    /**
     * Write resource into zip
     */
    @Override
    public void run() {
        try {
            for (Map.Entry<String, ?> entry : resources.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey().replace('\\', '/'));
                zos.putNextEntry(ze);
                Object obj = entry.getValue();
                if(obj instanceof InputStream) {
                    try (InputStream is = (InputStream) entry.getValue()) {
                        zos.write(IOUtil.readFully(is));
                    }
                } else if(obj instanceof byte[]) {
                    zos.write((byte[]) obj);
                } else if(obj instanceof ByteList) {
                    ((ByteList) obj).writeToStream(zos);
                } else {
                    throw new ClassCastException(obj.getClass().getName());
                }
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
