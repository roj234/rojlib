package roj.plugins.minecraft.server.util;

import roj.config.ValueEmitter;
import roj.ui.Text;

/**
 * @author Roj234
 * @since 2024/3/19 23:54
 */
public class TranslatedString extends Text {
	private Text[] with;

	public TranslatedString(CharSequence translate, Text... with) {
		super(translate);
		this.with = with;
	}

	@Override
	protected String getMinecraftType() { return "translate"; }
	@Override
	public void writeJson(ValueEmitter ser) {
		super.writeJson(ser);

		if (with.length > 0) {
			ser.emitKey("with");
			ser.emitList(with.length);
			for (Text string : with) {
				string.writeJson(ser);
			}
			ser.pop();
		}
	}
}