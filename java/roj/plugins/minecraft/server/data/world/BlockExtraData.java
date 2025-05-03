package roj.plugins.minecraft.server.data.world;

/**
 * @author Roj234
 * @since 2024/3/20 5:39
 */
public interface BlockExtraData {
	boolean tickable();
	void tick();
}