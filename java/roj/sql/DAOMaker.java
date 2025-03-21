package roj.sql;

import roj.asm.ClassNode;
import roj.asm.Parser;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Attribute;
import roj.asm.cp.CstClass;
import roj.asm.insn.CodeWriter;
import roj.asm.insn.Label;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asmx.mapper.ParamNameMapper;
import roj.collect.MyBitSet;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.config.auto.SerializerFactory;
import roj.config.serial.CVisitor;
import roj.reflect.ClassDefiner;
import roj.reflect.VirtualReference;
import roj.text.CharList;
import roj.util.Helpers;

import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/9/2 0002 3:03
 */
public final class DAOMaker {
	public interface DAO<T> {
		DAO<T> init(Connection connector);
		@SuppressWarnings("unchecked") default T unwrap() {return (T)this;}
	}

	private static final MyHashSet<String> iterable = new MyHashSet<>("java/lang/Iterable", "java/util/Collection", "java/util/List", "java/util/Set");
	private static final VirtualReference<Map<Class<?>, Object>> ref = new VirtualReference<>();
	@SuppressWarnings("unchecked")
	public static <T> DAO<T> makeDAO(Class<T> dao) {
		var map = ref.computeIfAbsent(dao.getClassLoader(), Helpers.fnMyHashMap());

		var impl = map.get(dao);
		if (impl == null) {
			synchronized (map) {
				if ((impl = map.get(dao)) == null) {
					try {
						impl = make(dao);
					} catch (ReflectiveOperationException e) {
						Helpers.athrow(e);
					}
					map.put(dao, impl);
				}
			}
		}

		return (DAO<T>) impl;
	}

