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
package ilib.client.renderer.mirror.render;

import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

public class ClippingHelperMy extends ClippingHelper {
    private static final ClippingHelperMy instance = new ClippingHelperMy();

    private final FloatBuffer buffer = GLAllocation.createDirectFloatBuffer(16);

    /**
     * Initialises the ClippingHelper object then returns an instance of it.
     */
    public static ClippingHelper getInstance() {
        instance.init();
        return instance;
    }

    private void normalize(float[] af) {
        float f = MathHelper.sqrt(af[0] * af[0] + af[1] * af[1] + af[2] * af[2]);
        af[0] /= f;
        af[1] /= f;
        af[2] /= f;
        af[3] /= f;
    }

    public void init() {
        this.buffer.clear();
        GlStateManager.getFloat(GL11.GL_PROJECTION_MATRIX, this.buffer);

        float[] pm = this.projectionMatrix;
        this.buffer.flip().limit(16);
        this.buffer.get(pm);

        this.buffer.clear();
        GlStateManager.getFloat(GL11.GL_MODELVIEW_MATRIX, this.buffer);

        float[] mvm = this.modelviewMatrix;
        this.buffer.flip().limit(16);
        this.buffer.get(mvm);

        this.clippingMatrix[0] = mvm[0] * pm[0] + mvm[1] * pm[4] + mvm[2] * pm[8] + mvm[3] * pm[12];
        this.clippingMatrix[1] = mvm[0] * pm[1] + mvm[1] * pm[5] + mvm[2] * pm[9] + mvm[3] * pm[13];
        this.clippingMatrix[2] = mvm[0] * pm[2] + mvm[1] * pm[6] + mvm[2] * pm[10] + mvm[3] * pm[14];
        this.clippingMatrix[3] = mvm[0] * pm[3] + mvm[1] * pm[7] + mvm[2] * pm[11] + mvm[3] * pm[15];
        this.clippingMatrix[4] = mvm[4] * pm[0] + mvm[5] * pm[4] + mvm[6] * pm[8] + mvm[7] * pm[12];
        this.clippingMatrix[5] = mvm[4] * pm[1] + mvm[5] * pm[5] + mvm[6] * pm[9] + mvm[7] * pm[13];
        this.clippingMatrix[6] = mvm[4] * pm[2] + mvm[5] * pm[6] + mvm[6] * pm[10] + mvm[7] * pm[14];
        this.clippingMatrix[7] = mvm[4] * pm[3] + mvm[5] * pm[7] + mvm[6] * pm[11] + mvm[7] * pm[15];
        this.clippingMatrix[8] = mvm[8] * pm[0] + mvm[9] * pm[4] + mvm[10] * pm[8] + mvm[11] * pm[12];
        this.clippingMatrix[9] = mvm[8] * pm[1] + mvm[9] * pm[5] + mvm[10] * pm[9] + mvm[11] * pm[13];
        this.clippingMatrix[10] = mvm[8] * pm[2] + mvm[9] * pm[6] + mvm[10] * pm[10] + mvm[11] * pm[14];
        this.clippingMatrix[11] = mvm[8] * pm[3] + mvm[9] * pm[7] + mvm[10] * pm[11] + mvm[11] * pm[15];
        this.clippingMatrix[12] = mvm[12] * pm[0] + mvm[13] * pm[4] + mvm[14] * pm[8] + mvm[15] * pm[12];
        this.clippingMatrix[13] = mvm[12] * pm[1] + mvm[13] * pm[5] + mvm[14] * pm[9] + mvm[15] * pm[13];
        this.clippingMatrix[14] = mvm[12] * pm[2] + mvm[13] * pm[6] + mvm[14] * pm[10] + mvm[15] * pm[14];
        this.clippingMatrix[15] = mvm[12] * pm[3] + mvm[13] * pm[7] + mvm[14] * pm[11] + mvm[15] * pm[15];

        float[] f0 = this.frustum[0];
        f0[0] = this.clippingMatrix[3] - this.clippingMatrix[0];
        f0[1] = this.clippingMatrix[7] - this.clippingMatrix[4];
        f0[2] = this.clippingMatrix[11] - this.clippingMatrix[8];
        f0[3] = this.clippingMatrix[15] - this.clippingMatrix[12];
        this.normalize(f0);

        float[] f1 = this.frustum[1];
        f1[0] = this.clippingMatrix[3] + this.clippingMatrix[0];
        f1[1] = this.clippingMatrix[7] + this.clippingMatrix[4];
        f1[2] = this.clippingMatrix[11] + this.clippingMatrix[8];
        f1[3] = this.clippingMatrix[15] + this.clippingMatrix[12];
        this.normalize(f1);

        float[] f2 = this.frustum[2];
        f2[0] = this.clippingMatrix[3] + this.clippingMatrix[1];
        f2[1] = this.clippingMatrix[7] + this.clippingMatrix[5];
        f2[2] = this.clippingMatrix[11] + this.clippingMatrix[9];
        f2[3] = this.clippingMatrix[15] + this.clippingMatrix[13];
        this.normalize(f2);

        float[] f3 = this.frustum[3];
        f3[0] = this.clippingMatrix[3] - this.clippingMatrix[1];
        f3[1] = this.clippingMatrix[7] - this.clippingMatrix[5];
        f3[2] = this.clippingMatrix[11] - this.clippingMatrix[9];
        f3[3] = this.clippingMatrix[15] - this.clippingMatrix[13];
        this.normalize(f3);

        float[] f4 = this.frustum[4];
        f4[0] = this.clippingMatrix[3] - this.clippingMatrix[2];
        f4[1] = this.clippingMatrix[7] - this.clippingMatrix[6];
        f4[2] = this.clippingMatrix[11] - this.clippingMatrix[10];
        f4[3] = this.clippingMatrix[15] - this.clippingMatrix[14];
        this.normalize(f4);

        float[] f5 = this.frustum[5];
        f5[0] = this.clippingMatrix[3] + this.clippingMatrix[2];
        f5[1] = this.clippingMatrix[7] + this.clippingMatrix[6];
        f5[2] = this.clippingMatrix[11] + this.clippingMatrix[10];
        f5[3] = this.clippingMatrix[15] + this.clippingMatrix[14];
        this.normalize(f5);
    }
}