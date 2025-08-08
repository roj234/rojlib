package roj.ui;

import org.jetbrains.annotations.Nullable;
import roj.asmx.launcher.Conditional;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

/**
 * @author Roj234
 * @since 2025/08/24 05:09
 */
@Conditional(target = Clipboard.class, action = Conditional.Action.DUMMY)
public class ClipboardUtil {
	private static Clipboard clipboard;
	static {
		try {
			clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		} catch (HeadlessException ignored) {}
	}

	@Nullable
	public static String getClipboardText() {
		if (clipboard != null) {
			DataFlavor stringFlavor = DataFlavor.stringFlavor;
			if (clipboard.isDataFlavorAvailable(stringFlavor)) {
				try {
					return clipboard.getData(stringFlavor).toString();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return null;
	}
	public static boolean setClipboardText(String text) {
		if (clipboard == null) return false;
		clipboard.setContents(new StringSelection(text), null);
		return true;
	}
}
