package roj.asm.tree.attr;

import roj.asm.Opcodes;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstModule;
import roj.asm.cp.CstPackage;
import roj.asm.cp.CstUTF;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class AttrModule extends Attribute {
	public AttrModule() {
		this.self = new ModuleInfo();
		this.requires = new ArrayList<>();
		this.exports = new ArrayList<>();
		this.opens = new ArrayList<>();
		this.uses = new ArrayList<>();
		this.providers = new ArrayList<>();
	}

	public AttrModule(DynByteBuf r, ConstantPool pool) {
		self = new ModuleInfo().read(r, pool);
		int count = r.readUnsignedShort();
		if (self.name.startsWith("java.base")) {
			if (count != 0) throw new IllegalArgumentException("'" + self.name + "' module should not have 'require' section");
		}
		List<ModuleInfo> requires = new ArrayList<>(count);
		while (count-- > 0) {
			requires.add(new ModuleInfo().read(r, pool));
		}
		this.requires = requires;

		count = r.readUnsignedShort();
		List<ExportInfo> export = new ArrayList<>(count);
		while (count-- > 0) {
			export.add(new ExportInfo().read(r, pool));
		}
		this.exports = export;

		count = r.readUnsignedShort();
		List<ExportInfo> open = new ArrayList<>(count);
		while (count-- > 0) {
			open.add(new ExportInfo().read(r, pool));
		}
		this.opens = open;

		count = r.readUnsignedShort();
		List<String> use = new ArrayList<>(count);
		while (count-- > 0) {
			use.add(pool.getRefName(r));
		}
		this.uses = use;

		count = r.readUnsignedShort();
		List<Provider> provide = new ArrayList<>(count);
		while (count-- > 0) {
			provide.add(new Provider().read(r, pool));
		}
		this.providers = provide;
	}

	public ModuleInfo self;

	public List<ModuleInfo> requires;
	public List<ExportInfo> exports, opens;
	// To tell the SPI that these classes should be loaded
	public List<String> uses;
	public List<Provider> providers;

	@Override
	public String name() { return "Module"; }

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
		self.write(w, pool);

		final List<ModuleInfo> requires = this.requires;
		w.putShort(requires.size());
		for (int i = 0; i < requires.size(); i++) {
			requires.get(i).write(w, pool);
		}

		final List<ExportInfo> exports = this.exports;
		w.putShort(exports.size());
		for (int i = 0; i < exports.size(); i++) {
			exports.get(i).write(w, pool);
		}

		final List<ExportInfo> opens = this.opens;
		w.putShort(opens.size());
		for (int i = 0; i < opens.size(); i++) {
			opens.get(i).write(w, pool);
		}

		final List<String> uses = this.uses;
		w.putShort(uses.size());
		for (int i = 0; i < uses.size(); i++) {
			w.putShort(pool.getClassId(uses.get(i)));
		}

		final List<Provider> providers = this.providers;
		w.putShort(providers.size());
		for (int i = 0; i < providers.size(); i++) {
			providers.get(i).write(w, pool);
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("Module: \n");
		sb.append(self).append("Requires: \n");

		final List<ModuleInfo> requires = this.requires;
		for (int i = 0; i < requires.size(); i++) {
			sb.append(requires.get(i)).append('\n');
		}

		sb.append("Exports: \n");
		final List<ExportInfo> exports = this.exports;
		for (int i = 0; i < exports.size(); i++) {
			sb.append(exports.get(i)).append('\n');
		}

		sb.append("Reflective opens: \n");
		final List<ExportInfo> opens = this.opens;
		for (int i = 0; i < opens.size(); i++) {
			sb.append(opens.get(i)).append('\n');
		}

		sb.append("SPI classes: \n");
		final List<String> uses = this.uses;
		for (int i = 0; i < uses.size(); i++) {
			sb.append(uses.get(i)).append('\n');
		}

		sb.append("SPI implements: \n");
		final List<Provider> providers = this.providers;
		for (int i = 0; i < providers.size(); i++) {
			sb.append(providers.get(i)).append('\n');
		}

		return sb.toString();
	}

	public static final class ModuleInfo {
		public String name;
		public int access;
		public String version;

		public ModuleInfo() {}

		public ModuleInfo read(DynByteBuf r, ConstantPool pool) {
			name = ((CstModule) pool.get(r)).name().str();
			access = r.readUnsignedShort();
			CstUTF utf = (CstUTF) pool.get(r);
			version = utf == null ? null : utf.str();
			return this;
		}

		public void write(DynByteBuf w, ConstantPool writer) {
			w.putShort(writer.getModuleId(name)).putShort(access).putShort(version == null ? 0 : writer.getUtfId(name));
		}

		@Override
		public String toString() {
			return "Module " + '\'' + name + '\'' + " ver" + version + ", acc='" + Opcodes.showModifiers(access, Opcodes.ACC_SHOW_MODULE) + '\'' + '}';
		}
	}

	public static final class ExportInfo {
		public String Package;
		public int access;
		public List<String> accessible;

		public ExportInfo() {}

		public ExportInfo read(DynByteBuf r, ConstantPool pool) {
			Package = ((CstPackage) pool.get(r)).name().str();
			access = r.readUnsignedShort();
			int len = r.readUnsignedShort();
			accessible = new SimpleList<>(len);
			while (len-- > 0) {
				accessible.add(((CstModule) pool.get(r)).name().str());
			}
			return this;
		}

		public void write(DynByteBuf w, ConstantPool writer) {
			w.putShort(writer.getPackageId(Package)).putShort(access).putShort(accessible.size());
			for (int i = 0, s = accessible.size(); i < s; i++) {
				w.putShort(writer.getModuleId(accessible.get(i)));
			}
		}

		@Override
		public String toString() {
			return "Export '" + Package + '\'' + ", acc=" + Opcodes.showModifiers(access, Opcodes.ACC_SHOW_MODULE) + ", accessibleClasses=" + accessible + '}';
		}
	}

	public static final class Provider {
		public String serviceName;
		public List<String> implement;

		public Provider() {}

		public Provider read(DynByteBuf r, ConstantPool pool) {
			serviceName = pool.getRefName(r);
			int len = r.readUnsignedShort();
			if (len == 0) throw new IllegalArgumentException("Provider.length should not be 0");
			implement = new SimpleList<>(len);
			while (len-- > 0) {
				implement.add(pool.getRefName(r));
			}
			return this;
		}

		public void write(DynByteBuf w, ConstantPool writer) {
			w.putShort(writer.getClassId(serviceName)).putShort(implement.size());
			for (int i = 0, s = implement.size(); i < s; i++) {
				w.putShort(writer.getClassId(implement.get(i)));
			}
		}

		@Override
		public String toString() {
			return "Server '" + serviceName + '\'' + ", implementors=" + implement + '}';
		}
	}
}