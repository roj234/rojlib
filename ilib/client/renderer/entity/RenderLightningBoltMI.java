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
/**
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: RenderLightningBoltMI.java
 */
package ilib.client.renderer.entity;

import ilib.entity.EntityLightningBoltMI;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;

public class RenderLightningBoltMI extends Render<EntityLightningBoltMI> {
    public RenderLightningBoltMI(RenderManager p_i46157_1_) {
        super(p_i46157_1_);
    }

    public void doRender(EntityLightningBoltMI p_doRender_1_, double p_doRender_2_, double p_doRender_4_, double p_doRender_6_, float p_doRender_8_, float p_doRender_9_) {
        Tessellator lvt_10_1_ = Tessellator.getInstance();
        BufferBuilder lvt_11_1_ = lvt_10_1_.getBuffer();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        double[] lvt_12_1_ = new double[8];
        double[] lvt_13_1_ = new double[8];
        double lvt_14_1_ = 0.0D;
        double lvt_16_1_ = 0.0D;
        Random lvt_18_1_ = new Random(p_doRender_1_.boltVertex);
        for (int lvt_19_1_ = 7; lvt_19_1_ >= 0; lvt_19_1_--) {
            lvt_12_1_[lvt_19_1_] = lvt_14_1_;
            lvt_13_1_[lvt_19_1_] = lvt_16_1_;
            lvt_14_1_ += (lvt_18_1_.nextInt(11) - 5);
            lvt_16_1_ += (lvt_18_1_.nextInt(11) - 5);
        }
        for (int lvt_18_2_ = 0; lvt_18_2_ < 4; lvt_18_2_++) {
            Random lvt_19_2_ = new Random(p_doRender_1_.boltVertex);
            for (int lvt_20_1_ = 0; lvt_20_1_ < 3; lvt_20_1_++) {
                int lvt_21_1_ = 7;
                int lvt_22_1_ = 0;
                if (lvt_20_1_ > 0)
                    lvt_21_1_ = 7 - lvt_20_1_;
                if (lvt_20_1_ > 0)
                    lvt_22_1_ = lvt_21_1_ - 2;
                double lvt_23_1_ = lvt_12_1_[lvt_21_1_] - lvt_14_1_;
                double lvt_25_1_ = lvt_13_1_[lvt_21_1_] - lvt_16_1_;
                for (int lvt_27_1_ = lvt_21_1_; lvt_27_1_ >= lvt_22_1_; lvt_27_1_--) {
                    double lvt_28_1_ = lvt_23_1_;
                    double lvt_30_1_ = lvt_25_1_;
                    if (lvt_20_1_ == 0) {
                        lvt_23_1_ += (lvt_19_2_.nextInt(11) - 5);
                        lvt_25_1_ += (lvt_19_2_.nextInt(11) - 5);
                    } else {
                        lvt_23_1_ += (lvt_19_2_.nextInt(31) - 15);
                        lvt_25_1_ += (lvt_19_2_.nextInt(31) - 15);
                    }
                    lvt_11_1_.begin(5, DefaultVertexFormats.POSITION_COLOR);
                    float lvt_32_1_ = 0.5F;
                    float lvt_33_1_ = 0.45F;
                    float lvt_34_1_ = 0.45F;
                    float lvt_35_1_ = 0.5F;
                    double lvt_36_1_ = 0.1D + lvt_18_2_ * 0.2D;
                    if (lvt_20_1_ == 0)
                        lvt_36_1_ *= (lvt_27_1_ * 0.1D + 1.0D);
                    double lvt_38_1_ = 0.1D + lvt_18_2_ * 0.2D;
                    if (lvt_20_1_ == 0)
                        lvt_38_1_ *= ((lvt_27_1_ - 1) * 0.1D + 1.0D);
                    for (int lvt_40_1_ = 0; lvt_40_1_ < 5; lvt_40_1_++) {
                        double lvt_41_1_ = p_doRender_2_ + 0.5D - lvt_36_1_;
                        double lvt_43_1_ = p_doRender_6_ + 0.5D - lvt_36_1_;
                        if (lvt_40_1_ == 1 || lvt_40_1_ == 2)
                            lvt_41_1_ += lvt_36_1_ * 2.0D;
                        if (lvt_40_1_ == 2 || lvt_40_1_ == 3)
                            lvt_43_1_ += lvt_36_1_ * 2.0D;
                        double lvt_45_1_ = p_doRender_2_ + 0.5D - lvt_38_1_;
                        double lvt_47_1_ = p_doRender_6_ + 0.5D - lvt_38_1_;
                        if (lvt_40_1_ == 1 || lvt_40_1_ == 2)
                            lvt_45_1_ += lvt_38_1_ * 2.0D;
                        if (lvt_40_1_ == 2 || lvt_40_1_ == 3)
                            lvt_47_1_ += lvt_38_1_ * 2.0D;
                        lvt_11_1_.pos(lvt_45_1_ + lvt_23_1_, p_doRender_4_ + (lvt_27_1_ * 16), lvt_47_1_ + lvt_25_1_).color(0.45F, 0.45F, 0.5F, 0.3F).endVertex();
                        lvt_11_1_.pos(lvt_41_1_ + lvt_28_1_, p_doRender_4_ + ((lvt_27_1_ + 1) * 16), lvt_43_1_ + lvt_30_1_).color(0.45F, 0.45F, 0.5F, 0.3F).endVertex();
                    }
                    lvt_10_1_.draw();
                }
            }
        }
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
    }

    @Nullable
    protected ResourceLocation getEntityTexture(@Nonnull EntityLightningBoltMI entity) {
        return null;
    }
}
