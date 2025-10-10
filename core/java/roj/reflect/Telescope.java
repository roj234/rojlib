package roj.reflect;

import roj.util.Helpers;
import roj.util.OperationDone;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static roj.reflect.Reflection.IMPL_LOOKUP;

/**
 * @author Roj234
 * @since 2025/09/07 02:50
 */
public sealed class Telescope {
	private static volatile Telescope instance;

	final MethodHandles.Lookup lookup;
	private Telescope(MethodHandles.Lookup lookup) {this.lookup = lookup;}

	// This is pretty safe, 就像不显式抛出异常的MethodHandles.Lookup
	public static Telescope lookup() {return trustedLookup().in(Reflection.getCallerClass(2));}
	// Unsafe on demand, 我使用ASM转换器来限制这个类的能力.
	public static Telescope trustedLookup() {
		if (instance == null) {
			Telescope h;
			try {
				h = new NewResolver(IMPL_LOOKUP);
			} catch (Throwable e) {
				h = new Telescope(IMPL_LOOKUP);
			}
			instance = h;
		}

		return instance;
	}

	public final Telescope in(Class<?> callerClass) {return in(lookup.in(callerClass));}
	public Telescope in(MethodHandles.Lookup lookup) {return new Telescope(lookup);}

	public static Class<?> findClass(String className) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			Helpers.athrow(e);
			throw OperationDone.NEVER;
		}
	}

	public VarHandle findVarHandle(Class<?> recv, String name, Class<?> type) {
		try {
			return lookup.findVarHandle(recv, name, type);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			Helpers.athrow(e);
			throw OperationDone.NEVER;
		}
	}

	public VarHandle findStaticVarHandle(Class<?> recv, String name, Class<?> type) {
		try {
			return lookup.findStaticVarHandle(recv, name, type);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			Helpers.athrow(e);
			throw OperationDone.NEVER;
		}
	}

	public MethodHandle findStatic(Class<?> recv, String name, MethodType methodType) {
		try {
			return lookup.findStatic(recv, name, methodType);
		} catch (NoSuchMethodException | IllegalAccessException e) {
			Helpers.athrow(e);
			throw OperationDone.NEVER;
		}
	}

	public MethodHandle findVirtual(Class<?> recv, String name, MethodType methodType) {
		try {
			return lookup.findVirtual(recv, name, methodType);
		} catch (NoSuchMethodException | IllegalAccessException e) {
			Helpers.athrow(e);
			throw OperationDone.NEVER;
		}
	}

	public MethodHandle findConstructor(Class<?> recv, MethodType methodType) {
		try {
			return lookup.findConstructor(recv, methodType);
		} catch (NoSuchMethodException | IllegalAccessException e) {
			Helpers.athrow(e);
			throw OperationDone.NEVER;
		}
	}

	public void ensureClassInitialized(Class<?> klass) {
		try {
			lookup.ensureInitialized(klass);
		} catch (IllegalAccessException e) {
			Helpers.athrow(e);
			throw OperationDone.NEVER;
		}
	}

	public Object findStatic(Class<?> recv, String name, Class<?> type) {return findField(recv, name, type, true);}
	public Object findField(Class<?> recv, String name, Class<?> type, boolean isStatic) {
		var nowRecv = recv;

		do {
			try {
				Field field = nowRecv.getDeclaredField(name);
				if (Modifier.isStatic(field.getModifiers()) == isStatic) {
					if (!field.getType().isAssignableFrom(type))
						throw new NoSuchFieldException("Field "+name+" ("+field.getType()+") is not "+type);

					var sm = ILSecurityManager.getSecurityManager();
					if (sm != null) sm.checkField(nowRecv, name, field.getType(), Reflection.getCallerClass(3, Unsafe.class));

					return field;
				}
			} catch (NoSuchFieldException ignored) {}

			try {
				nowRecv = lookup.accessClass(nowRecv.getSuperclass());
			} catch (IllegalAccessException e) {
				break;
			}
		} while (nowRecv != null && nowRecv != Object.class);

		Helpers.athrow(new NoSuchFieldException("Could not resolve "+recv.getName()+"."+name+" "+type.getName()));
		return null;
	}

	public long objectFieldOffset(Object handle) {return Unsafe.U.objectFieldOffset((Field) handle);}
	public long staticFieldOffset(Object handle) {return Unsafe.U.staticFieldOffset((Field) handle);}
	public Object staticFieldBase(Object handle) {return Unsafe.U.staticFieldBase((Field) handle);}

	static final class NewResolver extends Telescope {
		static final MethodHandle resolveOrFail, objectFieldOffset, staticFieldOffset, staticFieldBase;

		static {
			try {
				final Class<?> MemberName = findClass("java.lang.invoke.MemberName");
				final Class<?> MethodHandleNatives = findClass("java.lang.invoke.MethodHandleNatives");
				resolveOrFail = IMPL_LOOKUP.findVirtual(MethodHandles.Lookup.class, "resolveOrFail", MethodType.methodType(MemberName, byte.class, Class.class, String.class, Class.class));
				objectFieldOffset = IMPL_LOOKUP.findStatic(MethodHandleNatives, "objectFieldOffset", MethodType.methodType(long.class, MemberName));
				staticFieldOffset = IMPL_LOOKUP.findStatic(MethodHandleNatives, "staticFieldOffset", MethodType.methodType(long.class, MemberName));
				staticFieldBase = IMPL_LOOKUP.findStatic(MethodHandleNatives, "staticFieldBase", MethodType.methodType(Object.class, MemberName));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		NewResolver(MethodHandles.Lookup lookup) {super(lookup);}

		@Override
		public Telescope in(MethodHandles.Lookup lookup) {return new NewResolver(lookup);}

		//REF_getField                = 1,
		//REF_getStatic               = 2,
		@Override
		public Object findField(Class<?> recv, String name, Class<?> type, boolean isStatic) {
			Throwable firstException = null;
			do {
				try {
					return resolveOrFail.invoke(lookup, (byte) (isStatic ? 2 : 1), recv, name, type);
				} catch (Throwable e) {
					if (firstException == null)
						firstException = e;
				}
				recv = recv.getSuperclass();
			} while (recv != null && recv != Object.class);

			Helpers.athrow(firstException);
			return null;
		}

		@Override
		public long objectFieldOffset(Object handle) {
			try {
				return (long) objectFieldOffset.invoke(handle);
			} catch (Throwable e) {
				throw OperationDone.NEVER;
			}
		}

		@Override
		public long staticFieldOffset(Object handle) {
			try {
				return (long) staticFieldOffset.invoke(handle);
			} catch (Throwable e) {
				throw OperationDone.NEVER;
			}
		}

		@Override
		public Object staticFieldBase(Object handle) {
			try {
				return staticFieldBase.invoke(handle);
			} catch (Throwable e) {
				throw OperationDone.NEVER;
			}
		}
	}
}
