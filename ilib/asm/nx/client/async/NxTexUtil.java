package ilib.asm.nx.client.async;

import ilib.Config;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import java.awt.image.*;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj233
 * @since 2022/5/20 21:41
 */
@Nixim("net.minecraft.client.renderer.texture.TextureUtil")
class NxTexUtil {
	@Inject
	public static BufferedImage readBufferedImage(InputStream in) throws IOException {
		return AsyncTexHook.local.get().tman.readShared(in, Config.WTFIsIt);
	}
}
