package roj.reflect;

import java.lang.StackWalker.StackFrame;
import java.lang.invoke.MethodType;
import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2024/6/4 0004 6:09
 */
public final class GetCallerArgs {
	@Java22Workaround
	private interface H {
		Object newInstance(Object a, Object b);
		int size(Object o);
		int intValue(Object o);
		long longValue(Object o);
	}

	private static final Object localsAndOperands;
	private static final long oMonitors, oLocals, oStacks;
	private static final H builder;
	private static final Class<?> primitive;
	public static final GetCallerArgs INSTANCE;
	static {
		try {
			Class<?> options = Class.forName("java.lang.StackWalker$ExtendedOption");
			localsAndOperands = U.getObject(options, ReflectionUtils.fieldOffset(options, "LOCALS_AND_OPERANDS"));

			Class<?> impl = Class.forName("java.lang.LiveStackFrameInfo");
			oMonitors = ReflectionUtils.fieldOffset(impl, "monitors");
			oLocals = ReflectionUtils.fieldOffset(impl, "locals");
			oStacks = ReflectionUtils.fieldOffset(impl, "operands");

			primitive = Class.forName("java.lang.LiveStackFrame$PrimitiveSlot");

			builder = Bypass.builder(H.class).inline().delegate_o(StackWalker.class, "newInstance").delegate_o(primitive, new String[] {"size", "intValue", "longValue"}).build();

			INSTANCE = new GetCallerArgs(EnumSet.allOf(StackWalker.Option.class));
		} catch (Exception e) {
			throw new IllegalStateException("非常抱歉，由于使用了大量内部API，这个类无法兼容你的JVM", e);
		}
	}

	private final StackWalker walker;
	public GetCallerArgs(EnumSet<StackWalker.Option> t) {walker = (StackWalker) builder.newInstance(t, localsAndOperands);}

	public <T> T walk(Function<? super Stream<XSF>, ? extends T> function) {return walker.walk(stream -> function.apply(stream.skip(1).map(XSF::new)));}
	public void forEach(Consumer<? super XSF> action) {walker.walk(stream -> {stream.skip(1).map(XSF::new).forEach(action); return null;});}

	public Object getCallerInstance() {return getCallerInstance(3);}
	public Object getCallerInstance(int skip) {
		return walker.walk(stream -> {
			StackFrame frame = stream.skip(skip).findFirst().orElse(null);
			return frame == null ? null : new XSF(frame).getLocals()[0];
		});
	}

	public static class XSF implements StackFrame {
		private final StackFrame f;

		public XSF(StackFrame f) {this.f = f;}

		@Override
		public String getClassName() {return f.getClassName();}
		@Override
		public String getMethodName() {return f.getMethodName();}
		@Override
		public Class<?> getDeclaringClass() {return f.getDeclaringClass();}
		@Override
		public MethodType getMethodType() {return f.getMethodType();}
		@Override
		public String getDescriptor() {return f.getDescriptor();}
		@Override
		public int getByteCodeIndex() {return f.getByteCodeIndex();}
		@Override
		public String getFileName() {return f.getFileName();}
		@Override
		public int getLineNumber() {return f.getLineNumber();}
		@Override
		public boolean isNativeMethod() {return f.isNativeMethod();}
		@Override
		public StackTraceElement toStackTraceElement() {return f.toStackTraceElement();}
		@Override
		public String toString() {return f.toString();}

		public Object[] getMonitors() {return (Object[]) U.getObject(f, oMonitors);}
		public Object[] getLocals() {return (Object[]) U.getObject(f, oLocals);}
		public Object[] getStacks() {return (Object[]) U.getObject(f, oStacks);}

		public static boolean isPrimitive(Object o) {return o != null && primitive.isAssignableFrom(o.getClass());}
		public static int getSize(Object o) {return builder.size(o);}
		public static int intValue(Object o) {return builder.intValue(o);}
		public static long longValue(Object o) {return builder.longValue(o);}
	}
}