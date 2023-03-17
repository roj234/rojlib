package ilib.asm.nx.mock;

import ilib.ClientProxy;
import ilib.net.mock.MockingUtil;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.MyHashMap;

import net.minecraftforge.fml.common.ModContainer;

import java.util.List;
import java.util.Map;

/**
 * @author solo6975
 * @since 2022/3/31 20:44
 */
@Nixim("net.minecraftforge.fml.common.network.handshake.FMLHandshakeMessage$ModList")
class MockMods {
	@Shadow("/")
	private final Map<String, String> modTags;

	@Inject("<init>")
	public MockMods(List<ModContainer> modList) {
		this.modTags = new MyHashMap<>();
		if (MockingUtil.mockMods.isEmpty() || ClientProxy.mc.getIntegratedServer() != null) {
			for (ModContainer mod : modList) {
				this.modTags.put(mod.getModId(), mod.getVersion());
			}
		} else {
			for (String[] mod : MockingUtil.mockMods) {
				this.modTags.put(mod[0], mod[1]);
			}
		}
	}
}
