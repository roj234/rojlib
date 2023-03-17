package ilib.gui.util;

import ilib.client.RenderUtils;
import org.lwjgl.opengl.GL11;
import roj.opengl.util.Util;
import roj.opengl.util.VboUtil;
import roj.opengl.vertex.VertexBuilder;
import roj.opengl.vertex.VertexFormat;

import net.minecraft.util.ResourceLocation;

/**
 * @author Roj233
 * @since 2022/4/17 18:14
 */
public class SimpleSprite {
	public static final VertexFormat P2S_UV2F = VertexFormat.builder().pos(VertexFormat.SHORT, 2).uv2f().build();

	private final ResourceLocation tex;
	private final float u1, v1, u2, v2;

	public SimpleSprite(ResourceLocation texture, int u, int v, int w, int h) {
		tex = texture;
		float pw = 0.00390625F;
		this.u1 = u * pw;
		this.u2 = (u + w) * pw;
		this.v1 = v * pw;
		this.v2 = (v + h) * pw;
	}

	public void render(int x, int y, int w, int h) {
		RenderUtils.bindTexture(tex);

		VertexBuilder vb = Util.sharedVertexBuilder;
		vb.begin(P2S_UV2F);

		vb.pos(x, y + h).tex(u1, v2).endVertex();
		vb.pos(x + w, y + h).tex(u2, v2).endVertex();
		vb.pos(x + w, y).tex(u2, v1).endVertex();
		vb.pos(x, y).tex(u1, v1).endVertex();

		VboUtil.drawCPUVertexes(GL11.GL_QUADS, vb);
	}
}
