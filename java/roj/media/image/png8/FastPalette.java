package roj.media.image.png8;

import roj.collect.Int2IntMap;

/**
 * @author Roj234
 * @since 2024/5/19 0019 22:39
 */
public final class FastPalette implements Palette {
	private final Int2IntMap palette = new Int2IntMap(256);
	private boolean transparent;

	public FastPalette(int[] bitmap, int width, int height) {
		int c = 0;
		int i = 0;
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				c = bitmap[i++];
				if (c < 0xFF000000) transparent = true;
				palette.putIntIfAbsent(c, palette.size());
			}
		}
	}

	@Override
	public int getColorIndex(int argb) {return palette.getEntry(argb).v;}
}