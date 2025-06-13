package roj.plugins.minecraft.diff;

import roj.collect.HashSet;
import roj.math.Tile;

/**
 * @author Roj234
 * @since 2024/12/5 7:23
 */
public class ChunkGroup implements Tile {
	private int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
	HashSet<Long> chunks = new HashSet<>();
	int offsetX, offsetZ;
	private int id = xx++;
	private static int xx;

	@Override public int getTileWidth() {return maxX - minX + 2;}
	@Override public int getTileHeight() {return maxZ - minZ + 2;}
	@Override public String getTileName() {return "cg["+minX+","+minZ+","+maxX+","+maxZ+"]~"+id;}

	@Override
	public void onStitched(int atlasW, int atlasH, float atlasU1, float atlasV1, int x, int y, int actualW, int actualH, boolean rotated) {
		offsetX = x - minX + 1;
		offsetZ = y - minZ + 1;
		assert actualW == getTileWidth();
		assert actualH == getTileHeight();
		assert !rotated;
	}

	public void addAll(HashSet<Long> bfs) {
		chunks.addAll(bfs);
		for (long pos : bfs) {
			int x = (int) (pos >>> 32);
			int z = (int) pos;

			if (maxX < x) maxX = x;
			if (minX > x) minX = x;
			if (maxZ < z) maxZ = z;
			if (minZ > z) minZ = z;
		}
	}
}
