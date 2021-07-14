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

import net.minecraft.block.Block;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraftforge.fml.client.CustomModLoadingErrorDisplayException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/1/2 0:47
 */
final class MoreIdMissingException extends CustomModLoadingErrorDisplayException {
    static final long serialVersionUID = 2333L;

    private final Block block;

    MoreIdMissingException(Block block) {
        this.block = block;
    }

    @Override
    public void initGui(GuiErrorScreen gui, FontRenderer fr) {
    }

    @Override
    public void drawScreen(GuiErrorScreen gui, FontRenderer fr, int mouseX, int mouseY, float partialTicks) {
        fr.drawString("方块 " + block.getRegistryName() + " 需要更高的meta上限， 然而MoreId没有正确安装", 20, gui.height / 2f, 0xff0000, false);
    }
}
