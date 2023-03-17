package ilib.asm.rpl;

import roj.mapper.Inherited;
import roj.opengl.texture.FastStitcher;
import roj.opengl.texture.IAtlasPiece;
import roj.util.Helpers;

import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Roj233
 * @since 2022/5/20 0:54
 */
@Inherited(Stitcher.class)
public class RplStitcher {
	private final FastStitcher delegate;
	private final ReentrantLock lock;

	public RplStitcher(int w, int h, int tileDimension, int mipmap) {
		this.delegate = new FastStitcher(w, h, tileDimension, mipmap);
		this.lock = new ReentrantLock();
	}

	public int func_110935_a() {
		return delegate.getWidth();
	}

	public int func_110936_b() {
		return delegate.getHeight();
	}

	public void func_110934_a(TextureAtlasSprite sprite) {
		lock.lock();
		try {
			delegate.addSprite((IAtlasPiece) sprite);
		} finally {
			lock.unlock();
		}
	}

	public void func_94305_f() {
		delegate.stitch();
	}

	public List<TextureAtlasSprite> func_94309_g() {
		return Helpers.cast(delegate.getRegisteredTextures());
	}
}
