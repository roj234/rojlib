package roj.minecraft.render.texture;

/**
 * @author Roj234
 * @since 2022/12/9 0009 16:31
 */
public interface IAtlasPiece {
	int getPieceWidth();
	int getPieceHeight();
	String getPieceName();

	void onStitched(int atlasW, int atlasH, float atlasU1, float atlasV1, int x, int y, int actualW, int actualH, boolean rotated);
}