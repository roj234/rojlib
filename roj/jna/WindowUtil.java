package roj.jna;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import roj.collect.SimpleList;
import roj.reflect.DirectAccessor;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static com.sun.jna.platform.win32.WinUser.*;

/**
 * @author Roj233
 * @since 2022/6/12 0:44
 */
public class WindowUtil {
	static final User32 User32 = com.sun.jna.platform.win32.User32.INSTANCE;
	public static final GDI32 Gdi32 = GDI32.INSTANCE;

	interface H {
		Object getWinDisplay();

		long getPointer(Object display);

		long getHdc(Object display);

		Map<Class<?>, List<String>> fieldOrder();
	}

	static final H Util;

	static {
		Class<?> clz;
		try {
			clz = Class.forName("org.lwjgl.opengl.WindowsDisplay");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		Util = DirectAccessor.builder(H.class)
							 .access(clz, new String[] {"current_display", "hwnd", "hdc"}, new String[] {"getWinDisplay", "getPointer", "getHdc"}, null)
							 .access(Structure.class, "fieldOrder", "fieldOrder", null)
							 .build();
	}

	public final HWND hWnd;
	private HRGN whitespace;

	public WindowUtil() {
		long realPointer = Util.getPointer(Util.getWinDisplay());
		HWND hWnd = new HWND(new Pointer(realPointer));
		User32.SetWindowLong(hWnd, GWL_EXSTYLE, User32.GetWindowLong(hWnd, GWL_EXSTYLE) | WS_EX_LAYERED);
		this.hWnd = hWnd;
	}

	public void setTransparency(float v) {
		User32.SetLayeredWindowAttributes(hWnd, 0, (byte) (v * 256), LWA_ALPHA);
	}

	private static void IWillUse(Class<? extends Structure> sc) {
		Map<Class<?>, List<String>> map = Util.fieldOrder();
		if (!map.containsKey(sc)) {
			SimpleList<String> fields = new SimpleList<>();
			Class<?> clazz = sc;
			while (clazz != Structure.class) {
				for (Field field : clazz.getDeclaredFields()) {
					fields.add(field.getName());
				}
				clazz = clazz.getSuperclass();
			}
			map.put(sc, fields);
		}
	}
}
