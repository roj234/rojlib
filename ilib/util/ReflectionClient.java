/*
 * This file is a part of MoreItems
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
package ilib.util;

import roj.reflect.DirectAccessor;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import net.minecraftforge.fml.client.FMLClientHandler;

import java.util.BitSet;
import java.util.List;

/**
 * @author Roj233
 * @since 2021/8/25 1:06
 */
public interface ReflectionClient {
    ReflectionClient HELPER = preloadNecessaryClassesBeforeDefine(DirectAccessor.builder(ReflectionClient.class))
            .delayErrorToInvocation()
            .access(GuiMainMenu.class, "field_73975_c", null, "setMainMenuSplash")
            .access(FMLClientHandler.class, "resourcePackList")
            .delegate(BlockModelRenderer.class, "func_187496_a", "renderQuadsFlat")
            .access(SoundHandler.class, "field_147694_f", "getSoundManager", null)
            .access(SoundManager.class, "field_148617_f", "getInited", null)
            .build();

    static DirectAccessor<ReflectionClient> preloadNecessaryClassesBeforeDefine(DirectAccessor<ReflectionClient> builder) {
        return builder;
    }

    // region Field
    void setMainMenuSplash(GuiMainMenu menu, String splash);

    List<IResourcePack> getResourcePackList(FMLClientHandler handler);
    void setResourcePackList(FMLClientHandler handler, List<IResourcePack> list);

    SoundManager getSoundManager(SoundHandler h);
    boolean getInited(SoundManager h);

    // endregion
    // region Method
    void renderQuadsFlat(BlockModelRenderer r, IBlockAccess world, IBlockState state, BlockPos pos, int bright, boolean ownBright, BufferBuilder bb, List<BakedQuad> list, BitSet set);
    // endregion
}
