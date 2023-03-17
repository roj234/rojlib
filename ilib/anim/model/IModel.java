package ilib.anim.model;

import roj.config.data.CMapping;
import roj.text.StringPool;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2021/6/13 13:26
 */
public interface IModel {
	int type();

	int capabilities();

	void config(int id, double value);

	void render(double ticks);

	void toByteArray(ByteList w, StringPool pool);

	void toJSON(CMapping mapping);
}
