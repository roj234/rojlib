package roj.mod.mapping;

import LZMA.LzmaInputStream;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.collect.MyHashMap;
import roj.io.JarReaderStream;
import roj.reflect.ReflectionUtils;
import roj.ui.CmdUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * GDiff file patcher
 *
 * @author Roj234
 * @since 2021/5/30 19:59
 */
public final class GDiffPatcher {
	static final int DIFF_HEADER = 0xD1FFD1FF;
	static final int VERSION_4 = 4;

	public static void patch(ByteList src, byte[] patch, ByteList dst) {
		ByteList r = new ByteList(patch);
		if (r.readInt() == DIFF_HEADER && r.readUnsignedByte() == VERSION_4) {
			while (true) {
				int cmd = r.readUnsignedByte();
				if (cmd == 0) {
					return;
				}

				if (cmd <= 246) {
					append(cmd, r, dst);
				} else {
					int len, off;
					switch (cmd) {
						case 247:
							append(r.readUnsignedShort(), r, dst);
							break;
						case 248:
							append(r.readInt(), r, dst);
							break;
						case 249:
							off = r.readUnsignedShort();
							len = r.readUnsignedByte();
							copy(off, len, src, dst);
							break;
						case 250:
							off = r.readUnsignedShort();
							len = r.readUnsignedShort();
							copy(off, len, src, dst);
							break;
						case 251:
							off = r.readUnsignedShort();
							len = r.readInt();
							copy(off, len, src, dst);
							break;
						case 252:
							off = r.readInt();
							len = r.readUnsignedByte();
							copy(off, len, src, dst);
							break;
						case 253:
							off = r.readInt();
							len = r.readUnsignedShort();
							copy(off, len, src, dst);
							break;
						case 254:
							off = r.readInt();
							len = r.readInt();
							copy(off, len, src, dst);
							break;
						case 255:
							long loffset = r.readLong();
							if (loffset >= Integer.MAX_VALUE) throw new ArrayIndexOutOfBoundsException("Long param 0xFF is not supported");
							off = (int) loffset;
							len = r.readInt();
							copy(off, len, src, dst);
							break;
						default:
							throw new IllegalStateException("command " + cmd);
					}
				}
			}
		} else {
			throw new IllegalStateException("magic string not found, aborting!");
		}
	}

	private static void copy(int off, int len, ByteList src, ByteList dst) {
		dst.ensureCapacity(dst.wIndex() + len);
		System.arraycopy(src.list, off, dst.list, dst.wIndex(), len);
		dst.wIndex(dst.wIndex() + len);
	}

	private static void append(int len, ByteList src, ByteList dst) {
		dst.ensureCapacity(dst.wIndex() + len);
		System.arraycopy(src.list, src.rIndex, dst.list, dst.wIndex(), len);
		src.rIndex += len;
		dst.wIndex(dst.wIndex() + len);
	}

	private Map<String, List<Patch>> clientPatches, serverPatches;
	private final Adler32 adler321 = new Adler32(), adler322 = new Adler32();

	public int clientSuccessCount, serverSuccessCount, errorCount;

	public ByteList patchClient(String name, ByteList data) {
		ByteList patch = patch(name, data, clientPatches);
		if (patch != null) clientSuccessCount++;
		return patch;
	}

	public ByteList patchServer(String name, ByteList data) {
		ByteList patch = patch(name, data, serverPatches);
		if (patch != null) serverSuccessCount++;
		return patch;
	}

	public ByteList patch(String name, ByteList data, Map<String, List<Patch>> patchMap) {
		if (patchMap == null) return null;

		List<Patch> patches = patchMap.remove(name);
		if (patches == null) return null;

		for (Patch patch : patches) {
			if (!patch.exist) {
				if (data.isReadable()) {
					CmdUtil.warning("期待空的" + patch.source);
					errorCount++;
					data = new ByteList(0);
				}
			} else {
				if (!data.isReadable()) throw new RuntimeException("期待非空class " + patch.source);

				Adler32 adler32 = patchMap == serverPatches ? adler321 : adler322;
				adler32.update(data.list, data.arrayOffset(), data.readableBytes());
				int inputChecksum = (int) adler32.getValue();
				adler32.reset();
				if (patch.checksum != inputChecksum) {
					errorCount++;
					CmdUtil.warning("类 " + patch.source + " 的效验码不正确.");
					return null;
				}
			}
			if (patch.patch.length == 0) {
				CmdUtil.warning("E/不支持清空: " + patch);
				ConstantData clazz = new ConstantData();
				clazz.name(patch.source);
				return new ByteList(Parser.toByteArray(clazz));
			}

			try {
				ByteList out = new ByteList(data.readableBytes());
				patch(data, patch.patch, out);
				return out;
			} catch (Throwable e) {
				CmdUtil.error(name + "打补丁失败", e);
			}
		}
		return data;
	}

