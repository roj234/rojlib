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
package roj.opengl;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import roj.opengl.altas.TextureAtlas;
import roj.opengl.render.SkyRenderer;
import roj.opengl.util.Util;

import java.io.IOException;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.glDisable;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/19 13:50
 */
public class Test extends Game {
    public static void main(String[] args) throws LWJGLException, IOException {
        Game game = new Test();
        game.create();
        while (!Display.isCloseRequested()) {
            game.mainLoop();
        }
    }

    protected void init() {
        TextureAtlas atlas = new TextureAtlas();
        Util.sharedVertexBuilder = vertexBuilder;
    }

    @Override
    public void processInput() {

        super.processInput();
    }

    @Override
    protected void renderSky() {
        GL11.glCullFace(GL11.GL_BACK);
        SkyRenderer.renderStar();
        GL11.glCullFace(GL11.GL_FRONT);
    }

    @Override
    protected void render3D() {
        glDisable(GL_BLEND);
        glDisable(GL11.GL_CULL_FACE);

        Util.drawXYZ(33);

        // stub code

        GL11.glEnable(GL11.GL_CULL_FACE);
    }
}
