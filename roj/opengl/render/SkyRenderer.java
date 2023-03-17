/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: SkyRenderer.java
 */
package roj.opengl.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import roj.opengl.DisplayBuffer;
import roj.opengl.util.Util;
import roj.opengl.util.VboUtil;
import roj.opengl.vertex.VertexBuilder;
import roj.opengl.vertex.VertexFormat;

import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

public class SkyRenderer {
	public enum SkyboxType {ALL, ALL_HORIZONTAL, ALTERNATING, BOX}

	private static DisplayBuffer starVBO;

	private static void initialize() {
		if (starVBO == null) {
			/**
			 * Star
			 */
			starVBO = new DisplayBuffer(VertexFormat.POSITION, Util.sharedVertexBuilder, GL_QUADS, SkyRenderer::renderStars);
		}
	}

	private static final byte[] faceDown = {0, 1, 0, 0, 1, 0, 1, 1};
	private static final byte[] faceNorth = {0, 0, 0, 1, 1, 1, 1, 0};
	private static final byte[] faceSouth = {1, 1, 1, 0, 0, 0, 0, 1};
	private static final byte[] faceWest = {1, 0, 0, 0, 0, 1, 1, 1};
	private static final byte[] faceEast = {0, 1, 1, 1, 1, 0, 0, 0};

	/*public static void renderSkyTexture(TextureManager tm, String sky, String sky2, SkyboxType type) {
		prepareRenderSky();
		for (int i = 0; i < 6; ++i) {
			glPushMatrix();

			byte[] uv = faceDown;
			boolean white = true;

			switch (i) {
				case 0: {// Down face
					switch (type) {
						case ALL:
							tm.bindTexture(sky);
							break;
						case ALL_HORIZONTAL:
						case ALTERNATING:
							tm.bindTexture(sky2);
							break;
						default:
							white = false;
							break;
					}
				}
				break;
				case 1: {// North face
					tm.bindTexture(sky);
					glRotatef(90, 1, 0, 0);
					uv = faceNorth;
				}
				break;
				case 2: {       // South face
					tm.bindTexture(sky);
					glRotatef(-90, 1, 0, 0);
					uv = faceSouth;
				}
				break;
				case 3: {      // Up face
					glRotatef(180, 1, 0, 0);
					//uv = faceUp;
					//uv = faceDown;
					switch (type) {
						case ALL:
							tm.bindTexture(sky);
							break;
						case ALL_HORIZONTAL:
						case ALTERNATING:
							tm.bindTexture(sky2);
							break;
						default:
							white = false;
							break;
					}
				}
				break;
				case 4: {       // East face
					if (type == SkyboxType.ALTERNATING && sky2 != null) {
						tm.bindTexture(sky2);
					} else {
						tm.bindTexture(sky);
					}
					glRotatef(90, 0, 0, 1);
					uv = faceEast;
				}
				break;
				case 5: {       // West face
					if (type == SkyboxType.ALTERNATING && sky2 != null) {
						tm.bindTexture(sky2);
					} else {
						tm.bindTexture(sky);
					}
					glRotatef(-90, 0, 0, 1);
					uv = faceWest;
				}
			}

			renderSkyTexture(uv, white ? 255 : 0, 100);
			glPopMatrix();
		}
		postRenderSky();
	}*/

	private static void prepareRenderSky() {
		glDisable(GL_FOG);
		glDisable(GL_ALPHA_TEST);
		glEnable(GL_BLEND);
		GL14.glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
		glDepthMask(false);
	}

	private static void renderSkyTexture(byte[] uv, int color, int size) {
		VertexBuilder vb = Util.sharedVertexBuilder;
		vb.begin(VertexFormat.POSITION_TEX_COLOR);
		vb.pos(-size, -size, -size).tex(uv[0], uv[1]).color(color, color, color, 255).endVertex();
		vb.pos(-size, -size, size).tex(uv[2], uv[3]).color(color, color, color, 255).endVertex();
		vb.pos(size, -size, size).tex(uv[4], uv[5]).color(color, color, color, 255).endVertex();
		vb.pos(size, -size, -size).tex(uv[6], uv[7]).color(color, color, color, 255).endVertex();
		VboUtil.drawCPUVertexes(GL_QUADS, vb);
	}

	private static void postRenderSky() {
		glEnable(GL_DEPTH_TEST);
		glDepthMask(true);
		glEnable(GL_TEXTURE_2D);
		glEnable(GL_ALPHA_TEST);
	}

	public static void renderStar() {
		initialize();

		Util.color(0.2f, 0.4f, 0.6f);

		GL14.glBlendFuncSeparate(770, 1, 1, 0);
		GL11.glPushMatrix();
		float t = (System.currentTimeMillis() % 360000) / 1000f;
		GL11.glRotatef(t % 360f, 1, 0, 0);
		starVBO.draw();
		GL11.glPopMatrix();

		Util.color(0, 0, 0);

		postRenderSky();
	}

	private static void renderStars(VertexBuilder renderer) {
		Random rnd = new Random();

		for (int i = 0; i < 1500; ++i) {
			double d0 = (rnd.nextFloat() * 2 - 1);
			double d1 = (rnd.nextFloat() * 2 - 1);
			double d2 = (rnd.nextFloat() * 2 - 1);
			double d3 = (0.15F + rnd.nextFloat() * 0.1F);
			double d4 = d0 * d0 + d1 * d1 + d2 * d2;

			if (d4 < 1 && d4 > 0.01D) {
				d4 = 1 / Math.sqrt(d4);
				d0 *= d4;
				d1 *= d4;
				d2 *= d4;
				double d5 = d0 * 100;
				double d6 = d1 * 100;
				double d7 = d2 * 100;
				double d8 = Math.atan2(d0, d2);
				double d9 = Math.sin(d8);
				double d10 = Math.cos(d8);
				double d11 = Math.atan2(Math.sqrt(d0 * d0 + d2 * d2), d1);
				double d12 = Math.sin(d11);
				double d13 = Math.cos(d11);
				double d14 = rnd.nextDouble() * Math.PI * 2;
				double d15 = Math.sin(d14);
				double d16 = Math.cos(d14);

				for (int j = 0; j < 4; ++j) {
					double d17 = 0;
					double d18 = ((j & 2) - 1) * d3;
					double d19 = ((j + 1 & 2) - 1) * d3;
					double d20 = d18 * d16 - d19 * d15;
					double d21 = d19 * d16 + d18 * d15;
					double d22 = d20 * d12 + d17 * d13;
					double d23 = d17 * d12 - d20 * d13;
					double d24 = d23 * d9 - d21 * d10;
					double d25 = d21 * d9 + d23 * d10;
					renderer.pos(d5 + d24, d6 + d22, d7 + d25).endVertex();
				}
			}
		}
	}
}
