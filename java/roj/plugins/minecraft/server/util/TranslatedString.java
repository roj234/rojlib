package roj.plugins.minecraft.server.util;

import roj.config.serial.CVisitor;
import roj.ui.AnsiString;

/**
 * @author Roj234
 * @since 2024/3/19 0019 23:54
 */
public class TranslatedString extends AnsiString {
	private AnsiString[] with;

	public TranslatedString(CharSequence translate, AnsiString ... with) {
		super(translate);
		this.with = with;
	}

	@Override
	protected String getMinecraftType() { return "translate"; }
	@Override
	public void writeJson(CVisitor ser) {
		super.writeJson(ser);

		if (with.length > 0) {
			ser.key("with");
			ser.valueList(with.length);
			for (AnsiString string : with) {
				string.writeJson(ser);
			}
			ser.pop();
		}
	}
}