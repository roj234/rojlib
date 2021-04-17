package roj.opengl.util;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import roj.math.Mat4f;
import roj.math.Mat4x3f;
import roj.opengl.vertex.VertexBuilder;
import roj.opengl.vertex.VertexFormat;

import java.nio.FloatBuffer;

/**
 * Rendering Util
 *
 * @author Roj233
 * @since 2021/9/18 15:42
 */
public class Util {
	public static VertexBuilder sharedVertexBuilder = new VertexBuilder(4096);

	public static void color(float red, float green, float blue) {
		GL11.glColor4f(red, green, blue, 1);
	}

	public static void color(int color, float alpha) {
		float red = (color >> 16 & 0xFF) / 255F;
		float green = (color >> 8 & 0xFF) / 255F;
		float blue = (color & 0xFF) / 255F;
		GL11.glColor4f(red, green, blue, alpha);
	}

	public static void color(int color) {
		float red = ((color >>> 16) & 0xFF) / 255F;
		float green = ((color >>> 8) & 0xFF) / 255F;
		float blue = (color & 0xFF) / 255F;
		float alpha = ((color >>> 24) & 0xFF) / 255F;
		GL11.glColor4f(red, green, blue, alpha);
	}

	/**
	 * 圆柱
	 */
	public static void drawCylinder(float cx, float cy, float cz, float r, float height) {
		VertexBuilder bb = sharedVertexBuilder;
		float x, z;
		float x2, z2;

		double angle = 0;
		double delta = Math.PI / 16.0;

		while (angle < Math.PI * 2) {
			x = (float) (cx + (Math.sin(angle) * r));
			z = (float) (cz + (Math.cos(angle) * r));

			x2 = (float) (cx + (Math.sin(angle + delta) * r));
			z2 = (float) (cz + (Math.cos(angle + delta) * r));

			bb.pos(x2, cy, z2).endVertex();
			bb.pos(x2, cy + height, z2).endVertex();
			bb.pos(x, cy + height, z).endVertex();
			bb.pos(x, cy, z).endVertex();

			angle += delta;
		}
	}

	public static void drawCircleZ(double cx, double cy, double cz, double r) {
		VertexBuilder bb = sharedVertexBuilder;
		double angle = 0;
		double delta = Math.PI / 24.0;

		bb.pos(cx, cz, cy).endVertex();
		while (angle < Math.PI * 2) {
			double x = cx + (r * Math.cos(angle));
			double y = cy + (r * Math.sin(angle));

			bb.pos(x, cz, y).endVertex();

			angle += delta;
		}
	}

	static final VertexFormat POS2 = VertexFormat.builder().pos(VertexFormat.FLOAT, 2).build();

	public static void drawCircle(double cx, double cy, double r) {
		VertexBuilder bb = sharedVertexBuilder;
		double angle = 0;
		double delta = Math.PI / 24.0;

		bb.begin(POS2);
		bb.pos(cx, cy, 0).endVertex();
		while (angle < Math.PI * 2) {
			double x = cx + (r * Math.cos(angle));
			double y = cy + (r * Math.sin(angle));

			bb.pos(x, y, 0).endVertex();

			angle += delta;
		}
		VboUtil.drawCPUVertexes(GL11.GL_TRIANGLE_FAN, bb);
	}

	public static void drawXYZ(int length) {
		GL11.glLineWidth(2);
		VertexBuilder bb = sharedVertexBuilder;
		// Axis
		bb.begin(VertexFormat.POSITION_COLOR);
		// X
		bb.pos(0, 0, 0).color(255, 0, 0, 255).endVertex();
		bb.pos(length, 0, 0).color(255, 0, 0, 255).endVertex();
		bb.pos(length, 0.1f, 0).color(255, 0, 0, 255).endVertex();
		bb.pos(0, 0.1f, 0).color(255, 0, 0, 255).endVertex();
		// Y
		bb.pos(0, 0, 0).color(0, 255, 0, 255).endVertex();
		bb.pos(0, length, 0).color(0, 255, 0, 255).endVertex();
		bb.pos(0, length, 0.1f).color(0, 255, 0, 255).endVertex();
		bb.pos(0, 0, 0.1f).color(0, 255, 0, 255).endVertex();
		// Z
		bb.pos(0, 0, 0).color(0, 0, 255, 255).endVertex();
		bb.pos(0, 0, length).color(0, 0, 255, 255).endVertex();
		bb.pos(0.1f, 0, length).color(0, 0, 255, 255).endVertex();
		bb.pos(0.1f, 0, 0).color(0, 0, 255, 255).endVertex();
		bb.end();
		VboUtil.drawCPUVertexes(GL11.GL_QUADS, bb);
		// End Axis
	}

	public static void drawBar(VertexBuilder vb, int x, int y, int w, int h, int c) {
		vb.pos(x, y+h, 0).color(c).endVertex();
		vb.pos(x+w, y+h, 0).color(c).endVertex();
		vb.pos(x+w, y, 0).color(c).endVertex();
		vb.pos(x, y, 0).color(c).endVertex();
	}

	public static void bindTexture(int id) {
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
	}

	static FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

	public static void loadMatrix(Mat4f m) {
		matrixBuffer
			.put(m.m00).put(m.m01).put(m.m02).put(m.m03)
			.put(m.m10).put(m.m11).put(m.m12).put(m.m13)
			.put(m.m20).put(m.m21).put(m.m22).put(m.m23)
			.put(m.m30).put(m.m31).put(m.m32).put(m.m33)
			.flip();
		GL11.glMultMatrix(matrixBuffer);
	}

	public static void loadMatrix(Mat4x3f m) {
		matrixBuffer.put(m.m00).put(m.m01).put(m.m02).put(m.m03).put(m.m10).put(m.m11).put(m.m12).put(m.m13).put(m.m20).put(m.m21).put(m.m22).put(m.m23).put(0).put(0).put(0).put(1).flip();
		GL11.glMultMatrix(matrixBuffer);
	}
}
