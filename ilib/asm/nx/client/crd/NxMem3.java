package ilib.asm.nx.client.crd;

import ilib.asm.util.MCHooksClient;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.math.MathUtils;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;

import net.minecraftforge.client.model.pipeline.LightUtil;
import net.minecraftforge.client.model.pipeline.VertexLighterFlat;

/**
 * @author Roj233
 * @since 2022/4/22 19:09
 */
@Nixim("net.minecraftforge.client.model.pipeline.VertexLighterFlat")
class NxMem3 extends VertexLighterFlat {
	public NxMem3(BlockColors colors) {
		super(colors);
	}

	@Shadow("/")
	private int tint = -1;
	@Shadow("/")
	private boolean diffuse;

	@Inject("/")
	@SuppressWarnings("fallthrough")
	protected void processQuad() {
		float[][] position = quadData[posIndex];
		float[][] normal = quadData[normalIndex];
		float[][] lightmap = quadData[lightmapIndex];
		float[][] color = quadData[colorIndex];
		int v;
		if (dataLength[normalIndex] < 3 || normal[0][0] == -1 && normal[0][1] == -1 && normal[0][2] == -1) {

			MCHooksClient mcr = MCHooksClient.get();

			normal = mcr.normals;

			float[] t = mcr.data2;

			float[] p0 = position[0];
			float[] p1 = position[1];
			float[] p2 = position[2];
			float[] p3 = position[3];
			for (int i = 0; i < 3; i++) {
				t[i] = p3[i] - p1[i];
				t[i + 3] = p2[i] - p0[i];
			}

			float x = t[1] * t[5] - t[4] * t[2];
			float y = t[2] * t[3] - t[5] * t[0];
			float z = t[0] * t[4] - t[3] * t[1];

			float abs = MathUtils.sqrt(x * x + y * y + z * z);
			x /= abs;
			y /= abs;
			z /= abs;

			for (v = 0; v < 4; ++v) {
				normal[v][0] = x;
				normal[v][1] = y;
				normal[v][2] = z;
				normal[v][3] = 0;
			}
		}

		int multiplier = -1;
		if (tint != -1) {
			multiplier = blockInfo.getColorMultiplier(tint);
		}

		VertexFormat format = parent.getVertexFormat();
		int count = format.getElementCount();

		for (v = 0; v < 4; ++v) {
			position[v][0] += blockInfo.getShx();
			position[v][1] += blockInfo.getShy();
			position[v][2] += blockInfo.getShz();
			float x = position[v][0] - 0.5F;
			float y = position[v][1] - 0.5F;
			float z = position[v][2] - 0.5F;
			x += normal[v][0] * 0.5F;
			y += normal[v][1] * 0.5F;
			z += normal[v][2] * 0.5F;
			float blockLight = lightmap[v][0];
			float skyLight = lightmap[v][1];
			updateLightmap(normal[v], lightmap[v], x, y, z);
			if (dataLength[lightmapIndex] > 1) {
				if (blockLight > lightmap[v][0]) {
					lightmap[v][0] = blockLight;
				}

				if (skyLight > lightmap[v][1]) {
					lightmap[v][1] = skyLight;
				}
			}

			updateColor(normal[v], color[v], x, y, z, (float) tint, multiplier);
			if (diffuse) {
				float d = LightUtil.diffuseLight(normal[v][0], normal[v][1], normal[v][2]);

				for (int i = 0; i < 3; ++i) {
					color[v][i] *= d;
				}
			}

			if (EntityRenderer.anaglyphEnable) {
				applyAnaglyph(color[v]);
			}

			for (int e = 0; e < count; ++e) {
				VertexFormatElement element = format.getElement(e);
				switch (element.getUsage().ordinal()) {
					case 0:
						parent.put(e, position[v]);
						break;
					case 1:
						parent.put(e, normal[v]);
						break;
					case 2:
						parent.put(e, color[v]);
						break;
					case 3:
						if (element.getIndex() == 1) {
							parent.put(e, lightmap[v]);
							break;
						}
					default:
						parent.put(e, quadData[e][v]);
				}
			}
		}

		tint = -1;
	}
}
