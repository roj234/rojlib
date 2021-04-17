package ilib.asm.nx.client.crd;

import ilib.asm.util.MCHooksClient;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.util.ArrayCache;
import roj.util.Helpers;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.vertex.VertexFormat;

import net.minecraftforge.client.model.pipeline.IVertexConsumer;
import net.minecraftforge.client.model.pipeline.LightUtil;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * @author Roj233
 * @since 2022/4/22 19:42
 */
@Nixim("net.minecraftforge.client.model.pipeline.LightUtil")
class NxLight extends LightUtil {
	@Copy(staticInitializer = "create", targetIsFinal = true)
	public static Function<Pair<VertexFormat, VertexFormat>, int[]> FN;
	@Shadow("/")
	private static ConcurrentMap<Pair<VertexFormat, VertexFormat>, int[]> formatMaps;
	@Shadow("/")
	private static VertexFormat DEFAULT_FROM;
	@Shadow("/")
	private static VertexFormat DEFAULT_TO;
	@Shadow("/")
	private static int[] DEFAULT_MAPPING;

	private static void create() {
		FN = (pair) -> {
			return generateMapping(pair.getLeft(), pair.getRight());
		};
	}

	@Shadow("/")
	private static int[] generateMapping(VertexFormat from, VertexFormat to) {
		return null;
	}

	@Inject("/")
	public static void putBakedQuad(IVertexConsumer c, BakedQuad quad) {
		c.setTexture(quad.getSprite());
		c.setQuadOrientation(quad.getFace());
		if (quad.hasTintIndex()) c.setQuadTint(quad.getTintIndex());
		c.setApplyDiffuseLighting(quad.shouldApplyDiffuseLighting());

		float[] data = MCHooksClient.get().data;

		VertexFormat formatFrom = c.getVertexFormat();
		VertexFormat formatTo = quad.getFormat();
		int countFrom = formatFrom.getElementCount();
		int countTo = formatTo.getElementCount();

		int[] map = mapFormats(formatFrom, formatTo);

		for (int v = 0; v < 4; ++v) {
			for (int e = 0; e < countFrom; ++e) {
				if (map[e] != countTo) {
					unpack(quad.getVertexData(), data, formatTo, v, map[e]);
					c.put(e, data);
				} else {
					c.put(e, ArrayCache.FLOATS);
				}
			}
		}
	}

	@Inject("/")
	public static int[] mapFormats(VertexFormat from, VertexFormat to) {
		if (from.equals(DEFAULT_FROM) && to.equals(DEFAULT_TO)) {
			return DEFAULT_MAPPING;
		}

		MutablePair<VertexFormat, VertexFormat> fmt = Helpers.cast(MCHooksClient.get().pair);
		fmt.setLeft(from);
		fmt.setRight(to);
		return formatMaps.computeIfAbsent(fmt, FN);
	}
}
