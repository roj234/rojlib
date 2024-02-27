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
	public AttrModule(String name, int access) {
		this.self = new Module(name, access);
		this.requires = new ArrayList<>();
		this.exports = new ArrayList<>();
		this.opens = new ArrayList<>();
		this.uses = new ArrayList<>();
		this.provides = new ArrayList<>();
	}

	public AttrModule(DynByteBuf r, ConstantPool pool) {
		self = new Module(r, pool);

		int len = r.readUnsignedShort();
		if (self.name.startsWith("java.base")) {
			if (len != 0) throw new IllegalArgumentException("'"+self.name+"' module should not have 'require' section");
		}

		List<Module> requires = this.requires = new ArrayList<>(len);
		while (len-- > 0) requires.add(new Module(r, pool));

		len = r.readUnsignedShort();
		List<Export> export = this.exports = new ArrayList<>(len);
		while (len-- > 0) export.add(new Export(r, pool));

		len = r.readUnsignedShort();
		List<Export> open = this.opens = new ArrayList<>(len);
		while (len-- > 0) open.add(new Export(r, pool));

		len = r.readUnsignedShort();
		List<String> use = this.uses = new ArrayList<>(len);
		while (len-- > 0) use.add(pool.getRefName(r));

		len = r.readUnsignedShort();
		List<Provide> provide = this.provides = new ArrayList<>(len);
		while (len-- > 0) provide.add(new Provide(r, pool));
	}

	public AttrModule.Module self;

	public List<AttrModule.Module> requires;
	public List<Export> exports, opens;
	// To tell the SPI that these classes should be loaded
	public List<String> uses;
	public List<Provide> provides;

	@Override
	public String name() {return "Module";}

	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) {
		self.write(w, pool);

		var requires = this.requires;
		w.putShort(requires.size());
		for (int i = 0; i < requires.size(); i++)
			requires.get(i).write(w, pool);

		var exports = this.exports;
		w.putShort(exports.size());
		for (int i = 0; i < exports.size(); i++)
			exports.get(i).write(w, pool);

		var opens = this.opens;
		w.putShort(opens.size());
		for (int i = 0; i < opens.size(); i++)
			opens.get(i).write(w, pool);

		var uses = this.uses;
		w.putShort(uses.size());
		for (int i = 0; i < uses.size(); i++)
			w.putShort(pool.getClassId(uses.get(i)));

		var provides = this.provides;
		w.putShort(provides.size());
		for (int i = 0; i < provides.size(); i++)
			provides.get(i).write(w, pool);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("Module: \n");
		sb.append(self).append("Requires: \n");

		final List<AttrModule.Module> requires = this.requires;
		for (int i = 0; i < requires.size(); i++) {
			sb.append(requires.get(i)).append('\n');
		}

		sb.append("Exports: \n");
		final List<Export> exports = this.exports;
		for (int i = 0; i < exports.size(); i++) {
			sb.append(exports.get(i)).append('\n');
		}

		sb.append("Reflective opens: \n");
		final List<Export> opens = this.opens;
		for (int i = 0; i < opens.size(); i++) {
			sb.append(opens.get(i)).append('\n');
		}

		sb.append("SPI classes: \n");
		final List<String> uses = this.uses;
		for (int i = 0; i < uses.size(); i++) {
			sb.append(uses.get(i)).append('\n');
		}

		sb.append("SPI implements: \n");
		final List<Provide> provides = this.provides;
		for (int i = 0; i < provides.size(); i++) {
			sb.append(provides.get(i)).append('\n');
		}

		return sb.toString();
	}

	public static final class Module {
		public final String name;
		public int access;
		public String version;

		public Module(DynByteBuf r, ConstantPool pool) {
			name = ((CstModule) pool.get(r)).name().str();
			access = r.readUnsignedShort();
			CstUTF utf = (CstUTF) pool.get(r);
			version = utf == null ? null : utf.str();
		}
		public Module(String name, int access) {
			this.name = name;
			this.access = access;
		}

		public void write(DynByteBuf w, ConstantPool writer) {
			w.putShort(writer.getModuleId(name)).putShort(access).putShort(version == null ? 0 : writer.getUtfId(name));
		}

		@Override
		public String toString() {
			return "Module " + '\'' + name + '\'' + " ver" + version + ", acc='" + Opcodes.showModifiers(access, Opcodes.ACC_SHOW_MODULE) + '\'' + '}';
		}
	}

	public static final class Export {
		public final String pkg;
		public int access;
		public List<String> to;

		public Export(DynByteBuf r, ConstantPool pool) {
			pkg = ((CstPackage) pool.get(r)).name().str();
			access = r.readUnsignedShort();

			int len = r.readUnsignedShort();
			to = new SimpleList<>(len);
			while (len-- > 0) to.add(((CstModule) pool.get(r)).name().str());
		}
		public Export(String pkg) {
			this.pkg = pkg;
			this.to = new SimpleList<>();
		}

		public void write(DynByteBuf w, ConstantPool writer) {
			w.putShort(writer.getPackageId(pkg)).putShort(access).putShort(to.size());
			for (int i = 0, s = to.size(); i < s; i++) {
				w.putShort(writer.getModuleId(to.get(i)));
			}
		}

		@Override
		public String toString() {return "export "+Opcodes.showModifiers(access, Opcodes.ACC_SHOW_MODULE)+' '+pkg+" to "+to+';';}
	}

	public static final class Provide {
		public final String spi;
		public final List<String> impl;

		public Provide(DynByteBuf r, ConstantPool pool) {
			spi = pool.getRefName(r);

			int len = r.readUnsignedShort();
			if (len == 0) throw new IllegalArgumentException("Provide cannot be empty");

			impl = new SimpleList<>(len);
			while (len-- > 0) impl.add(pool.getRefName(r));
		}
		public Provide(String spi) {
			this.spi = spi;
			this.impl = new SimpleList<>();
		}

		public void write(DynByteBuf w, ConstantPool writer) {
			w.putShort(writer.getClassId(spi)).putShort(impl.size());
			for (int i = 0, s = impl.size(); i < s; i++) {
				w.putShort(writer.getClassId(impl.get(i)));
			}
		}

		@Override
		public String toString() {return "provide "+spi.replace('/', '.')+" with "+impl+';';}
	}
}