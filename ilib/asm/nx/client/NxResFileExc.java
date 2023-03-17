package ilib.asm.nx.client;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Nixim;

/**
 * @author Roj233
 * @since 2022/6/2 20:53
 */
@Nixim("net.minecraft.client.resources.ResourcePackFileNotFoundException")
class NxResFileExc extends Throwable {
	@Override
	@Copy
	public Throwable fillInStackTrace() {
		return this;
	}
}
