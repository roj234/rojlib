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

package ilib;

import ilib.capabilities.Capabilities;
import ilib.client.mirror.Mirror;
import ilib.client.model.BlockStateBuilder;
import ilib.event.CommonEvent;
import ilib.misc.MiscOptimize;
import ilib.util.Hook;
import ilib.util.PlayerUtil;

import net.minecraft.util.ResourceLocation;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class ServerProxy {
    Thread serverThread;

    public BlockStateBuilder getBlockMergedModel() {
        return null;
    }
    public BlockStateBuilder getItemMergedModel() {
        return null;
    }
    public BlockStateBuilder getFluidMergedModel() {
        return null;
    }
    public void registerTexture(ResourceLocation tex) {
        throw new IllegalStateException("Wrong side");
    }

    public boolean isOnThread(boolean client) {
        return !client && Thread.currentThread() == serverThread;
    }

    public void runAtMainThread(boolean client, Runnable run) {
        PlayerUtil.getMinecraftServer().addScheduledTask(run);
    }

    void setServerThread(Thread t) {
        if (this.serverThread != null && t != null)
            throw new IllegalStateException("Server Thread already set");
        this.serverThread = t;
    }

    void preInit() {
        CommonEvent.init();
        Capabilities.init();

        MiscOptimize.fixVanillaTool();

        if (Config.moreEggs)
            MiscOptimize.giveMeSomeEggs();
    }

    void init() {
        ImpLib.HOOK.remove(Hook.MODEL_REGISTER);

        Mirror.init();
    }

    void postInit() {

    }
}
