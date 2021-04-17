/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AttrBootstrapMethods.java
 */
package roj.asm.struct.attr;

import roj.asm.constant.CstPackage;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.List;

public final class AttrModulePackages extends Attribute {
    public AttrModulePackages() {
        super("ModulePackages");
        packages = new ArrayList<>();
    }

    public AttrModulePackages(ByteReader r, ConstantPool pool) {
        super("ModulePackages");
        packages = parse(r, pool);
    }

    public List<String> packages;

    /*
    u2 package_count;
    u2 package_index[package_count];
    */
    public static List<String> parse(ByteReader r, ConstantPool pool) {
        int packages = r.readUnsignedShort();
        List<String> pkg = new ArrayList<>(packages);
        for (int i = 0; i < packages; i++) {
            pkg.add(((CstPackage)pool.get(r)).getValue().getString());
        }
        return pkg;
    }

    @Override
    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        final List<String> packages = this.packages;
        w.writeShort(packages.size());
        for (int i = 0; i < packages.size(); i++) {
            w.writeShort(pool.getPackageId(packages.get(i)));
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("ModulePackages: \n");

        final List<String> packages = this.packages;
        for (int i = 0; i < packages.size(); i++) {
            sb.append(packages.get(i)).append('\n');
        }
        
        return sb.toString();
    }
}