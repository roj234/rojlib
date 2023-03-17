package ilib.asm.FeOrg;

import org.objectweb.asm.Type;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValEnum;

import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TypeHelper {
	public final Type type;
	public final Map<String, Object> val;

	public TypeHelper(Type type, Map<String, Object> val) {
		this.type = type;
		this.val = val;
	}

	public static Map<String, Object> toPrimitive(Map<String, AnnVal> param) {
		Map<String, Object> map = new HashMap<>(param.size());
		for (Map.Entry<String, AnnVal> entry : param.entrySet()) {
			map.put(entry.getKey(), toPrimitive(entry.getValue()));
		}
		return map;
	}

	public static Object toPrimitive(AnnVal param) {
		switch (param.type()) {
			default: // should not go there
			case AnnVal.FLOAT: return param.asFloat();
			case AnnVal.DOUBLE: return param.asDouble();
			case AnnVal.LONG: return param.asLong();
			case AnnVal.INT: return param.asInt();
			case AnnVal.STRING: return param.asString();
			case AnnVal.SHORT: return (short) param.asInt();
			case AnnVal.CHAR: return (char) param.asInt();
			case AnnVal.BYTE: return (byte) param.asInt();
			case AnnVal.BOOLEAN: return param.asInt() == 1;
			case AnnVal.ENUM:
				AnnValEnum ave = param.asEnum();
				return new ModAnnotation.EnumHolder(ave.clazz, ave.value);
			case AnnVal.ARRAY:
				List<AnnVal> value = param.asArray();
				List<Object> list = new ArrayList<>(value.size());
				for (int i = 0; i < value.size(); i++) {
					list.add(toPrimitive(value.get(i)));
				}
				return list;
			case AnnVal.CLASS: return Type.getType(roj.asm.type.TypeHelper.getField(param.asClass()));
			case AnnVal.ANNOTATION: return toPrimitive(param.asAnnotation().values);
		}
	}

	public static Type asmType(String rawName) {
		return Type.getType("L" + rawName + ";");
	}
}
