package ilib.asm.nx.client;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.GuiTextField;

/**
 * @author Roj233
 * @since 2022/5/7 0:19
 */
@Nixim("/")
abstract class NxAnvilLen extends GuiRepair {
	@Shadow("field_147091_w")
	GuiTextField nameField;

	@Override
	@Inject(value = "/", at = Inject.At.TAIL)
	public void initGui() {
		nameField.setMaxStringLength(100);
	}

	NxAnvilLen() {
		super(null, null);
	}
}
