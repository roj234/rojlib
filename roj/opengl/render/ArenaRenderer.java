package roj.opengl.render;

import org.lwjgl.opengl.GL11;
import roj.math.MathUtils;
import roj.math.Vec3i;
import roj.math.Vector;
import roj.opengl.util.Util;
import roj.opengl.util.VboUtil;
import roj.opengl.vertex.VertexBuilder;
import roj.opengl.vertex.VertexFormat;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class ArenaRenderer {
	public static final ArenaRenderer INSTANCE = new ArenaRenderer();

	private static final float DTHETA = 0.025F;
	private int color;
	private VertexBuilder vertexBuilder;

	public ArenaRenderer() {
		color = 0xFFFFFFFF;
		vertexBuilder = Util.sharedVertexBuilder;
	}

	public void setVertexBuilder(VertexBuilder vb) {
		vertexBuilder = vb;
	}

	public int getColor() {
		return color;
	}

	public void setColor(int color) {
		this.color = color;
	}

	public void render(Vector pos1, Vector pos2, float time) {
		GL11.glPushMatrix();
		GL11.glTranslated(pos1.x(), pos1.y(), pos1.z());

		float lx = (float) (pos2.x() - pos1.x());
		float ly = (float) (pos2.y() - pos1.y());
		float lz = (float) (pos2.z() - pos1.z());
		if (lx < 0) {
			GL11.glTranslatef(lx, 0, 0);
			lx = -lx;
		}
		if (ly < 0) {
			GL11.glTranslatef(0, ly, 0);
			ly = -ly;
		}
		if (lz < 0) {
			GL11.glTranslatef(0, 0, lz);
			lz = -lz;
		}

		if (pos1 instanceof Vec3i) {
			lx += 1;
			ly += 1;
			lz += 1;
		}

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glLineWidth(4);

		if ((lx > 15 || ly > 15 || lz > 15) && (lx * ly * lz < 1000000)) renderAnimated(lx, ly, lz, time);
		renderDefault(lx, ly, lz);

		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glPopMatrix();
	}

	public void renderDefault(float lx, float ly, float lz) {
		VertexBuilder bb = vertexBuilder;
		bb.begin(VertexFormat.POSITION);

		bb.pos(0, 0, 0).endVertex();
		bb.pos(lx, 0, 0).endVertex();
		bb.pos(lx, 0, lz).endVertex();
		bb.pos(0, 0, lz).endVertex();
		bb.pos(0, 0, 0).endVertex();

		bb.pos(0, ly, 0).endVertex();

		bb.pos(lx, ly, 0).endVertex();
		bb.pos(lx, 0, 0).endVertex();
		bb.pos(lx, ly, 0).endVertex();

		bb.pos(lx, ly, lz).endVertex();
		bb.pos(lx, 0, lz).endVertex();
		bb.pos(lx, ly, lz).endVertex();

		bb.pos(0, ly, lz).endVertex();
		bb.pos(0, 0, lz).endVertex();
		bb.pos(0, ly, lz).endVertex();

		bb.pos(0, ly, 0).endVertex();

		bb.end();

		Util.color(color);
		VboUtil.drawCPUVertexes(GL11.GL_LINE_STRIP, bb);
		Util.color(1, 1, 1);
	}

	public void renderAnimated(float lx, float ly, float lz, float time) {
		GL11.glDisable(GL11.GL_CULL_FACE);

		float theta = DTHETA * time;

		float r = ((color >>> 16) & 0xFF) / 255F;
		float g = ((color >>> 8) & 0xFF) / 255F;
		float b = (color & 0xFF) / 255F;
		float a = ((color >>> 24) & 0xFF) / 255F;

		VertexBuilder bb = vertexBuilder;
		bb.begin(VertexFormat.POSITION_COLOR);

		for (int i = 0; i < lx; i++) {
			for (int j = 0; j < lz; j++) {
				// j/2 和 i/2 : 控制轴上的速度
				float anime = (MathUtils.sin(theta + j / 2f + i / 2f) + 1) * 0.5F;

				// 0.3? 因为 方块大小不要太大
				if (anime >= 0.3f) continue;

				// 0.2是 0.5 - 0.3的结果
				float lenDis = anime + 0.2F;
				float lenDis_ = 1 - lenDis;

				// 这里的0.3要和上面一样
				anime = a * (1 - anime * (1 / 0.3f));

				bb.pos(i + lenDis, -0.025D, j + lenDis).colorf(r, g, b, anime).endVertex();
				bb.pos(i + lenDis_, -0.025D, j + lenDis).colorf(r, g, b, anime).endVertex();
				bb.pos(i + lenDis_, -0.025D, j + lenDis_).colorf(r, g, b, anime).endVertex();
				bb.pos(i + lenDis, -0.025D, j + lenDis_).colorf(r, g, b, anime).endVertex();

				bb.pos(i + lenDis, ly + 0.025F, j + lenDis).colorf(r, g, b, anime).endVertex();
				bb.pos(i + lenDis_, ly + 0.025F, j + lenDis).colorf(r, g, b, anime).endVertex();
				bb.pos(i + lenDis_, ly + 0.025F, j + lenDis_).colorf(r, g, b, anime).endVertex();
				bb.pos(i + lenDis, ly + 0.025F, j + lenDis_).colorf(r, g, b, anime).endVertex();
			}
		}

		for (int i = 0; i < lx; i++) {
			for (int j = 0; j < ly; j++) {
				float anime = (MathUtils.sin(theta + j / 2f + i / 2f) + 1) * 0.5F;

				if (anime >= 0.3f) continue;

				float lenDis = anime + 0.2F;
				float lenDis_ = 1 - lenDis;

				anime = a * (1 - anime * (1 / 0.3f));

				bb.pos(i + lenDis, j + lenDis, -0.025D).colorf(r, g, b, anime).endVertex();
				bb.pos(i + lenDis_, j + lenDis, -0.025D).colorf(r, g, b, anime).endVertex();
				bb.pos(i + lenDis_, j + lenDis_, -0.025D).colorf(r, g, b, anime).endVertex();
				bb.pos(i + lenDis, j + lenDis_, -0.025D).colorf(r, g, b, anime).endVertex();

				bb.pos(i + lenDis, j + lenDis, lz + 0.025F).colorf(r, g, b, anime).endVertex();
				bb.pos(i + lenDis_, j + lenDis, lz + 0.025F).colorf(r, g, b, anime).endVertex();
				bb.pos(i + lenDis_, j + lenDis_, lz + 0.025F).colorf(r, g, b, anime).endVertex();
				bb.pos(i + lenDis, j + lenDis_, lz + 0.025F).colorf(r, g, b, anime).endVertex();
			}
		}

		for (int i = 0; i < ly; i++) {
			for (int j = 0; j < lz; j++) {
				float anime = (MathUtils.sin(theta + j / 2f + i / 2f) + 1) * 0.5F;

				if (anime >= 0.3f) continue;

				float lenDis = anime + 0.2F;
				float lenDis_ = 1 - lenDis;

				anime = a * (1 - anime * (1 / 0.3f));

				bb.pos(-0.025D, i + lenDis, j + lenDis).colorf(r, g, b, anime).endVertex();
				bb.pos(-0.025D, i + lenDis_, j + lenDis).colorf(r, g, b, anime).endVertex();
				bb.pos(-0.025D, i + lenDis_, j + lenDis_).colorf(r, g, b, anime).endVertex();
				bb.pos(-0.025D, i + lenDis, j + lenDis_).colorf(r, g, b, anime).endVertex();

				bb.pos(lx + 0.025F, i + lenDis, j + lenDis).colorf(r, g, b, anime).endVertex();
				bb.pos(lx + 0.025F, i + lenDis_, j + lenDis).colorf(r, g, b, anime).endVertex();
				bb.pos(lx + 0.025F, i + lenDis_, j + lenDis_).colorf(r, g, b, anime).endVertex();
				bb.pos(lx + 0.025F, i + lenDis, j + lenDis_).colorf(r, g, b, anime).endVertex();
			}
		}
		VboUtil.drawCPUVertexes(GL11.GL_QUADS, bb);

		GL11.glEnable(GL11.GL_CULL_FACE);
	}
}
