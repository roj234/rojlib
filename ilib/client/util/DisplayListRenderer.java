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
package ilib.client.util;

import ilib.ClientProxy;
import ilib.Config;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.opengl.GL11;
import roj.math.MathUtils;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class DisplayListRenderer {
    private int listId = -1, tick;
    private double ox, oy, oz;
    private final boolean type;

    public DisplayListRenderer(boolean type) {
        this.type = type;
    }

    public void start(Entity entity, float partialTick) {
        this.ox = MathUtils.interpolate(entity.prevPosX, entity.posX, partialTick);
        this.oy = MathUtils.interpolate(entity.prevPosY, entity.posY, partialTick);
        this.oz = MathUtils.interpolate(entity.prevPosZ, entity.posZ, partialTick);

        tick = 0;
        if (this.listId < 0) {
            this.listId = GLAllocation.generateDisplayLists(1);
        }
        GlStateManager.glNewList(this.listId, GL11.GL_COMPILE_AND_EXECUTE);
    }

    public void end() {
        GlStateManager.glEndList();
    }

    public void draw(Entity entity, float partialTick) {
        double x = ox - MathUtils.interpolate(entity.prevPosX, entity.posX, partialTick);
        double y = oy - MathUtils.interpolate(entity.prevPosY, entity.posY, partialTick);
        double z = oz - MathUtils.interpolate(entity.prevPosZ, entity.posZ, partialTick);
        ClientProxy.mc.player.sendStatusMessage(new TextComponentString("T "  + partialTick + " D " + x + " " + y + " " + z), true);
        GlStateManager.translate(x * 2, y, z * 2);
        GL11.glCallList(this.listId);
    }

    @Override
    protected void finalize() {
        deleteGlBuffers();
    }

    public void deleteGlBuffers() {
        if (this.listId >= 0) {
            GLAllocation.deleteDisplayLists(this.listId);
            this.listId = -1;
        }
    }

    public boolean needUpdate() {
        return tick++ > (type ? Config.entityUpdateFreq : Config.tileUpdateFreq);
    }
}
