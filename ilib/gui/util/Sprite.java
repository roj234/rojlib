package ilib.gui.util;

import net.minecraft.util.ResourceLocation;

/**
 * @author Roj233
 * @since 2022/4/17 17:52
 */
public interface Sprite {
	ResourceLocation texture();

	default int offsetX() {
		return 0;
	}

	default int offsetY() {
		return 0;
	}

	int u();

	int v();

	int w();

	int h();

	//void render(int x, int y, int w, int h);
}
