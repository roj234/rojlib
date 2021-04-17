package ilib.asm.nx.client.async;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.opengl.texture.IAtlasPiece;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.ReportedException;

import java.util.ArrayList;

/**
 * @author Roj233
 * @since 2022/5/20 21:41
 */
@Nixim(value = "/", copyItf = true)
class NxSprite extends TextureAtlasSprite implements IAtlasPiece {
	@Inject(value = "/", at = Inject.At.TAIL)
	protected NxSprite(String spriteName) {
		super(spriteName);
		framesTextureData = new ArrayList<>(1);
	}

	@Inject("/")
	public void generateMipmaps(int level) {
		for (int i = framesTextureData.size() - 1; i >= 0; --i) {
			int[][] arr = framesTextureData.get(i);
			if (arr != null) {
				try {
					framesTextureData.set(i, TextureUtil.generateMipmapData(level, width, arr));
				} catch (Throwable e) {
					CrashReport rpt = CrashReport.makeCrashReport(e, "Generating mipmaps for frame");
					CrashReportCategory cat = rpt.makeCategory("Frame being iterated");
					cat.addCrashSection("Frame index", i);
					cat.addDetail("Frame sizes", () -> {
						StringBuilder sb = new StringBuilder();
						for (int[] arr1 : arr) {
							if (sb.length() > 0) {
								sb.append(", ");
							}
							sb.append(arr1 == null ? "null" : arr1.length);
						}
						return sb.toString();
					});
					throw new ReportedException(rpt);
				}
			} else {
				framesTextureData.remove(i);
			}
		}
		setFramesTextureData(framesTextureData);
	}

	@Copy
	public int getPieceWidth() {
		return width;
	}

	@Copy
	public int getPieceHeight() {
		return height;
	}

	@Copy
	public String getPieceName() {
		return getIconName();
	}

	@Copy
	public void onStitched(int atlasW, int atlasH, float atlasU1, float atlasV1, int x, int y, int actualW, int actualH, boolean rotated) {
		initSprite(atlasW, atlasH,x,y,rotated);
	}
}
