package ilib.asm.nx.client.async;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Nixim;

import net.minecraftforge.client.model.ModelLoaderRegistry;

/**
 * @author Roj233
 * @since 2022/5/20 6:14
 */
@Nixim("/")
class NxLoaderEx extends ModelLoaderRegistry.LoaderException {
	NxLoaderEx() {super("");}

	@Copy
	@Override
	public Throwable fillInStackTrace() {
		return this;
	}
}
