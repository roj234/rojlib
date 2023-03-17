package roj.config;

import roj.config.data.*;
import roj.config.serial.Serializers;
import roj.util.Helpers;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Roj233
 * @since 2022/5/17 2:15
 */
public class Wrapping {
	public static CEntry wrap(Object o) {
		return wrap(o, null);
	}

	public static CEntry wrap(Object o, Serializers ser) {
		if (o == null) {
			return CNull.NULL;
		} else if (o instanceof Object[]) {
			Object[] arr = (Object[]) o;
			CList dst = new CList(arr.length);
			for (Object o1 : arr) dst.add(wrap(o1, ser));
			return dst;
		} else if (o.getClass().getComponentType() != null) {
			switch (o.getClass().getComponentType().getName()) {
				case "int": return Serializers.wArray((int[]) o);
				case "byte":return Serializers.wArray((byte[]) o);
				case "boolean":return Serializers.wArray((boolean[]) o);
				case "char":return Serializers.wArray((char[]) o);
				case "long":return Serializers.wArray((long[]) o);
				case "short":return Serializers.wArray((short[]) o);
				case "float":return Serializers.wArray((float[]) o);
				case "double":return Serializers.wArray((double[]) o);
				default:throw new UnsupportedOperationException("void[] ??? " + o.getClass());
			}
		} else if (o instanceof Map) {
			Map<String, Object> map = Helpers.cast(o);
			CMapping dst = new CMapping(map.size());
			try {
				for (Map.Entry<String, Object> entry : map.entrySet()) {
					dst.put(entry.getKey(), wrap(entry.getValue()));
				}
			} catch (ClassCastException e) {
				if (ser != null) return ser.serialize(o);
				throw new UnsupportedOperationException("简易序列化的map必须使用string做key!");
			}
			return dst;
		} else if (o instanceof List) {
			List<Object> list = Helpers.cast(o);
			CList dst = new CList(list.size());
			for (int i = 0; i < list.size(); i++)
				dst.add(wrap(list.get(i), ser));
			return dst;
		} else if (o instanceof Collection) {
			Collection<Object> list = Helpers.cast(o);
			CList dst = new CList(list.size());
			for (Object o1 : list) dst.add(wrap(o1, ser));
			return dst;
		} else if (o instanceof CharSequence) {
			return CString.valueOf(o.toString());
		} else if (o instanceof Boolean) {
			return CBoolean.valueOf((Boolean) o);
		} else if (o instanceof Long) {
			return CLong.valueOf((Long) o);
		} else if (o instanceof Double || o instanceof Float) {
			return CDouble.valueOf(((Number) o).doubleValue());
		} else if (o instanceof Number) {
			return CInteger.valueOf(((Number) o).intValue());
		} else if (o instanceof Character) {
			return CInteger.valueOf((Character) o);
		} else if (o instanceof CEntry) {
			return (CEntry) o;
		} else {
			if (ser != null) return ser.serialize(o);
			throw new UnsupportedOperationException("简易序列化不支持的类型 " + o.getClass().getName());
		}
	}
}
