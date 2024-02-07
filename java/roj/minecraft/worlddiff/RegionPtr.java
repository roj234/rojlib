package roj.minecraft.worlddiff;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/12/21 0021 7:19
 */
public class RegionPtr {
	String path;
	byte[] head_hash;
	List<ChunkPtr> chunks;
}
