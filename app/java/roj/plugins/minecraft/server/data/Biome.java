package roj.plugins.minecraft.server.data;

/**
 * @author Roj234
 * @since 2024/3/20 7:12
 */
public class Biome {
	public static final Registry<Biome> REGISTRY = new Registry<>("biome", 256);
	public static final Biome VOID = new Biome();
	public static final Biome PLAINS = new Biome();
	static {
		REGISTRY.register(VOID, 0);
		REGISTRY.register(PLAINS, 1);
	}

	public String getKey() {
		return "void";
	}
}