	private static final MyBitSet VAR_ID_END = MyBitSet.from(")`'\" \t\r\n");
	private static DAO<?> make(Class<?> daoItf) throws ReflectiveOperationException {
		var ref = Parser.parseConstants(daoItf);
		if (ref == null) throw new IllegalArgumentException("找不到"+daoItf+"的代码");

		var impl = new ClassNode();
		impl.name(ref.name()+"$Impl");
		impl.addInterface("roj/sql/DAOMaker$DAO");
		impl.addInterface(daoItf.getName().replace('.', '/'));
		ClassDefiner.premake(impl);

		var init = impl.newMethod(ACC_PUBLIC, "init", "(Ljava/sql/Connection;)Lroj/sql/DAOMaker$DAO;");
		init.visitSize(3, 2);
		init.newObject(impl.name());
		init.one(ASTORE_0);

		var variables = new SimpleList<String>();
		var parIds = new ToIntMap<String>();

		for (var method : ref.methods) {
			Annotation query = Annotation.findInvisible(ref.cp, method, "roj/sql/Query");
			if (query == null) continue;

			variables.clear();
			var sql = new CharList(query.getString("value"));
			int i = 0;
			while (true) {
				int j = sql.indexOf(":", i);
				if (j < 0) break;

				int end = sql.indexOf(VAR_ID_END, j+1);
				if (end < 0) end = sql.length();

				variables.add(sql.substring(j+1, end));

				sql.replace(j, end, "?");
				i = j+1;
			}
			int psid = impl.newField(ACC_PRIVATE, "ps$"+impl.fields.size(), "Ljava/sql/PreparedStatement;");

			init.one(ALOAD_0);
			init.one(ALOAD_1);
			init.ldc(sql.toString());
			init.invokeItf("java/sql/Connection", "prepareStatement", "(Ljava/lang/String;)Ljava/sql/PreparedStatement;");
			init.field(PUTFIELD, impl, psid);

			var cw = impl.newMethod(ACC_PUBLIC| ACC_FINAL, method.name(), method.rawDesc());
			var parNames = ParamNameMapper.getParameterName(ref.cp, method);
			var parTypes = method.parameters();
			parIds.clear();
			i = 1;
			for (int j = 0; j < parNames.size(); j++) {
				parIds.putInt(parNames.get(j), (i << 16) | j);
				i += parTypes.get(j).length();
			}
			cw.visitSize(3, i);

			cw.one(ALOAD_0);
			cw.field(GETFIELD, impl, psid);
			cw.one(ASTORE_0);

			boolean isSelectStatement = sql.startsWith("select ") || sql.startsWith("SELECT ");
			boolean isBatch;
			var extraType = method.getAttribute(ref.cp, Attribute.SIGNATURE);

			if (isBatch = (parTypes.size() == 1 && iterable.contains(parTypes.get(0).owner))) {
				if (isSelectStatement) throw new IllegalArgumentException(method+"的参数不能使用select语句");
				if (extraType == null) throw new IllegalArgumentException(method+"缺少泛型签名,无法确定List<T>的类型");

				IType itrType = ((Generic) extraType.values.get(0)).children.get(0);
				Class<?> parTypeInst = itrType.rawType().toJavaType(daoItf.getClassLoader());

				if (parTypes.get(0).owner.equals("java/util/List")) {
					cw.visitSizeMax(3, 5);
					cw.one(ALOAD_1);
					cw.ldc(0);
					cw.one(ISTORE_3);
					cw.invokeItf("java/util/List", "size", "()I");
					cw.vars(ISTORE, 4);

					var loopCheck = new Label();
					cw.jump(loopCheck);
					var loopStart = cw.label();

					cw.one(ALOAD_1);
					cw.one(ILOAD_3);
					cw.invokeItf("java/util/List", "get", "(I)Ljava/lang/Object;");
					cw.clazz(CHECKCAST, itrType.owner());
					cw.one(ASTORE_2);

					// Real code
					for (int j = 0; j < variables.size(); j++) {
						var name = variables.get(j);
						if (!name.startsWith(parNames.get(0))) throw new IllegalArgumentException(method+"参数"+name+"不合法");
						name = name.substring(parNames.get(0).length()+1);

						cw.one(ALOAD_0);
						cw.ldc(j+1);
						cw.one(ALOAD_2);

						Type asmType = Type.fromJavaType(parTypeInst.getDeclaredField(name).getType());
						cw.field(GETFIELD, itrType.owner(), name, asmType);
						invokeSet(cw, asmType);
					}
					// Real code

					cw.one(ALOAD_0);
					cw.invokeItf("java/sql/PreparedStatement", "addBatch", "()V");

					cw.label(loopCheck);
					cw.one(ILOAD_3);
					cw.vars(ILOAD, 4);
					cw.jump(IF_icmplt, loopStart);
				} else {
					cw.one(ALOAD_1);
					cw.invokeItf("java/lang/Iterable", "iterator", "()Ljava/util/Iterator;");
					cw.one(ASTORE_1);

					var loopCheck = new Label();
					cw.jump(loopCheck);
					var loopStart = cw.label();

					cw.one(ALOAD_1);
					cw.invokeItf("java/util/Iterator", "next", "()Ljava/lang/Object;");
					cw.clazz(CHECKCAST, itrType.owner());
					cw.one(ASTORE_2);

					// Real code
					for (int j = 0; j < variables.size(); j++) {
						var name = variables.get(j);
						if (!name.startsWith(parNames.get(0))) throw new IllegalArgumentException(method+"参数"+name+"不合法");
						name = name.substring(parNames.get(0).length()+1);

						cw.one(ALOAD_0);
						cw.ldc(j+1);
						cw.one(ALOAD_2);

						Type asmType = Type.fromJavaType(parTypeInst.getDeclaredField(name).getType());
						cw.field(GETFIELD, itrType.owner(), name, asmType);
						invokeSet(cw, asmType);
					}
					// Real code

					cw.one(ALOAD_0);
					cw.invokeItf("java/sql/PreparedStatement", "addBatch", "()V");

					cw.label(loopCheck);
					cw.one(ALOAD_1);
					cw.invokeItf("java/util/Iterator", "hasNext", "()Z");
					cw.jump(IFNE, loopStart);
				}
			} else {
				for (int j = 0; j < variables.size(); j++) {
					var name = variables.get(j);
					int idx = name.indexOf('.');
					String nameFirst, nameLast;
					if (idx < 0) {
						nameFirst = name;
						nameLast = null;
					} else {
						nameFirst = name.substring(0, idx);
						nameLast = name.substring(idx+1);
					}

					cw.one(ALOAD_0);
					cw.ldc(j+1);

					int parId = parIds.getInt(nameFirst);
					if (parId == 0) throw new IllegalStateException("无法找到参数:"+nameFirst+" (傻逼javac), 已知"+parNames);

					Type parType = parTypes.get(parId&0xFFFF);
					cw.varLoad(parType, parId>>>16);
					if (nameLast != null) {
						Class<?> parTypeInst = parType.toJavaType(daoItf.getClassLoader());
						Type asmType = Type.fromJavaType(parTypeInst.getDeclaredField(nameLast).getType());
						cw.field(GETFIELD, parType.owner, nameLast, parType = asmType);
					}
					invokeSet(cw, parType);
				}
			}

			Type returnType = method.returnType();
			if (isSelectStatement) {
				if (returnType.owner == null) throw new IllegalArgumentException(method+"需要返回对象");

				cw.one(ALOAD_0);
				if (returnType.owner.equals("java/util/List") || returnType.owner.equals("java/util/Collection")) {
					if (extraType == null) throw new IllegalArgumentException(method+"缺少泛型签名,无法确定List<T>的类型");

					IType itrType = ((Generic) extraType.values.get(extraType.values.size()-1)).children.get(0);
					cw.ldc(new CstClass(itrType.owner()));
					cw.invokeS("roj/sql/DAOMaker", "_doSelectMany", "(Ljava/sql/PreparedStatement;Ljava/lang/Class;)Ljava/util/List;");
				} else {
					cw.ldc(new CstClass(returnType.owner));
					cw.invokeS("roj/sql/DAOMaker", "_doSelectOne", "(Ljava/sql/PreparedStatement;Ljava/lang/Class;)Ljava/lang/Object;");
					cw.clazz(CHECKCAST, returnType);
				}

				doClear(cw, false);
				cw.one(ARETURN);
			} else {
				cw.one(ALOAD_0);
				int type = returnType.getActualType();
				if (type == Type.VOID) {
					cw.invokeItf("java/sql/PreparedStatement", "executeUpdate", "()I");
					cw.one(POP);
					doClear(cw, isBatch);
					cw.one(RETURN);
				} else if (type == Type.INT) {
					cw.invokeItf("java/sql/PreparedStatement", "executeUpdate", "()I");
					doClear(cw, isBatch);
					cw.one(IRETURN);
				} else if (type == Type.LONG) {
					cw.invokeItf("java/sql/PreparedStatement", "executeLargeUpdate", "()J");
					doClear(cw, isBatch);
					cw.one(LRETURN);
				} else {
					throw new IllegalArgumentException(method+"不能返回对象");
				}
			}
		}

		init.one(ALOAD_0);
		init.one(ARETURN);
		return (DAO) ClassDefiner.make(impl, daoItf.getClassLoader());
	}

