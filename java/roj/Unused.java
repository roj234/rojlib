package roj;

import roj.compiler.runtime.RtUtil;
import roj.config.ConfigMaster;
import roj.config.Tokenizer;
import roj.config.auto.SerializerFactory;
import roj.gui.GuiUtil;
import roj.text.CharList;
import roj.ui.Terminal;
import roj.util.Helpers;

/**
 * 准备删掉的东西
 */
public class Unused {
	/**
	 * 深度转换为字符串
	 * @deprecated 这个方案比之前的好，但是依然做不到contextual
	 */
	@Deprecated
	public static String deepToString(Object o) {return ConfigMaster.YAML.writeObject(SerializerFactory.UNSAFE.serializer(o.getClass()), Helpers.cast(o), new CharList()).toStringAndFree();}

	public static void pack(int[] arr) {
		var sb = RtUtil.pack(arr);
		GuiUtil.setClipboardText(Tokenizer.addSlashes(sb, 0, new CharList().append('"'), '\'').append('"').toStringAndFree());
		Terminal.pause();
	}
	public static void pack(byte[] arr) {
		var sb = RtUtil.pack(arr);
		GuiUtil.setClipboardText(Tokenizer.addSlashes(sb, 0, new CharList().append('"'), '\'').append('"').toStringAndFree());
		Terminal.pause();
	}
}