package roj.plugins.kscript.func;

import roj.collect.SimpleList;
import roj.config.data.*;
import roj.plugins.kscript.VariableContext;
import roj.text.TextUtil;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/11/24 0024 3:52
 */
public class KSArray extends CList implements VariableContext {
	private final KSObject instance = new KSObject(this, Constants.ARRAY);

	public KSArray() {super();}
	public KSArray(int size) {super(size);}
	public KSArray(List<? extends CEntry> list) {super(list);}

	@Override public CMap asMap() {return instance;}

	@Override
	public void putVar(String name, CEntry value) {
		if (name.equals("length")) {
			var list = (SimpleList<CEntry>) raw();
			int size = value.asInt();
			if (size < 0) size = 0;

			var arr = list.getInternalArray();
			for (int i = size; i < list.size(); i++) arr[i] = null;

			list._setSize(size);
			return;
		}

		if (TextUtil.isNumber(name, TextUtil.INT_MAXS) == 0 && !name.startsWith("-")) {
			set(Integer.parseInt(name), value);
		}
	}

	@Override
	public CEntry getVar(String name) {
		if (name.equals("caller")) return CNull.NULL;

		if (name.equals("length")) return CInt.valueOf(size());
		if (TextUtil.isNumber(name, TextUtil.INT_MAXS) == 0 && !name.startsWith("-")) {
			int i = Integer.parseInt(name);
			if (i < size()) return get(i);
		}
		throw new IllegalArgumentException("无效的参数, 需要'length'或int32数字");
	}
}
