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

package ilib.client;

import ilib.ClientProxy;
import ilib.Config;
import ilib.ImpLib;
import ilib.util.Hook;
import ilib.util.ReflectionClient;
import roj.collect.SimpleList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;

import net.minecraftforge.client.resource.IResourceType;
import net.minecraftforge.client.resource.ISelectiveResourceReloadListener;
import net.minecraftforge.client.resource.VanillaResourceType;
import net.minecraftforge.fml.client.FMLClientHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

//!!AT [["net.minecraft.client.resources.SimpleReloadableResourceManager", ["field_110548_a", "func_110544_b"]], ["net.minecraft.client.resources.FallbackResourceManager", ["field_110540_a"]]]
/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class TextureHelper implements ISelectiveResourceReloadListener {
    public static SimpleReloadableResourceManager resourceManager;
    private static ArrayList<IResourcePack> packLoad = new ArrayList<>();

    public static void preInit() {
        resourceManager = (SimpleReloadableResourceManager) Minecraft.getMinecraft().getResourceManager();
        resourceManager.registerReloadListener(new TextureHelper());
        for (int i = 0; i < packLoad.size(); i++) {
            load0(packLoad.get(i));
        }
        packLoad = null;
    }

    public static void manualReload() {
        resourceManager.notifyReloadListeners();
    }

    public static void load(IResourcePack pack) {
        if (packLoad != null) {
            packLoad.add(pack);
        } else {
            load0(pack);
        }
    }

    private static void load0(IResourcePack pack) {
        ReflectionClient.HELPER.getResourcePackList(FMLClientHandler.instance()).add(pack);
        resourceManager.reloadResourcePack(pack);
    }

    public static List<IResourcePack> getLoadedResourcePacks() {
        List<IResourcePack> list = new SimpleList<>();
        for (FallbackResourceManager man : resourceManager.domainResourceManagers.values()) {
            list.addAll(man.resourcePacks);
        }
        return list;
    }

    @Override
    public void onResourceManagerReload(IResourceManager man, Predicate<IResourceType> pred) {
        if (pred.test(VanillaResourceType.LANGUAGES)) {
            ImpLib.HOOK.trigger(Hook.LANGUAGE_RELOAD);
            if (Config.fixFont) {
                ClientProxy.mc.fontRenderer.setUnicodeFlag(false);
                ClientProxy.mc.fontRenderer.setBidiFlag(false);
            }
        }
    }
}