package roj.asm.attr;

import roj.asm.Opcodes;
import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.asm.type.TypeHelper;
import roj.collect.ArrayList;
import roj.text.CharList;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class ModuleAttribute extends Attribute {
	public ModuleAttribute(String name, int access) {
		this.self = new Module(name, access);
		this.requires = new java.util.ArrayList<>();
		this.exports = new java.util.ArrayList<>();
		this.opens = new java.util.ArrayList<>();
		this.uses = new java.util.ArrayList<>();
		this.provides = new java.util.ArrayList<>();
	}

	public ModuleAttribute(DynByteBuf r, ConstantPool pool) {
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
		while (len-- > 0) use.add(pool.getRefName(r, Constant.CLASS));

		len = r.readUnsignedShort();
		List<Provide> provide = this.provides = new ArrayList<>(len);
		while (len-- > 0) provide.add(new Provide(r, pool));
	}

	public ModuleAttribute.Module self;

	public List<ModuleAttribute.Module> requires;
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
		CharList sb = new CharList().append("module ").append(self).append(" {");
		writeModuleInfo(sb);
		return sb.append("\n}").toString();
	}
	public void writeModuleInfo(CharList sb) {
		var requires = this.requires;
		if (!requires.isEmpty()) {
			sb.append('\n');
			for (int i = 0; i < requires.size(); i++) {
				sb.append("\n  requires ").append(requires.get(i)).append(';');
			}
		}

		var exports = this.exports;
		if (!exports.isEmpty()) {
			sb.append('\n');
			for (int i = 0; i < exports.size(); i++) {
				writeExportOpen(sb.append("\n  exports "), exports.get(i));
			}
		}

		var opens = this.opens;
		if (!opens.isEmpty()) {
			sb.append('\n');
			for (int i = 0; i < opens.size(); i++) {
				writeExportOpen(sb.append("\n  opens "), opens.get(i));
			}
		}

		var uses = this.uses;
		if (!uses.isEmpty()) {
			sb.append('\n');
			for (int i = 0; i < uses.size(); i++) {
				sb.append("\n  uses ").append(uses.get(i)).append(';');
			}
		}

		var provides = this.provides;
		if (!provides.isEmpty()) {
			sb.append('\n');
			for (int i = 0; i < provides.size(); i++) {
				var export = provides.get(i);
				sb.append("\n  provides ").append(export.spi);
				var _list = export.impl;
				if (!_list.isEmpty()) {
					sb.append(" with\n    ");
					for (int j = 0; j < _list.size();) {
						sb.append(_list.get(j));
						if (++j == _list.size()) break;
						sb.append(",\n    ");
					}
				}
				sb.append(';');
			}
		}
	}
	private static void writeExportOpen(CharList sb, Export export) {
		sb.append(export.pkg);
		var _list = export.to;
		if (!_list.isEmpty()) {
			sb.append(" to\n    ");
			for (int j = 0; j < _list.size();) {
				String ss = _list.get(j);
				TypeHelper.toStringOptionalPackage(sb, ss);
				if (++j == _list.size()) break;
				sb.append(",\n    ");
			}
		}
		sb.append(';');
	}

	public static final class Module {
		public final String name;
		public int access;
		public String version;

		public Module(DynByteBuf r, ConstantPool pool) {
			name = pool.getRefName(r, Constant.MODULE);
			access = r.readUnsignedShort();
			var utf = (CstUTF) pool.getNullable(r);
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
		public String toString() {return Opcodes.showModifiers(access, Opcodes.ACC_SHOW_MODULE)+name+(version==null?"":" v"+version);}
	}

	public static final class Export {
		public final String pkg;
		public int access;
		public List<String> to;

		public Export(DynByteBuf r, ConstantPool pool) {
			pkg = pool.getRefName(r, Constant.PACKAGE);
			access = r.readUnsignedShort();

			int len = r.readUnsignedShort();
			to = new ArrayList<>(len);
			while (len-- > 0) to.add(pool.getRefName(r, Constant.MODULE));
		}
		public Export(String pkg) {
			this.pkg = pkg;
			this.to = new ArrayList<>();
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
			spi = pool.getRefName(r, Constant.CLASS);

			int len = r.readUnsignedShort();
			if (len == 0) throw new IllegalArgumentException("Provide cannot be empty");

			impl = new ArrayList<>(len);
			while (len-- > 0) impl.add(pool.getRefName(r, Constant.CLASS));
		}
		public Provide(String spi) {
			this.spi = spi;
			this.impl = new ArrayList<>();
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