package ilib.asm.nx.client;

import org.apache.logging.log4j.Logger;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import java.awt.*;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * @author solo6975
 * @since 2022/5/2 23:06
 */
@Nixim("net.minecraft.client.gui.GuiScreen")
class NxFastUri {
	@Shadow
	private static Logger LOGGER;

	@Inject
	private void openWebLink(URI url) {
		CompletableFuture.runAsync(() -> {
			try {
				Desktop.getDesktop().browse(url);
			} catch (Throwable e) {
				Throwable cause = e.getCause();
				LOGGER.error("Couldn't open link: {}", cause == null ? "<UNKNOWN>" : cause.getMessage());
			}
		});
	}
}
