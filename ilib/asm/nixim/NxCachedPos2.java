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
package ilib.asm.nixim;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEventData;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.util.Helpers;

import java.util.ArrayList;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.world.WorldServer")
abstract class NxCachedPos2 extends WorldServer {
    public NxCachedPos2(MinecraftServer server, ISaveHandler saveHandlerIn, WorldInfo info, int dimensionId, Profiler profilerIn) {
        super(server, saveHandlerIn, info, dimensionId, profilerIn);
    }

    @Shadow("field_147490_S")
    private WorldServer.ServerBlockEventList[] blockEventQueue;
    @Shadow("field_147489_T")
    private int blockEventCacheIndex;

    @Inject("func_175641_c")
    public void addBlockEvent(BlockPos pos, Block block, int id, int param) {
        BlockEventData nextTick = new BlockEventData(pos.toImmutable(), block, id, param);
        ArrayList<?> list = this.blockEventQueue[this.blockEventCacheIndex];
        if (!list.contains(nextTick)) {
            list.add(Helpers.cast(nextTick));
        }
    }
}