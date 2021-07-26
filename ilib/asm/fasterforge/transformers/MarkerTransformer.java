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
package ilib.asm.fasterforge.transformers;

import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.commons.io.IOUtils;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.io.IOUtil;
import roj.text.SimpleLineReader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MarkerTransformer implements IClassTransformer {
    private final ListMultimap<String, String> markers;

    public MarkerTransformer() throws IOException {
        this("fml_marker.cfg");
    }

    protected MarkerTransformer(String rulesFile) throws IOException {
        this.markers = ArrayListMultimap.create();
        readMapFile(rulesFile);
    }

    private void readMapFile(String rulesFile) throws IOException {
        byte[] rulesResource;
        File file = new File(rulesFile);
        if (!file.exists()) {
            rulesResource = IOUtil.getBytes(MarkerTransformer.class, rulesFile);
        } else {
            try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                rulesResource = new byte[is.available()];
                int count = is.read(rulesResource);
            }
        }
        new SimpleLineReader(new String(rulesResource, StandardCharsets.UTF_8)).forEach((String input) -> {
            String line = Iterables.getFirst(Splitter.on('#').limit(2).split(input), "").trim();
            if (line.length() == 0)
                return;
            List<String> parts = Lists.newArrayList(Splitter.on(" ").trimResults().split(line));
            if (parts.size() != 2)
                throw new RuntimeException("Invalid config file line " + input);
            List<String> markerInterfaces = Lists.newArrayList(Splitter.on(",").trimResults().split(parts.get(1)));
            for (String marker : markerInterfaces)
                MarkerTransformer.this.markers.put(parts.get(0), marker);
        });
    }

    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null)
            return null;
        if (!this.markers.containsKey(name))
            return bytes;
        ConstantData data = Parser.parseConstants(bytes);
        for (String marker : this.markers.get(name)) {
            data.interfaces.add(data.writer.getClazz(marker));
        }
        return Parser.toByteArray(data, true);
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: MarkerTransformer <JarPath> <MapFile> [MapFile2]... ");
            return;
        }
        boolean hasTransformer = false;
        MarkerTransformer[] trans = new MarkerTransformer[args.length - 1];
        for (int x = 1; x < args.length; x++) {
            try {
                trans[x - 1] = new MarkerTransformer(args[x]);
                hasTransformer = true;
            } catch (IOException e) {
                System.out.println("Could not read Transformer Map: " + args[x]);
                e.printStackTrace();
            }
        }
        if (!hasTransformer) {
            System.out.println("Could not find a valid transformer to perform");
            return;
        }
        File orig = new File(args[0]);
        File temp = new File(args[0] + ".ATBack");
        if (!orig.exists() && !temp.exists()) {
            System.out.println("Could not find target jar: " + orig);
            return;
        }
        if (!orig.renameTo(temp)) {
            System.out.println("Could not rename file: " + orig + " -> " + temp);
            return;
        }
        try {
            processJar(temp, orig, trans);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!temp.delete())
            System.out.println("Could not delete temp file: " + temp);
    }

    private static void processJar(File inFile, File outFile, MarkerTransformer[] transformers) throws IOException {
        ZipInputStream inJar = null;
        ZipOutputStream outJar = null;
        try {
            try {
                inJar = new ZipInputStream(new BufferedInputStream(new FileInputStream(inFile)));
            } catch (FileNotFoundException e) {
                throw new FileNotFoundException("Could not open input file: " + e.getMessage());
            }
            try {
                outJar = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)));
            } catch (FileNotFoundException e) {
                throw new FileNotFoundException("Could not open output file: " + e.getMessage());
            }
            ZipEntry entry;
            while ((entry = inJar.getNextEntry()) != null) {
                int len;
                if (entry.isDirectory()) {
                    outJar.putNextEntry(entry);
                    continue;
                }
                byte[] data = new byte[4096];
                ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();
                do {
                    len = inJar.read(data);
                    if (len <= 0)
                        continue;
                    entryBuffer.write(data, 0, len);
                } while (len != -1);
                byte[] entryData = entryBuffer.toByteArray();
                String entryName = entry.getName();
                if (entryName.endsWith(".class") && !entryName.startsWith(".")) {
                    ConstantData z = Parser.parseConstants(entryData);
                    String name = z.name.replace('/', '.').replace('\\', '.');
                    for (MarkerTransformer trans : transformers)
                        entryData = trans.transform(name, name, entryData);
                }
                ZipEntry newEntry = new ZipEntry(entryName);
                outJar.putNextEntry(newEntry);
                outJar.write(entryData);
            }
        } finally {
            IOUtils.closeQuietly(outJar);
            IOUtils.closeQuietly(inJar);
        }
    }
}
