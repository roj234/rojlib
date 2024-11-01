package roj.math;

/**
 * @author Roj234
 * @since 2022/12/9 0009 16:31
 */
public interface Tile {
	int getTileWidth();
	int getTileHeight();
	String getTileName();

	void onStitched(int atlasW, int atlasH, float atlasU1, float atlasV1, int x, int y, int actualW, int actualH, boolean rotated);
}