	public void setup113(InputStream serverStream, Map<String, String> unmapper) {
		try {
			try (LzmaInputStream decompressed = new LzmaInputStream(serverStream)) {
				serverPatches = new MyHashMap<>();
				ZipInputStream zis = new ZipInputStream(decompressed);
				ZipEntry ze;
				ByteList list = new ByteList(2048);
				while ((ze = zis.getNextEntry()) != null) {
					if (ze.getName().endsWith(".binpatch")) {
						list.clear();
						list.readStreamFully(zis);
						Patch cp = read113Patch(list);
						String cn = unmapper.getOrDefault(cp.source, cp.source);

						serverPatches.computeIfAbsent(cn + ".class", Helpers.fnArrayList()).add(cp);
					}
					zis.closeEntry();
				}
			}
		} catch (Throwable e) {
			throw new RuntimeException("补丁加载失败!", e);
		}
	}

	public void setup112(InputStream in) {
		if (ReflectionUtils.JAVA_VERSION >= 14) throw new IllegalArgumentException("Java14起删除了Pack200 再说了你写1.12的mod用啥J"+ReflectionUtils.JAVA_VERSION);
		try {
			try (LzmaInputStream decompressed = new LzmaInputStream(in)) {
				clientPatches = new MyHashMap<>();
				serverPatches = new MyHashMap<>();
				JarOutputStream jos = new JarReaderStream(((entry, bl) -> {
					try {
						Patch cp = readPatch(bl);
						if (entry.getName().startsWith("binpatch/client")) {
							clientPatches.computeIfAbsent(cp.source + ".class", Helpers.fnArrayList()).add(cp);
						} else if (entry.getName().startsWith("binpatch/server")) {
							serverPatches.computeIfAbsent(cp.source + ".class", Helpers.fnArrayList()).add(cp);
						} else {
							CmdUtil.warning("未知名字 " + entry.getName());
						}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}));
				Pack200.newUnpacker().unpack(decompressed, jos);
			}
		} catch (Throwable e) {
			throw new RuntimeException("补丁加载失败!", e);
		}
	}

	private static Patch readPatch(DataInput input) throws IOException {
		String name = input.readUTF(), src = input.readUTF(), target = input.readUTF();
		boolean exists = input.readBoolean();
		int inputChecksum = 0;
		if (exists) inputChecksum = input.readInt();
		int patchLength = input.readInt();
		byte[] patchBytes = new byte[patchLength];
		input.readFully(patchBytes);
		return new Patch(src, target, exists, inputChecksum, patchBytes);
	}

	private static Patch read113Patch(DataInput input) throws IOException {
		int version = input.readByte() & 0xFF;
		if (version != 1) throw new IOException("Unsupported patch format: " + version);
		String obf = input.readUTF();
		String srg = input.readUTF();
		boolean exists = input.readBoolean();
		int checksum = exists ? input.readInt() : 0;
		int length = input.readInt();
		byte[] data = new byte[length];
		input.readFully(data);

		return new Patch(obf, srg, exists, checksum, data);
	}

	public void reset() {
		if (clientPatches != null) clientPatches.clear();
		if (serverPatches != null) serverPatches.clear();
		clientPatches = serverPatches = null;
		clientSuccessCount = serverSuccessCount = errorCount = 0;
	}

	public static class Patch {
		public String source;
		//public final String target;
		public final boolean exist;
		public final byte[] patch;
		public final int checksum;

		public Patch(String source, String target, boolean exist, int checksum, byte[] patch) {
			this.source = source;
			//this.target = target;
			this.exist = exist;
			this.checksum = checksum;
			this.patch = patch;
		}

		public String toString() {
			return "Src: " + source + " Patch.length: " + patch.length + ", Target.length: " + (exist ? " > 0" : " = 0");
		}
	}
}
