package roj.asm.attr;

import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.collect.HashMap;
import roj.util.DynByteBuf;

import java.util.Map;

/**
 * @author Roj234
 * @since 2025/5/4 9:06
 */
public class ModuleHashes extends Attribute {
	public ModuleHashes(){map = new HashMap<>();}
	public ModuleHashes(DynByteBuf r, ConstantPool cp) {
		algorithm = ((CstUTF) cp.get(r)).str();

		int len = r.readUnsignedShort();
		map = new HashMap<>(len);
		for (int i = 0; i < len; i++) {
			String moduleName = cp.getRefName(r, Constant.MODULE);

			int hashLen = r.readUnsignedShort();
			if (hashLen == 0) throw new IllegalArgumentException("hashLen == 0");

			byte[] hash = r.readBytes(hashLen);
			map.put(moduleName, hash);
		}
	}

	public String algorithm;
	public Map<String, byte[]> map;

	@Override public final String name() {return "ModuleHashes";}
	@Override public String toString() {return "ModuleHashes["+algorithm+"]: "+map;}
	@Override public boolean writeIgnore() {return map.isEmpty();}
	@Override public void toByteArrayNoHeader(DynByteBuf w, ConstantPool cp) {
		w.putShort(cp.getUtfId(algorithm)).putShort(map.size());
		for (var entry : map.entrySet()) {
			byte[] hash = entry.getValue();
			w.putShort(cp.getModuleId(entry.getKey())).putShort(hash.length).put(hash);
		}
	}
}
