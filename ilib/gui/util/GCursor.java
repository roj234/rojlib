package ilib.gui.util;

import org.lwjgl.LWJGLException;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Mouse;

import javax.imageio.ImageIO;
import java.awt.image.*;
import java.io.InputStream;
import java.nio.IntBuffer;

/**
 * @author Roj233
 * @since 2022/4/10 2:35
 */
public enum GCursor {
	DEFAULT, CLICK("click"), FORBID("forbid"), TEXT("text"), RESIZE("resize"), MOVE("move");

	private static final GCursor[] VALUES = values();
	private static GCursor current = DEFAULT;

	private Cursor lgCursor;

	GCursor() {
		lgCursor = Mouse.getNativeCursor();
	}

	GCursor(String id) {
		InputStream in = GCursor.class.getClassLoader().getResourceAsStream("assets/ilib/textures/gui/" + id + ".png");
		if (in == null) return;
		try {
			BufferedImage img = ImageIO.read(in);
			int[] arr = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
			//            for (int i = 0; i < arr.length; i++) {
			//                if (arr[i] == 0xFFFF0000) {
			//                    // remove red
			//                    arr[i] = 0;
			//                }
			//            }
			lgCursor = new Cursor(img.getWidth(), img.getHeight(), 0, 0, 1, IntBuffer.wrap(arr), null);
		} catch (Throwable e) {
			e.printStackTrace();
			lgCursor = Mouse.getNativeCursor();
		}
	}

	public static GCursor current() {
		return current;
	}

	public boolean apply() {
		try {
			current = this;
			Mouse.setNativeCursor(lgCursor);
			return true;
		} catch (LWJGLException e) {
			e.printStackTrace();
			return false;
		}
	}
}
