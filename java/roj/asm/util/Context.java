package roj.asm.util;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.asm.ClassNode;
import roj.asm.Parser;
import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstRef;
import roj.concurrent.TaskHandler;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Context implements ClassLike, Consumer<Constant>, Supplier<ByteList> {
	private static final int ID_METHOD = 0, ID_FIELD = 1, ID_CLASS = 2;

	private String name;
	private Object in;
	private ClassNode data;
	private boolean isCompressed;
	private int initialCpOffset;

	public Context(ClassNode o) {
		data = o;
		getFileName();
	}
	public Context(String name, Object o) {
		this.name = name;
		if (o instanceof ClassNode) this.data = (ClassNode) o;
		else this.in = Objects.requireNonNull(o, name);
	}

	private static ByteList read0(Object o) throws IOException {
		if (o instanceof InputStream in) {
			try {
				return ByteList.wrap(IOUtil.read(in));
			} finally {
				IOUtil.closeSilently(in);
			}
		} else if (o instanceof ByteList n) return n;
		else if (o instanceof byte[] n) return ByteList.wrap(n);
		else if (o instanceof File) {
			try {
				return ByteList.wrap(IOUtil.read((File) o));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		throw new IllegalArgumentException("无法读取"+o);
	}

	public ClassNode getData() {
		isCompressed = false;
		if (data == null) {
			try {
				ByteList r = read0(in);
				in = null;

				if (cp == null) {
					data = Parser.parseConstants(r);
				} else {
					r.rIndex += 4;
					int version = r.readInt();
					r.skipBytes(initialCpOffset);
					data = Parser.parseConstantsNoCp(r, cp, version);
				}
				cp = data.cp;
			} catch (Throwable e) {
				throw new IllegalArgumentException(name+" 解析失败", e);
			}

			getFileName();
		}
		return data;
	}

	//region ConstantRef
	private ConstantPool cp;
	private ArrayList<Constant>[] cstCache;

	public ConstantPool getConstantPool() {
		if (cp == null) {
			try {
				ByteList r = read0(in);
				in = r;

				int i = r.rIndex;

				if (r.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");
				r.rIndex += 4;

				cp = new ConstantPool();
				cp.read(r, ConstantPool.BYTE_STRING);
				initialCpOffset = 2 + cp.byteLength();

				r.rIndex = i;
			} catch (Throwable e) {
				throw new IllegalArgumentException(name+" 解析失败", e);
			}

			getFileName();
		}
		return cp;
	}

	public List<CstRef> getMethodConstants() { cstInit(); return Helpers.cast(cstCache[ID_METHOD]); }
	public List<CstRef> getFieldConstants() { cstInit(); return Helpers.cast(cstCache[ID_FIELD]); }
	public List<CstClass> getClassConstants() { cstInit(); return Helpers.cast(cstCache[ID_CLASS]); }

	private void cstInit() {
		if (cstCache == null) {
			cstCache = Helpers.cast(new ArrayList<?>[] {
				new ArrayList<>(),
				new ArrayList<>(),
				new ArrayList<>()
			});
		}

		if (cstCache[0].isEmpty()) {
			var cp = getConstantPool();
			cp.setAddListener(this);
			List<Constant> csts = cp.data();
			for (int i = 0; i < csts.size(); i++) accept(csts.get(i));
		}
	}

	@Override
	public void accept(Constant c) {
		if (c == null) {
			for (List<?> list : cstCache) list.clear();
			cstInit();
			return;
		}

		switch (c.type()) {
			case Constant.INTERFACE: case Constant.METHOD: cstCache[ID_METHOD].add(c); break;
			case Constant.CLASS: cstCache[ID_CLASS].add(c); break;
			case Constant.FIELD: cstCache[ID_FIELD].add(c); break;
		}
	}
	// endregion

	/**
	 * This buffer maybe (ThreadLocal) shared, use toByteArray() to distinguish it
	 */
	public ByteList get() {
		try {
			if (data != null) {
				getFileName();
				return Parser.toByteArrayShared(data);
			} else {
				var buf = read0(in);
				in = buf;
				return buf;
			}
		} catch (Throwable e) {
			throw new IllegalArgumentException(name+" 序列化失败", e);
		}
	}

	public void set(String newName, ByteList bytes) {
		this.in = bytes;
		this.data = null;
		if (newName != null)
			this.name = newName;
	}

	public ByteList getCompressedShared() {
		compress();
		return get();
	}

	public void compress() {
		if (isCompressed) return;
		TransformUtil.compress(getData());
		isCompressed = true;
	}

	@Override
	public String toString() {return "Ctx " + "'" + name + '\'';}

	public String getFileName() {
		if (data == null) return name;
		return name = data.name().concat(".class");
	}
	public String getClassName() {
		if (data == null) return name.substring(0, name.length()-6);
		return name = data.name();
	}

	public static List<Context> fromZip(File input, ZipFileWriter rw) throws IOException {
		List<Context> ctx = new ArrayList<>();

		try (ZipFile archive = new ZipFile(input)) {
			for (ZEntry value : archive.entries()) {
				String name = value.getName();
				if (name.endsWith("/")) continue;

				if (name.endsWith(".class")) {
					ctx.add(new Context(name, archive.get(value)));
				} else if (rw != null) {
					rw.copy(archive, value);
				}
			}
		}
		return ctx;
	}

	public static void runAsync(Consumer<Context> action, List<List<Context>> ctxs, TaskHandler executor) {
		ArrayList<Future<?>> wait = new ArrayList<>(ctxs.size());
		for (int i = 0; i < ctxs.size(); i++) {
			List<Context> files = ctxs.get(i);
			var w = executor.submit(() -> {
				for (int j = 0; j < files.size(); j++) {
					try {
						action.accept(files.get(j));
					} catch (Throwable e) {
						throw new RuntimeException(files.get(j).getFileName(), e);
					}
				}
				return null;
			});
			wait.add(w);
		}

		for (int i = 0; i < wait.size(); i++) {
			try {
				wait.get(i).get();
			} catch (InterruptedException ignored) {
			} catch (ExecutionException e) {
				Helpers.athrow(e.getCause());
			}
		}
	}
}