	private static void doClear(CodeWriter cw, boolean clearBatch) {
		cw.one(ALOAD_0);
		cw.invokeItf("java/sql/PreparedStatement", "clearParameters", "()V");
		if (clearBatch) {
			cw.one(ALOAD_0);
			cw.invokeItf("java/sql/PreparedStatement", "clearBatch", "()V");
		}
	}

	private static void invokeSet(CodeWriter cw, Type type) {
		var methodName = switch (type.getActualType()) {
			case Type.BOOLEAN -> "setBoolean";
			case Type.BYTE -> "setByte";
			case Type.SHORT -> "setShort";
			default -> "setInt";
			case Type.LONG -> "setLong";
			case Type.FLOAT -> "setFloat";
			case Type.DOUBLE -> "setDouble";
			case Type.CLASS -> {
				if (type.array() != 0) {
					if (type.array() != 1 || type.type != Type.BYTE) throw new IllegalArgumentException("无法处理"+type);
					yield "setBytes";
				} else {
					yield switch (type.owner) {
						case "java/lang/String" -> "setString";
						case "java/math/BigDecimal" -> "setBigDecimal";
						case "java/sql/Time" -> "setTime";
						case "java/sql/Date" -> "setDate";
						case "java/sql/Timestamp" -> "setTimestamp";
						default -> {
							type = Type.klass("java/lang/Object");
							yield "setObject";
						}
					};
				}
			}
		};

		cw.invokeItf("java/sql/PreparedStatement", methodName, "(I"+type.toDesc()+")V");
	}

	private static final WeakHashMap<PreparedStatement, List<Object>> adapterCache = new WeakHashMap<>();

