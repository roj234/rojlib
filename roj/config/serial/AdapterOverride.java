package roj.config.serial;

import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.reflect.DirectAccessor;

/**
 * @author Roj234
 * @since 2023/3/24 0024 12:45
 */
public final class AdapterOverride {
	static boolean checked, have;
	public static synchronized boolean overridePermission() {
		if (checked) return have;
		checked = true;

		try {
			ConstantData c = Parser.parseConstants(IOUtil.readRes("roj/config/serial/Adapter.class"));
			c.parent(DirectAccessor.MAGIC_ACCESSOR_CLASS);
			ClassDefiner.INSTANCE.defineClassC(c);
			have = true;
		} catch (Throwable e) {
			e.printStackTrace();
		}

		return have;
	}
}
