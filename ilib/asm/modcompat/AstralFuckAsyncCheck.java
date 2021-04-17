package ilib.asm.modcompat;

import ilib.ImpLib;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

@Nixim("hellfirepvp.astralsorcery.common.constellation.perk.PerkEffectHelper")
public class AstralFuckAsyncCheck {
	@Inject(at = Inject.At.INVOKE, value = "/", param = {"getName",
														 "threadGetName"}, desc = "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraftforge/fml/relauncher/Side;Ljava/util" + "/Collection;Lhellfirepvp/astralsorcery/common" + "/constellation/perk/AbstractPerk;)V")
	private void batchApplyConverters() {}

	@Inject(at = Inject.At.INVOKE, value = "/", param = {"getName",
														 "threadGetName"}, desc = "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraftforge/fml/relauncher/Side;Ljava/util" + "/Collection;Lhellfirepvp/astralsorcery/common" + "/constellation/perk/AbstractPerk;)V")
	private void batchRemoveConverters() {}

	@Copy(unique = true)
	private static String threadGetName(Thread thread) {
		return thread == ImpLib.proxy.getClientThread() ? "Client thread" : thread == ImpLib.proxy.getServerThread() ? "Server thread" : thread.getName();
	}
}