	public static <T> List<T> _doSelectMany(PreparedStatement stm, Class<T> type) throws Exception {
		var adapters = adapterCache.get(stm);
		if (adapters == null) {
			synchronized (adapterCache) {
				adapterCache.putIfAbsent(stm, adapters = createAdapter(type, stm.getMetaData()));
			}
		}

		ResultSet set = stm.executeQuery();

		var ser = getSerializerFactory(type).listOf(type);
		ser.valueList();

		while (set.next()) {
			ser.valueMap();
			for (int i = 0; i < adapters.size(); ) {
				ser.key(adapters.get(i++).toString());
				((A)adapters.get(i++)).adapt(ser, set);
			}
			ser.pop();
		}

		ser.pop();
		return ser.get();
	}
	public static <T> T _doSelectOne(PreparedStatement stm, Class<T> type) throws Exception {
		var adapters = adapterCache.get(stm);
		if (adapters == null) {
			synchronized (adapterCache) {
				adapterCache.putIfAbsent(stm, adapters = createAdapter(type, stm.getMetaData()));
			}
		}

		var set = stm.executeQuery();
		if (set.next()) {
			var ser = getSerializerFactory(type).serializer(type);
			ser.valueMap();
			for (int i = 0; i < adapters.size(); ) {
				ser.key(adapters.get(i++).toString());
				((A)adapters.get(i++)).adapt(ser, set);
			}
			ser.pop();
			return ser.get();
		}

		return null;
	}
	private static SerializerFactory getSerializerFactory(Class<?> type) {
        if (type.getClassLoader() == null || type.getClassLoader() == DAOMaker.class.getClassLoader()) return SerializerFactory.SAFE;

		var map = ref.getEntry(type.getClassLoader()).getValue();
        assert map != null;

		Object v = map.get(null);
		if (v == null) {
			synchronized (map) {
				v = SerializerFactory.getInstance0(SerializerFactory.GENERATE | SerializerFactory.CHECK_INTERFACE | SerializerFactory.CHECK_PARENT, type.getClassLoader());
				var tmp = map.putIfAbsent(null, v);
				if (tmp != null) v = tmp;
			}
		}
		return (SerializerFactory) v;
    }

	private interface A {void adapt(CVisitor visitor, ResultSet set) throws SQLException;}
	private static List<Object> createAdapter(Class<?> klass, ResultSetMetaData meta) throws Exception {
		var adapters = new SimpleList<>();
		for (int i = 1; i <= meta.getColumnCount(); i++) {
			String name = meta.getColumnLabel(i);

			var type = Type.fromJavaType(klass.getDeclaredField(name).getType());

			var adapter = switch (type.getActualType()) {
				case Type.BOOLEAN -> boolAdapter(i);
				case Type.BYTE -> byteAdapter(i);
				case Type.SHORT -> shortAdapter(i);
				default -> intAdapter(i);
				case Type.LONG -> longAdapter(i);
				case Type.FLOAT -> floatAdapter(i);
				case Type.DOUBLE -> doubleAdapter(i);
				case Type.CLASS -> {
					if (type.array() != 0) {
						if (type.array() != 1 || type.type != Type.BYTE) throw new IllegalArgumentException("无法处理"+type);
						yield byteArrayAdapter(i);
					} else {
						yield switch (type.owner) {
							case "java/lang/String" -> stringAdapter(i);
							case "java/math/BigDecimal" -> bigDecimalAdapter(i);
							case "java/sql/Time" -> timeAdapter(i);
							case "java/sql/Date" -> dateAdapter(i);
							case "java/sql/Timestamp" -> timestampAdapter(i);
							default -> stringAdapter(i);
						};
					}
				}
			};
			adapters.add(name);
			adapters.add(adapter);
		}
		return adapters;
	}
	private static A boolAdapter(int column) {return (visitor, set) -> visitor.value(set.getBoolean(column));}
	private static A byteAdapter(int column) {return (visitor, set) -> visitor.value(set.getByte(column));}
	private static A shortAdapter(int column) {return (visitor, set) -> visitor.value(set.getShort(column));}
	private static A intAdapter(int column) {return (visitor, set) -> visitor.value(set.getInt(column));}
	private static A longAdapter(int column) {return (visitor, set) -> visitor.value(set.getLong(column));}
	private static A floatAdapter(int column) {return (visitor, set) -> visitor.value(set.getFloat(column));}
	private static A doubleAdapter(int column) {return (visitor, set) -> visitor.value(set.getDouble(column));}
	private static A stringAdapter(int column) {return (visitor, set) -> visitor.value(set.getString(column));}
	private static A byteArrayAdapter(int column) {return (visitor, set) -> visitor.value(set.getBytes(column));}
	private static A dateAdapter(int column) {return (visitor, set) -> visitor.valueDate(set.getDate(column).getTime());}
	private static A timeAdapter(int column) {return (visitor, set) -> visitor.valueTimestamp(set.getTime(column).getTime());}
	private static A timestampAdapter(int column) {return (visitor, set) -> visitor.valueTimestamp(set.getTimestamp(column).getTime());}
	private static A bigDecimalAdapter(int column) {return (visitor, set) -> visitor.value(set.getBigDecimal(column).toString());}
}
