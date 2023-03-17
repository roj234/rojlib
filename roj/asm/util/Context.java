package roj.asm.util;

import roj.asm.Parser;
import roj.asm.cst.*;
import roj.asm.tree.ConstantData;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Context implements Consumer<Constant>, Supplier<ByteList> {
	static final int ID_METHOD = 0, ID_FIELD = 1, ID_CLASS = 2, ID_INVOKE_DYN = 3;

	private String name;
	private ConstantData data;
	private Object in;
	private ByteList buf;
	private boolean clean;

	private final ArrayList<Constant>[] cstCache = Helpers.cast(new ArrayList<?>[4]);

	public Context(String name, Object o) {
		this.name = name;
		if (o instanceof ConstantData) this.data = (ConstantData) o;
		else this.in = o;
	}

	public ConstantData getData() {
		clean = false;
		if (this.data == null) {
			ByteList bytes;
			if (in != null) {
				bytes = read0(in);
				in = null;
			} else if (buf != null) {
				bytes = this.buf;
				this.buf = null;
			} else throw new IllegalStateException(getFileName() + " 没有数据");
			ConstantData data;
			try {
				data = Parser.parseConstants(bytes);
			} catch (Throwable e) {
				final File file = new File(getFileName().replace('/', '.'));
				try (FileOutputStream fos = new FileOutputStream(file)) {
					bytes.writeToStream(fos);
				} catch (IOException ignored) {}
				throw new IllegalArgumentException(name + " 读取失败", e);
			}
			this.data = data;
			getFileName();
		}
		return this.data;
	}

	private static ByteList read0(Object o) {
		if (o instanceof InputStream) {
			try (InputStream in = (InputStream) o) {
				return ByteList.wrap(IOUtil.getSharedByteBuf().readStreamFully(in).toByteArray());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else if (o instanceof ByteList) {
			return (ByteList) o;
		} else if (o instanceof byte[]) {
			return ByteList.wrap((byte[]) o);
		} else if (o instanceof File) {
			try {
				return ByteList.wrap(IOUtil.read((File) o));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		throw new ClassCastException(o.getClass().getName());
	}

	public List<CstRef> getMethodConstants() {
		cstInit();
		return Helpers.cast(cstCache[ID_METHOD]);
	}

	public List<CstRef> getFieldConstants() {
		cstInit();
		return Helpers.cast(cstCache[ID_FIELD]);
	}

	public List<CstDynamic> getInvokeDynamic() {
		cstInit();
		return Helpers.cast(cstCache[ID_INVOKE_DYN]);
	}

	public List<CstClass> getClassConstants() {
		cstInit();
		return Helpers.cast(cstCache[ID_CLASS]);
	}

	public ByteList get(boolean shared) {
		if (this.buf == null) {
			if (this.data != null) {
				getFileName();
				try {
					data.verify();
					if (shared) {
						return Parser.toByteArrayShared(data);
					} else {
						this.buf = new ByteList(Parser.toByteArray(data));
						clearData();
					}
				} catch (Throwable e) {
					throw new IllegalArgumentException(name + " 写入失败", e);
				}
			} else {
				this.buf = read0(in);
				this.in = null;
			}
		}
		return this.buf;
	}

	public ByteList get() {
		return get(true);
	}

	public boolean inRaw() {
		return data == null;
	}

	private void clearData() {
		if (this.data != null) {
			getFileName();
			this.data = null;
			if (cstCache[0] != null) {
				for (List<?> list : cstCache) {
					list.clear();
				}
			}
		}
	}

	public void set(ByteList bytes) {
		this.buf = bytes;
		clearData();
	}

	@Override
	public String toString() {
		return "Ctx " + "'" + name + '\'';
	}

	private void cstInit() {
		if (cstCache[0] == null) {
			cstCache[0] = new ArrayList<>();
			cstCache[1] = new ArrayList<>();
			cstCache[2] = new ArrayList<>();
			cstCache[3] = new ArrayList<>();
		}
		if (cstCache[0].isEmpty()) {
			boolean prev = clean;
			ConstantPool cw = getData().cp;
			clean = prev;

			cw.setAddListener(this);
			List<Constant> csts = cw.array();
			for (int i = 0; i < csts.size(); i++) {
				accept(csts.get(i));
			}
			getFileName();
		}
	}

	@Override
	public void accept(Constant cst) {
		if (cst == null) {
			for (List<?> list : cstCache) {
				list.clear();
			}
			cstInit();
			return;
		}
		switch (cst.type()) {
			case Constant.INTERFACE:
			case Constant.METHOD:
				cstCache[ID_METHOD].add(cst);
				break;
			case Constant.CLASS:
				cstCache[ID_CLASS].add(cst);
				break;
			case Constant.FIELD:
				cstCache[ID_FIELD].add(cst);
				break;
			case Constant.INVOKE_DYNAMIC:
				cstCache[ID_INVOKE_DYN].add(cst);
				break;
		}
	}

	public String getFileName() {
		if (data == null) return name;
		String n = data.name;

		ch:
		if (name.length() == n.length()-6) {
			for (int i = n.length() - 7; i >= 0; i--) {
				if (name.charAt(i) != n.charAt(i)) break ch;
			}
			return name;
		}

		return this.name = n.concat(".class");
	}

	public ByteList getCompressedShared() {
		String fn = getFileName();
		try {
			if (!clean) {
				if (data == null) {
					data = Parser.parse(get());
				} else {
					Parser.withParsedAttribute(data);
				}

				clean = true;
			}
			return get(true);
		} catch (Throwable t) {
			try (FileOutputStream fos = new FileOutputStream(fn.replace('/', '_'))) {
				get().writeToStream(fos);
			} catch (Throwable ignored) {}
			throw t;
		}
	}

	public void compress() {
		if (data == null) {
			data = Parser.parse(get(true));
			set(ByteList.wrap(Parser.toByteArray(data)));
		} else {
			Parser.withParsedAttribute(data);
			get(false);
		}
		clean = true;
	}

	// region 准备上下文

	public static List<Context> fromStream(Map<String, InputStream> streams) throws IOException {
		List<Context> ctx = new ArrayList<>(streams.size());

		ByteList bl = new ByteList();
		for (Map.Entry<String, InputStream> entry : streams.entrySet()) {
			Context c = new Context(entry.getKey().replace('\\', '/'), bl.readStreamFully(entry.getValue()).toByteArray());
			entry.getValue().close();
			c.getData();
			bl.clear();
			ctx.add(c);
		}

		return ctx;
	}

	public static List<Context> fromZip(File input, Charset charset) throws IOException {
		return fromZip(input, charset, Helpers.alwaysTrue());
	}

	public static List<Context> fromZip(File input, Charset charset, Predicate<String> filter) throws IOException {
		ZipFile inputJar = new ZipFile(input, charset);

		List<Context> ctx = new ArrayList<>();

		ByteList bl = new ByteList();
		Enumeration<? extends ZipEntry> en = inputJar.entries();
		while (en.hasMoreElements()) {
			ZipEntry zn;
			try {
				zn = en.nextElement();
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("可能是编码错误! 请指定编码", e);
			}
			if (zn.isDirectory()) continue;
			if (zn.getName().endsWith(".class") && filter.test(zn.getName())) {
				InputStream in = inputJar.getInputStream(zn);
				Context c = new Context(zn.getName().replace('\\', '/'), bl.readStreamFully(in).toByteArray());
				in.close();
				bl.clear();
				ctx.add(c);
			}
		}

		inputJar.close();

		return ctx;
	}

	public static List<Context> fromZip(File input, Charset charset, Map<String, byte[]> res) throws IOException {
		ZipFile inputJar = new ZipFile(input, charset);

		List<Context> ctx = new ArrayList<>();

		ByteList bl = new ByteList();
		Enumeration<? extends ZipEntry> en = inputJar.entries();
		while (en.hasMoreElements()) {
			ZipEntry zn;
			try {
				zn = en.nextElement();
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("可能是编码错误! 请指定编码", e);
			}
			if (zn.isDirectory()) continue;
			InputStream in = inputJar.getInputStream(zn);
			bl.readStreamFully(in);
			in.close();
			if (zn.getName().endsWith(".class")) {
				Context c = new Context(zn.getName().replace('\\', '/'), bl.toByteArray());
				ctx.add(c);
			} else {
				res.put(zn.getName(), bl.toByteArray());
			}
			bl.clear();
		}

		inputJar.close();

		return ctx;
	}

	// endregion
}