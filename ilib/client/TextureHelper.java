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

import ilib.ATHandler;
import ilib.ClientProxy;
import ilib.Config;
import ilib.ImpLib;
import ilib.util.Reflection;
import roj.asm.annotation.OpenAny;
import roj.collect.SimpleList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.*;

import net.minecraftforge.fml.client.FMLClientHandler;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

@OpenAny(value = "net.minecraft.client.resources:SimpleReloadableResourceManager", names = {
        "domainResourceManagers", "field_110548_a"
})
@OpenAny(value = "net.minecraft.client.resources:FallbackResourceManager", names = {
        "resourcePacks", "field_110540_a"
})
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class TextureHelper implements IResourceManagerReloadListener {
    public static SimpleReloadableResourceManager resourceManager;
    private static Queue<IResourcePack> packLoad = new LinkedList<>();

    public static void preInit() {
        resourceManager = (SimpleReloadableResourceManager) Minecraft.getMinecraft().getResourceManager();
        while (packLoad.size() > 0) {
            register(packLoad.poll());
        }
        packLoad = null;
    }

    public static void postInit() {
        TextureHelper instance = new TextureHelper();
        resourceManager.registerReloadListener(instance);
    }

    public static void manualReload() {
        ilib.ATHandler.notifyReloadListeners(resourceManager);
    }

    public static void enqueuePackLoad(IResourcePack pack) {
        if (packLoad != null) {
            packLoad.add(pack);
        } else {
            register(pack);
        }
    }

    private static void register(IResourcePack pack) {
        Reflection.HELPER.getResourcePackList(FMLClientHandler.instance()).add(pack);
        resourceManager.reloadResourcePack(pack);
    }

    public static List<IResourcePack> getLoadedResourcePacks() {
        List<IResourcePack> anotherList = new SimpleList<>();
        for (FallbackResourceManager resourceManager : resourceManager.domainResourceManagers.values()) {
            anotherList.addAll(resourceManager.resourcePacks);
        }
        return anotherList;
    }

    public static void removeAndReload(Collection<? extends IResourcePack> list) {
        Collection<IResourcePack> anotherList = getLoadedResourcePacks();
        int size = anotherList.size();
        anotherList.removeAll(list);
        if (anotherList.size() == size) {
            return;
        }

        ATHandler.clearResources(resourceManager);

        for (IResourcePack pack : anotherList) {
            resourceManager.reloadResourcePack(pack);
        }

        ilib.ATHandler.notifyReloadListeners(resourceManager);
    }

    @Override
    public void onResourceManagerReload(@Nonnull IResourceManager man) {
        ImpLib.HOOK.trigger(ilib.util.hook.Hook.LANGUAGE_RELOAD);
        if (Config.fixFont) {
            ClientProxy.mc.fontRenderer.setUnicodeFlag(false);
            ClientProxy.mc.fontRenderer.setBidiFlag(false);
        }
    }
}