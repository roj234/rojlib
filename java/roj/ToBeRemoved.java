package roj;

import roj.config.ConfigMaster;
import roj.config.auto.Serializers;
import roj.text.CharList;
import roj.util.Helpers;

/**
 * 准备删掉的东西
 */
public class ToBeRemoved {
	/**
	 * 深度转换为字符串
	 * @deprecated 这个方案比之前的好，但是依然做不到contextual
	 */
	@Deprecated
	public static String deepToString(Object o) {return ConfigMaster.YAML.writeObject(Serializers.UNSAFE.serializer(o.getClass()), Helpers.cast(o), new CharList()).toStringAndFree();}
}