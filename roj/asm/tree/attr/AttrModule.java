/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package roj.asm.tree.attr;

import roj.asm.cst.CstModule;
import roj.asm.cst.CstPackage;
import roj.asm.cst.CstUTF;
import roj.asm.util.AccessFlag;
import roj.asm.util.ConstantPool;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public final class AttrModule extends Attribute {
    public AttrModule() {
        super("Module");
    }

    public AttrModule(ByteReader r, ConstantPool pool) {
        super("Module");
        parse(r, pool);
    }

    public ModuleInfo self;

    public List<ModuleInfo> requires;
    public List<ExportInfo> exports, opens;
    // To tell the SPI that these classes should be loaded
    public List<String> uses;
    public List<Provider> providers;

    /* u2 module_name_index;
    u2 module_flags;
    u2 module_version_index;

    u2 requires_count;
    {   u2 requires_index;
        u2 requires_flags;
        u2 requires_version_index;
    } requires[requires_count];

    u2 exports_count;
    {   u2 exports_index;
        u2 exports_flags;
        u2 exports_to_count;
        u2 exports_to_index[exports_to_count];
    } exports[exports_count];

    u2 opens_count;
    {   u2 opens_index;
        u2 opens_flags;
        u2 opens_to_count;
        u2 opens_to_index[opens_to_count];
    } opens[opens_count];

    u2 uses_count;
    u2 uses_index[uses_count];

    u2 provides_count;
    {   u2 provides_index;
        u2 provides_with_count;
        u2 provides_with_index[provides_with_count];
    } provides[provides_count];
    */
    public void parse(ByteReader r, ConstantPool pool) {
        self = new ModuleInfo().read(r, pool);
        int required = r.readUnsignedShort();
        if(self.name.startsWith("java.base")) {
            if(required != 0)
                throw new IllegalArgumentException("'" + self.name + "' module should not have 'require' section");
        }
        List<ModuleInfo> requires = new ArrayList<>(required);
        for (int i = 0; i < required; i++) { // name不能重复
            requires.add(new ModuleInfo().read(r, pool));
        }
        this.requires = requires;

        int exports = r.readUnsignedShort();
        List<ExportInfo> export = new ArrayList<>(exports);
        for (int i = 0; i < required; i++) {
            export.add(new ExportInfo().read(r, pool));
        }
        this.exports = export;

        int opens = r.readUnsignedShort();
        List<ExportInfo> open = new ArrayList<>(opens);
        for (int i = 0; i < required; i++) {
            open.add(new ExportInfo().read(r, pool));
        }
        this.opens = open;

        int uses = r.readUnsignedShort();
        List<String> use = new ArrayList<>(uses);
        for (int i = 0; i < uses; i++) {
            use.add(pool.getName(r));
        }
        this.uses = use;

        int provides = r.readUnsignedShort();
        List<Provider> provide = new ArrayList<>(provides);
        for (int i = 0; i < uses; i++) {
            provide.add(new Provider().read(r, pool));
        }
        this.providers = provide;
    }

    @Override
    protected void toByteArray1(ConstantPool pool, ByteWriter w) {
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

        public ModuleInfo read(ByteReader r, ConstantPool pool) {
            name = ((CstModule) pool.get(r)).getValue().getString();
            access = r.readUnsignedShort();
            CstUTF utf = (CstUTF) pool.get(r);
            version = utf == null ? null : utf.getString();
            return this;
        }

        public void write(ByteWriter w, ConstantPool writer) {
            w.putShort(writer.getModuleId(name)).putShort(access).putShort(version == null ? 0 : writer.getUtfId(name));
        }

        @Override
        public String toString() {
            return "Module " + '\'' + name + '\'' + " ver" + version + ", acc='" + AccessFlag.byIdModule(access) + '\'' + '}';
        }
    }

    public static final class ExportInfo {
        public String Package;
        public int access;
        public List<String> accessible;

        public ExportInfo() {}

        public ExportInfo read(ByteReader r, ConstantPool pool) {
            Package = ((CstPackage) pool.get(r)).getValue().getString();
            access = r.readUnsignedShort();
            int len = r.readUnsignedShort();
            accessible = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                accessible.add(((CstModule) pool.get(r)).getValue().getString());
            }
            return this;
        }

        public void write(ByteWriter w, ConstantPool writer) {
            w.putShort(writer.getPackageId(Package)).putShort(access).putShort(accessible.size());
            for (int i = 0, s = accessible.size(); i < s; i++) {
                w.putShort(writer.getModuleId(accessible.get(i)));
            }
        }

        @Override
        public String toString() {
            return "Export '" + Package + '\'' + ", acc=" + AccessFlag.byIdModule(access) + ", accessibleClasses=" + accessible + '}';
        }
    }

    public static final class Provider {
        public String serviceName;
        public List<String> implement;

        public Provider() {}

        public Provider read(ByteReader r, ConstantPool pool) {
            serviceName = pool.getName(r);
            int len = r.readUnsignedShort();
            if(len == 0)
                throw new IllegalArgumentException("Provider.length should not be 0");
            implement = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                implement.add(pool.getName(r));
            }
            return this;
        }

        public void write(ByteWriter w, ConstantPool writer) {
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