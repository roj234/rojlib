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
package ilib.client.resource;

import ilib.Config;
import ilib.ImpLib;
import ilib.client.TextureHelper;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.ResourcePackFileNotFoundException;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.io.IOUtil;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class GeneratedModelRepo extends AbstractResourcePack {
    static final Map<String, byte[]> data = new MyHashMap<>();
    static final Set<String> domains = new MyHashSet<>();

    public static void preInitDone() {
        GeneratedModelRepo instance = new GeneratedModelRepo();
    }

    public static void register(String path, String jsonData) {
        register(path, jsonData.getBytes(StandardCharsets.UTF_8));
        if ((Config.debug & 8) != 0)
            System.out.println("Path " + path + "   is   " + jsonData);
    }

    public static void register(String path, byte[] jsonData) {
        if (path == null || !path.startsWith("assets/")) {
            throw new IllegalArgumentException("Illegal resource path " + path);
        }

        String domain = path.substring(7);
        domain = domain.substring(0, domain.indexOf('/'));

        domains.add(domain);

        data.put(path, jsonData);
    }

    private GeneratedModelRepo() {
        super(new File("generated_model_repository"));
        TextureHelper.enqueuePackLoad(this);
    }

    public static String registerFileableTexture(String texture, File basePath) {
        if (texture.indexOf(':') != -1)
            return texture;
        File real = new File(basePath, texture);
        if (!real.isFile()) {
            ImpLib.logger().warn("File not found: " + real.getAbsolutePath());
            return "missingno";
        }
        final String domain = "fake";
        domains.add(domain);
        try {
            data.put("assets/" + domain + "/textures/" + texture, IOUtil.read(new FileInputStream(real)));
        } catch (IOException e) {
            ImpLib.logger().warn("File couldn't be read: ", e);
            return "missingno";
        }

        return domain + ":" + texture;
    }

    @Override
    protected InputStream getInputStreamByName(@Nonnull String name) throws ResourcePackFileNotFoundException {
        final byte[] buf = data.get(name);
        if(buf != null)
            return new ByteArrayInputStream(buf);
        throw new ResourcePackFileNotFoundException(new File("generated_model_repo"), name);
    }

    @Override
    protected boolean hasResourceName(@Nonnull String key) {
        return data.containsKey(key);
    }

    @Nonnull
    @Override
    public Set<String> getResourceDomains() {
        return Collections.unmodifiableSet(domains);
    }
}
