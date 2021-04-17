package ilib.asm.nx.client;

import ilib.Config;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

/**
 * @author Roj234
 * @since 2020/8/20 22:55
 */
@Nixim("net.minecraft.client.ClientBrandRetriever")
class NxClientBrand {
	@Inject("getClientModName")
	public static String getClientModName() {
		return Config.clientBrand;
	}
}
