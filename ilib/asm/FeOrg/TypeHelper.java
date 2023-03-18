package ilib.asm.FeOrg;

import org.objectweb.asm.Type;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValEnum;

import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static roj.asm.type.Type.*;

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
			case FLOAT: return param.asFloat();
			case DOUBLE: return param.asDouble();
			case LONG: return param.asLong();
			case INT: return param.asInt();
			case AnnVal.STRING: return param.asString();
			case SHORT: return (short) param.asInt();
			case CHAR: return (char) param.asInt();
			case BYTE: return (byte) param.asInt();
			case BOOLEAN: return param.asInt() == 1;
			case AnnVal.ENUM:
				AnnValEnum ave = param.asEnum();
				return new ModAnnotation.EnumHolder(ave.clazz, ave.value);
			case ARRAY:
				List<AnnVal> value = param.asArray();
				List<Object> list = new ArrayList<>(value.size());
				for (int i = 0; i < value.size(); i++) {
					list.add(toPrimitive(value.get(i)));
				}
				return list;
			case AnnVal.ANNOTATION_CLASS: return Type.getType(roj.asm.type.TypeHelper.getField(param.asClass()));
			case AnnVal.ANNOTATION: return toPrimitive(param.asAnnotation().values);
		}
	}

	public static Type asmType(String rawName) {
		return Type.getType("L" + rawName + ";");
	}
}
