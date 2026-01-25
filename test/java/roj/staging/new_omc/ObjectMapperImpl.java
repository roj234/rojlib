package roj.staging.new_omc;

import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.asm.type.Type;
import roj.asmx.GenericTemplate;
import roj.collect.BitSet;
import roj.config.JsonParser;
import roj.io.IOUtil;
import roj.reflect.Reflection;
import roj.reflect.Unsafe;
import roj.util.ByteList;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * @author Roj234
 * @since 2026/02/07 05:47
 */
public class ObjectMapperImpl {

	static class TestClass {
		boolean primitive;
		int[] array;
		Map<String, ?> map;

		@Override
		public String toString() {
			return "TestClass{" +
						   "primitive=" + primitive +
						   ", array=" + Arrays.toString(array) +
						   ", map=" + map +
						   '}';
		}
	}

	static TypeAdapter __root;
	public static void main(String[] args) throws Exception {
		var context = new ObjectMapperImpl();

		var intArrayAdapter = ObjectMapperImpl.array(int[].class);
		var mapAdapter = new MapAdapterImpl(context);

		var root = new TypeAdapter.ObjectAdapter() {
			@Override
			public Object newContainerBuilder(int size) {
				try {
					return Unsafe.U.allocateInstance(TestClass.class);
				} catch (InstantiationException e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public FieldSet listFields() {
				var fields = new FieldSet();
				fields.add(new State.Field("primitive", 1));
				fields.add(new State.Field("array", intArrayAdapter));
				fields.add(new State.Field("map", mapAdapter));
				return fields;
			}

			@Override
			public long maskOptionalFields(long fieldPresent1, BitSet fieldPresent2) {
				return fieldPresent1 | 7;
			}

			@Override
			@SuppressWarnings("unchecked")
			public void setField(Object container, int fieldId, ValueContainer value) {
				var x = (TestClass) container;
				switch (fieldId) {
					case 0 -> x.primitive = value.ZValue;
					case 1 -> x.array = (int[]) value.LValue;
					case 2 -> x.map = (Map<String, ?>) value.LValue;
				}
			}
		};
		__root = root;

		var exampleJson = """
				{
				\tprimitive: true,
				\tarray: [1, 2, 3],
				\tmap: {
				\t\ta: 1,
				\t\tb: {
				\t\t\t"==": "[I",
				\t\t\tvalue: [1, 2, 3]
				\t\t},
				\t\tc: "asd",
				\t\td: {
				\t\t\t"==": "obj",
				\t\t\tprimitive: true,
				skip_me: {
				   a: [3,4,5,{a:"b"}],
				   b: 1
				}
				\t\t}
				\t}
				}""";
		ObjectReaderImpl emitter = new ObjectReaderImpl(root);
		new JsonParser().parse(exampleJson, emitter);
		System.out.println(emitter.value.LValue);
	}

	TypeAdapter getAdapter(String type) {
		return switch (type) {
			case "[I" -> array(int[].class);
			case "obj" -> __root;
			case "java/lang/Object" -> new AnyObjectAdapter(this);
			default -> null;
		};
	}

	private static byte[] primitiveArrayTemplate;
	/**
	 * 数组序列化器
	 */
	static TypeAdapter array(Class<?> type) {
		type = type.getComponentType();
		assert type.isPrimitive();

		if (primitiveArrayTemplate == null) {
			try {
				primitiveArrayTemplate = IOUtil.getResourceIL("roj/staging/new_omc/PrimitiveArrayAdapter.class");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		byte[] b = primitiveArrayTemplate.clone();

		Type asmType = Type.getType(type);
		Type methodType = asmType.opcodePrefix().equals("I") ? Type.INT_TYPE : asmType;
		ClassNode c = new GenericTemplate(Type.DOUBLE,asmType,methodType,asmType,true) {
			@Override
			public void insn(byte code) {
				if (code == Opcodes.ICONST_3) {
					super.ldc(switch (Type.getSort(asmType.type)) {
						default -> throw new IllegalStateException();
						case 1, 2 -> 0;
						case 3, 4 -> 1;
						case 5, 7 -> 2;
						case 6, 8 -> 3;
					});
				} else if (code == Opcodes.ICONST_5) {
					super.ldc(Type.getSort(asmType.type));
				} else {
					super.insn(code);
				}
			}

			@Override
			public void field(byte code, String owner, String name, String type) {
				if (name.equals("DValue")) name = (char)asmType.type+"Value";
				super.field(code, owner, name, type);
			}
		}.generate(ByteList.wrap(b));

		c.name("roj/config/mapper/PAS$"+asmType);
		return (TypeAdapter) Reflection.createInstance(TypeAdapter.class, c);
	}

}
