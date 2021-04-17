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
package ilib.asm.nixim.client;

import ilib.Config;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEmitter;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/23 13:00
 */
@Nixim("net.minecraft.client.particle.ParticleManager")
public class NiximParticleManager extends ParticleManager {
    public NiximParticleManager(World worldIn, TextureManager rendererIn) {
        super(worldIn, rendererIn);
    }

    @Shadow("field_78876_b")
    private ArrayDeque<Particle>[][] fxLayers;
    @Shadow("field_178933_d")
    private Queue<ParticleEmitter> particleEmitters;
    @Shadow("field_187241_h")
    private Queue<Particle> queue;

    @Shadow("func_178922_a")
    private void updateEffectLayer(int layer) {
    }

    @Inject("func_78868_a")
    public void updateEffects() {
        for (int i = 0; i < 4; ++i) {
            this.updateEffectLayer(i);
        }

        if (!this.particleEmitters.isEmpty()) {
            for (Iterator<ParticleEmitter> iterator = this.particleEmitters.iterator(); iterator.hasNext(); ) {
                ParticleEmitter emitter = iterator.next();
                emitter.onUpdate();
                if (!emitter.isAlive()) {
                    iterator.remove();
                }
            }
        }

        if (!this.queue.isEmpty()) {
            Particle particle = this.queue.poll();
            while (particle != null) {
                int j = particle.getFXLayer();
                int k = particle.shouldDisableDepth() ? 0 : 1;
                if (this.fxLayers[j][k].size() >= Config.maxParticleCountPerLayer) {
                    this.fxLayers[j][k].removeFirst();
                }

                this.fxLayers[j][k].add(particle);
                particle = this.queue.poll();
            }
        }

    }
}
