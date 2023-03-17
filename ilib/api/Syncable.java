package ilib.api;

import ilib.tile.FieldSyncer;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public interface Syncable {
	FieldSyncer getSyncHandler();

	int getFieldCount1();

	void getFieldInfo(int[] array);

	void setFieldInfo(int id, int val);
}