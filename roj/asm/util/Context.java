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
	private boolean absolutelyCompressed;

	private ArrayList<Constant>[] cstCache;

	public Context(String name, Object o) {
		this.name = name;
		if (o instanceof ConstantData) this.data = (ConstantData) o;
		else this.in = o;
	}

	public ConstantData getData() {
		absolutelyCompressed = false;
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
				File file = new File(getFileName().replace('/', '.').concat(".class"));
				try (FileOutputStream fos = new FileOutputStream(file)) {
					bytes.writeToStream(fos);
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
				throw new IllegalArgumentException(name + " 解析失败", e);
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

	public List<CstRef> getMethodConstants() { cstInit(); return Helpers.cast(cstCache[ID_METHOD]); }
	public List<CstRef> getFieldConstants() { cstInit(); return Helpers.cast(cstCache[ID_FIELD]); }
	public List<CstDynamic> getInvokeDynamic() { cstInit(); return Helpers.cast(cstCache[ID_INVOKE_DYN]); }
	public List<CstClass> getClassConstants() { cstInit(); return Helpers.cast(cstCache[ID_CLASS]); }

	public ByteList get() { return get(true); }
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


	public boolean inRaw() { return data == null; }

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
		if (cstCache == null) {
			cstCache = Helpers.cast(new ArrayList<?>[] {
				new ArrayList<>(),
				new ArrayList<>(),
				new ArrayList<>(),
				new ArrayList<>()
			});
		}

		if (cstCache[0].isEmpty()) {
			boolean prev = absolutelyCompressed;
			ConstantPool cw = getData().cp;
			absolutelyCompressed = prev;

			cw.setAddListener(this);
			List<Constant> csts = cw.array();
			for (int i = 0; i < csts.size(); i++) accept(csts.get(i));
			getFileName();
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
			case Constant.INVOKE_DYNAMIC: cstCache[ID_INVOKE_DYN].add(c); break;
		}
	}

	public String getFileName() {
		if (data == null) return name;
		return name = data.name.concat(".class");
	}

	public ByteList getCompressedShared() {
		String fn = getFileName();
		if (!absolutelyCompressed) {
			Parser.compress(getData());
			absolutelyCompressed = true;
		}
		return get(true);
	}

	public void compress() {
		if (absolutelyCompressed) return;

		boolean targetIsByte = data == null;
		Parser.compress(getData());
		if (targetIsByte) set(ByteList.wrap(Parser.toByteArray(data)));
		absolutelyCompressed = true;
	}

	// region 准备上下文

	public static List<Context> fromZip(File input, Charset charset) throws IOException { return fromZip(input, charset, Helpers.alwaysTrue()); }
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