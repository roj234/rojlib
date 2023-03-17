package roj.asm.nixim;

import roj.asm.tree.MoFNode;
import roj.asm.tree.anno.AnnValString;

import java.util.List;
import java.util.Map;

/**
 * @author solo6975
 * @since 2022/5/22 1:19
 */
public interface NiximHelper {
	Map<String, NiximSystem.NiximData> getParentMap();

	default boolean transformParameter() {
		return true;
	}

	default String map(String owner, MoFNode name, String desc) {
		return desc;
	}

	default boolean shouldApply(String annotation, MoFNode node, List<AnnValString> argument) {
		throw new UnsupportedOperationException();
	}
}
