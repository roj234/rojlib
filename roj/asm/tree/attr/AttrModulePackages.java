package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstPackage;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class AttrModulePackages extends Attribute {
	public AttrModulePackages() {
		super("ModulePackages");
		packages = new ArrayList<>();
	}

	public AttrModulePackages(DynByteBuf r, ConstantPool pool) {
		super("ModulePackages");
		packages = parse(r, pool);
	}

	public List<String> packages;

	@Override
	public boolean isEmpty() {
		return packages.isEmpty();
	}

	public static List<String> parse(DynByteBuf r, ConstantPool pool) {
		int count = r.readUnsignedShort();
		List<String> pkg = new SimpleList<>(count);
		while (count-- > 0) {
			pkg.add(((CstPackage) pool.get(r)).name().str());
		}
		return pkg;
	}

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
		List<String> packages = this.packages;
		w.putShort(packages.size());
		for (int i = 0; i < packages.size(); i++) {
			w.putShort(pool.getPackageId(packages.get(i)));
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("ModulePackages: \n");

		List<String> packages = this.packages;
		for (int i = 0; i < packages.size(); i++) {
			sb.append(packages.get(i)).append('\n');
		}

		return sb.toString();
	}
}