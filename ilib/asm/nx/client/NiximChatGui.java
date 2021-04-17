package ilib.asm.nx.client;

import ilib.Config;
import org.apache.logging.log4j.Logger;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;

import java.util.Iterator;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Nixim("/")
abstract class NiximChatGui extends GuiNewChat {
	public NiximChatGui(Minecraft _lvt_1_) {
		super(_lvt_1_);
	}

	@Shadow("field_146249_a")
	static Logger xxLOGGER;

	@Shadow
	private List<ChatLine> chatLines;
	@Shadow
	private List<ChatLine> drawnChatLines;
	@Shadow
	private int scrollPos;
	@Shadow
	private boolean isScrolled;
	@Shadow
	Minecraft mc;

	@Inject
	private void setChatLine(ITextComponent text, int newId, int updateCounter, boolean noHistory) {
		if (newId != 0) {
			this.deleteChatLine(newId);
		}

		int _lvt_5_ = MathHelper.floor((float) this.getChatWidth() / this.getChatScale());
		List<ITextComponent> _lvt_6_ = GuiUtilRenderComponents.splitText(text, _lvt_5_, this.mc.fontRenderer, false, false);
		boolean isOpen = this.getChatOpen();

		ITextComponent _lvt_9_;
		for (Iterator<ITextComponent> it = _lvt_6_.iterator(); it.hasNext(); this.drawnChatLines.add(0, new ChatLine(updateCounter, _lvt_9_, newId))) {
			_lvt_9_ = it.next();
			if (isOpen && this.scrollPos > 0) {
				this.isScrolled = true;
				this.scroll(1);
			}
		}

		while (this.drawnChatLines.size() > Config.chatLength) {
			this.drawnChatLines.remove(this.drawnChatLines.size() - 1);
		}

		if (!noHistory) {
			this.chatLines.add(0, new ChatLine(updateCounter, text, newId));

			while (this.chatLines.size() > Config.chatLength) {
				this.chatLines.remove(this.chatLines.size() - 1);
			}
		}
	}

	@Inject
	public static int calculateChatboxHeight(float scale) {
		return MathHelper.floor(scale * Config.chatHeight + 20.0F);
	}

	@Inject
	public void printChatMessageWithOptionalDeletion(ITextComponent text, int id) {
		this.setChatLine(text, id, this.mc.ingameGUI.getUpdateCounter(), false);

		if (Config.logChat) xxLOGGER.info("[聊天] {}", text.getFormattedText().replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n"));
	}
}
