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
package ilib.client.mirror;

import ilib.ImpLib;
import ilib.client.mirror.render.WorldRenderer;
import ilib.net.MyChannel;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class Mirror {
    public static int maxRecursion = 2;
    public static int maxRenderPerTick = 10;
    public static int maxRenderDistanceChunks = 6;
    public static int minStencilLevel = 1;

    public static MyChannel CHANNEL = new MyChannel("prt");

    public static void init() {
        CHANNEL.registerMessage(null, PktChunkData.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(null, PktNewWorld.class, 1, Side.CLIENT);

        if (ImpLib.isClient) initClient();
    }

    @SideOnly(Side.CLIENT)
    private static void initClient() {
        MinecraftForge.EVENT_BUS.register(WorldRenderer.class);
    }
}
