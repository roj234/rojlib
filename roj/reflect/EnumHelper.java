package roj.reflect;

import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.util.ArrayCache;
import roj.util.Helpers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

/**
 * 动态修改Enum <BR>
 * 推荐preload
 *
 * @author Roj234
 * @since 2021/5/2 8:22
 */
public final class EnumHelper<E extends Enum<E>> {
	public static final CDirAcc cDirAcc;
	private static final FieldAccessor ordinalAcc;

	public interface CDirAcc {
		Map<String, Enum<?>> enumConstantDirectory(Class<? extends Enum<?>> clazz);
		Object[] getEnumConstantsShared(Class<? extends Enum<?>> clazz);

		void setEnumData(Class<? extends Enum<?>> clazz, Object _void);
		void setEnumData2(Class<? extends Enum<?>> clazz, Object _void);

		Object newInstance0(Constructor<?> c, Object... params) throws InstantiationException, IllegalArgumentException, InvocationTargetException;
	}

	static {
		DirectAccessor<CDirAcc> b = DirectAccessor.builder(CDirAcc.class).unchecked().delegate(Class.class, "enumConstantDirectory", "enumConstantDirectory");

		if (!ReflectionUtils.OPENJ9) {
			b.access(Class.class, "enumConstantDirectory", null, "setEnumData")
			 .access(Class.class, "enumConstants", "getEnumConstantsShared", "setEnumData2");
		} else  {
			b.i_access("java/lang/Class", "enumVars", new Type("java/lang/Class$EnumVars"), null, "setEnumData2", false);
		}

		try {
			String cn = ReflectionUtils.JAVA_VERSION <= 8 ? "sun.reflect.NativeConstructorAccessorImpl" : "jdk.internal.reflect.NativeConstructorAccessorImpl";
			b.delegate(Class.forName(cn), "newInstance0");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		cDirAcc = b.build();

		FieldAccessor t;
		try {
			t = ReflectionUtils.access(Enum.class.getDeclaredField("ordinal"));
		} catch (NoSuchFieldException e) {
			Helpers.athrow(e);
			t = null;
		}
		ordinalAcc = t;
	}

	private final Class<E> clazz;
	private final Field[] fields;
	private FieldAccessor values;
	private final Collection<Field> switchFields;
	private final SimpleList<UndoInfo<E>> undoStack = new SimpleList<>();

	private Class<?>[] lastAdditionalTypes;
	private Constructor<?> lastConstructor;

	public String valueName = "$VALUES";

	/**
	 * Switch uses ordinal to decide enum;
	 */
	public EnumHelper(Class<E> clazz, Class<?>... switchUsers) {
		this.clazz = clazz;
		this.fields = clazz.getDeclaredFields();
		if (!clazz.isEnum()) throw new IllegalArgumentException("Not an enum");
		try {
			this.switchFields = findSwitchMaps(switchUsers);
		} catch (Exception e) {
			throw new IllegalArgumentException("Could not create the class", e);
		}
	}

	public E make(String value, int ordinal) {
		return make(value, ordinal, ArrayCache.CLASSES, ArrayCache.OBJECTS);
	}

	public E make(String value, int ordinal, Class<?>[] additionalTypes, Object[] additional) {
		try {
			undoStack.add(new UndoInfo<>(this));

			Constructor<?> cst;
			if (Arrays.equals(additionalTypes, lastAdditionalTypes)) {
				cst = lastConstructor;
			} else {
				lastConstructor = cst = findConstructor(additionalTypes, clazz);
				lastAdditionalTypes = additionalTypes;
			}

			cst.setAccessible(true);
			return construct(clazz, cst, value, ordinal, additional);
		} catch (ReflectiveOperationException e) {
			throw new IllegalArgumentException("Could not create enum", e);
		}
	}

	public EnumHelper<E> setValueName(String valueName) {
		this.valueName = valueName;
		return this;
	}

	private void _setClassInternals(E from, E to) {
		if (to == null) {
			cDirAcc.setEnumData2(clazz, null);
			if (!ReflectionUtils.OPENJ9)
				cDirAcc.setEnumData(clazz, null);
		} else {
			Map<String, E> map = Helpers.cast(cDirAcc.enumConstantDirectory(clazz));
			map.put(to.name(), to);
			if (from == null) {
				cDirAcc.setEnumData2(clazz, null);
			} else {
				E[] arr = Helpers.cast(cDirAcc.getEnumConstantsShared(clazz));
				if (from.ordinal() != to.ordinal()) throw new AssertionError("from.ordinal() != to.ordinal()");
				arr[from.ordinal()] = to;
			}
		}
	}

	/**
	 * Add enum instance, overwrite if exists.
	 * <p/>
	 * Overwrite:
	 * Replace constant field and array.
	 * <p/>
	 * The ordinal will be set.
	 * <p/>
	 * Warning: This should probably never be called,
	 * since it can cause permanent changes to the enum
	 * values.  Use only in extreme conditions.
	 */
	public void add(E e) {
		if (e == null) throw new NullPointerException();

		undoStack.add(new UndoInfo<>(this));

		FieldAccessor vf = findValuesField(valueName);

		E[] values = values();
		for (int i = 0; i < values.length; i++) {
			E value = values[i];

			if (value.name().equals(e.name())) {
				ordinalAcc.setInt(e, value.ordinal());
				_setClassInternals(values[i], e);
				values[i] = e;
				replace(e.name(), e);
				return;
			}
		}

		E[] newValues = Arrays.copyOf(values, values.length + 1);
		newValues[newValues.length - 1] = e;
		vf.setObject(null, newValues);

		int ordinal = newValues.length - 1;
		ordinalAcc.setInt(e, ordinal);
		_setClassInternals(null, e);

		addSwitch();
	}

	private FieldAccessor findValuesField(String valueId) {
		if (values == null) {
			for (Field field : fields) {
				if ((valueId == null ? field.getType().getComponentType() == clazz : field.getName().equals(valueId)) && (field.getModifiers() & AccessFlag.STATIC) != 0) {
					return values = ReflectionUtils.access(field);
				}
			}
		}
		return values;
	}

	/**
	 * !Set constant to null.
	 */
	public boolean delete(E e) {
		if (e == null) throw new NullPointerException();

		undoStack.add(new UndoInfo<>(this));

		E[] values = values();
		for (int i = 0; i < values.length; i++) {
			E value = values[i];

			if (value.name().equals(e.name())) {
				E[] newValues = Arrays.copyOf(values, values.length - 1);
				System.arraycopy(values, i + 1, newValues, i, values.length - i - 1);

				for (int j = i; j < newValues.length; j++) {
					ordinalAcc.setInt(newValues[j], j);
				}

				findValuesField(valueName).setObject(null, newValues);
				removeSwitch(i);
				_setClassInternals(e, null);
				replace(e.name(), null);

				return true;
			}
		}

		return false;
	}

	public void restore() {
		if (undoStack.isEmpty()) return;
		UndoInfo<E> info = undoStack.get(0);
		if (info != null) {
			info.undo();
			undoStack.clear();
		}
	}

	public boolean undo() {
		if (undoStack.isEmpty()) return false;
		UndoInfo<E> info = undoStack.remove(undoStack.size() - 1);

		info.undo();
		return true;
	}

	private Constructor<?> findConstructor(Class<?>[] add, Class<E> clazz) throws NoSuchMethodException {
		Class<?>[] paramType = new Class<?>[add.length + 2];
		paramType[0] = String.class;
		paramType[1] = int.class;
		if (add.length > 0) System.arraycopy(add, 0, paramType, 2, add.length);

		return clazz.getDeclaredConstructor(paramType);
	}

	private E construct(Class<E> clazz, Constructor<?> cst, String value, int ordinal, Object[] add) throws ReflectiveOperationException {
		Object[] param = new Object[add.length + 2];
		param[0] = value;
		param[1] = ordinal;
		if (add.length > 0) System.arraycopy(add, 0, param, 2, add.length);

		E cast = clazz.cast(cDirAcc.newInstance0(cst, param));

		Map<String, E> map = Helpers.cast(cDirAcc.enumConstantDirectory(clazz));
		map.put(value, cast);

		return cast;
	}

	private void replace(String name, Object val) {
		for (Field field : fields) {
			if (field.getName().equals(name)) {
				ReflectionUtils.setFinal(field, val);
			}
		}
	}

	private Collection<Field> findSwitchMaps(Class<?>[] switchUsers) {
		Collection<Field> result = new LinkedList<>();

		try {
			for (Class<?> switchUser : switchUsers) {
				String name = switchUser.getName();
				int i = 0;

				while (true) {
					try {
						Class<?> suspect = Class.forName(String.format("%s$%d", name, ++i));
						Field[] fields = suspect.getDeclaredFields();

						for (Field field : fields) {
							String fieldName = field.getName();

							if (fieldName.startsWith("$SwitchMap$") && fieldName.endsWith(clazz.getSimpleName())) {
								field.setAccessible(true);
								result.add(field);
							}
						}
					} catch (ClassNotFoundException e) {
						break;
					}
				}
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Could not get switch map", e);
		}

		return result;
	}

	private void addSwitch() {
		try {
			for (Field field : switchFields) {
				int[] switches = (int[]) field.get(null);
				switches = Arrays.copyOf(switches, switches.length + 1);
				ReflectionUtils.setFinal(field, switches);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private void removeSwitch(int ordinal) {
		try {
			for (Field switchField : switchFields) {
				int[] old = (int[]) switchField.get(null);
				int[] now = Arrays.copyOf(old, old.length - 1);
				System.arraycopy(old, ordinal + 1, now, ordinal, old.length - ordinal - 1);
				ReflectionUtils.setFinal(switchField, now);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public E[] values() {
		return (E[]) findValuesField(valueName).getObject(null);
	}

	private static final class UndoInfo<E extends Enum<E>> {
		private final E[] values;
		private final MyHashMap<Field, int[]> switchValues;
		private final EnumHelper<E> helper;

		private UndoInfo(EnumHelper<E> helper) {
			try {
				this.helper = helper;
				E[] v = helper.values();
				this.values = v == null ? null : v.clone();
				this.switchValues = new MyHashMap<>(helper.switchFields.size());

				for (Field switchField : helper.switchFields) {
					int[] arr = (int[]) switchField.get(null);
					switchValues.put(switchField, arr.clone());
				}
			} catch (ReflectiveOperationException e) {
				throw new IllegalArgumentException("Could not record undo", e);
			}
		}

		private void undo() {
			helper.findValuesField(helper.valueName).setObject(null, values);

			for (int i = 0; i < values.length; i++) {
				ordinalAcc.setInt(values[i], i);
			}

			// reset all the constants defined inside the enum
			Map<String, E> valueOf = new MyHashMap<>(values.length);

			for (E e : values) {
				valueOf.put(e.name(), e);
			}

			for (Field field : helper.fields) {
				E e = valueOf.get(field.getName());

				if (e != null) {
					ReflectionUtils.setFinal(field, e);
				}
			}

			for (Map.Entry<Field, int[]> entry : switchValues.entrySet()) {
				Field field = entry.getKey();
				int[] mappings = entry.getValue();
				ReflectionUtils.setFinal(field, mappings);
			}
		}
	}
}
