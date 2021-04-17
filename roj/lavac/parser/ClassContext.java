package roj.lavac.parser;

import roj.asm.Parser;
import roj.asm.tree.Clazz;
import roj.asm.tree.Field;
import roj.asm.tree.Method;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.AttrMethodParameters;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.config.ParseException;
import roj.config.word.AbstLexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;

import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/31 17:34
 */
public final class ClassContext {
    private final InputStream in;
    private final IAccessor ctx;
    private final MyHashMap<String, CharSequence> importMap = new MyHashMap<>();
    private JavaLexer wr;
    private final Clazz dest = new Clazz();
    private final ArrayList<Runnable> pendingToStage2 = new ArrayList<>();
    private final MyHashSet<String> finder = new MyHashSet<>();

    public Clazz getDest() {
        return dest;
    }

    private String path;

    public static void main(String[] args) {

    }

    public ClassContext(InputStream in, IAccessor ctx) {
        this.in = in;
        this.ctx = ctx;
    }

    public ClassContext(String absolutePath, InputStream in, IAccessor ctx) {
        this(in, ctx);
        this.path = absolutePath;
    }

    public void stage0Read() throws IOException {
        wr = (JavaLexer) new JavaLexer().init(IOUtil.readUTF(in));
    }

    public void stage1Struct() throws ParseException {
        JavaLexer wr = this.wr;
        Word w;
        CharList tmp = new CharList(64);

        // ## 1. resolve package / import
        CharList pkg = new CharList();
        out:
        while (true) {
            w = wr.nextWord();
            wr.recycle(w);
            switch (w.type()) {
                case Keyword.PACKAGE:
                    if(pkg.length() > 0)
                        throw wr.err("unexpected:package");
                    resolveType(wr, pkg, false);
                    expect(wr, Symbol.semicolon);
                    if(pkg.length() == 0) {
                        throw wr.err("empty:package");
                    }
                    pkg.append('/');
                    break;
                case Keyword.IMPORT: {
                    resolveType(wr, tmp, false);
                    expect(wr, Symbol.semicolon);
                    if(tmp.length() == 0) {
                        throw wr.err("empty:import");
                    }
                    String tot = tmp.toString();
                    int i = tot.lastIndexOf('/');
                    if(i == -1) {
                        System.out.println("Useless import []CC.98");
                    }
                    importMap.put(tot, tot.substring(i + 1));
                    tmp.clear();
                }
                break;

                case Keyword.PUBLIC:
                case Keyword.FINAL:
                case Keyword.ABSTRACT:
                case Keyword.CLASS:
                case Keyword.INTERFACE:
                case Keyword.ENUM:
                case Symbol.at:
                    wr.retractWord();
                    break out;

                default:
                    throw wr.err("unexpected:" + w.val());
            }
        }

        // ## 2. class type, flag
        // ## 2.1 class acc
        int acc = resolveAccessFlag(wr, AccessFlag.PUBLIC | AccessFlag.FINAL | AccessFlag.ABSTRACT);

        // ## 2.2 class type
        acc = getClass(wr, acc);
        dest.accesses = AccessFlag.of(acc);

        // # 3.1 class name
        w = wr.nextWord();
        wr.recycle(w);
        if(w.type() != WordPresets.LITERAL) {
            throw wr.err("unexpected:" + w.val() + ":CLASS_NAME");
        }

        dest.name = pkg.append(w.val()).toString();

        // ## 3.2 generic
        w = wr.nextWord();
        wr.recycle(w);
        if(w.type() == Symbol.lss) { // <
            dest.signature = resolveGeneric(wr, Signature.CLASS);
            System.out.println("Generic test: " + dest.signature);

            w = wr.nextWord();
            wr.recycle(w);
        }

        // ## 4. extends and/or implements

        String parent = "java/lang/Object";
        switch (w.type()) {
            case Keyword.EXTENDS:
                parent = resolveClass(tmp, false, true);
                w = wr.nextWord();
                wr.recycle(w);
                if(w.type() == Symbol.lss) { // extends A<B>
                    appendGeneric(false);
                } else {
                    wr.retractWord();
                }
                break;

            case Keyword.IMPLEMENTS:
                parent = resolveClass(tmp, true, true);
                w = wr.nextWord();
                wr.recycle(w);
                if(w.type() == Symbol.lss) { // implements A<B>
                    appendGeneric(true);
                } else {
                    wr.retractWord();
                }
                break;

            case Symbol.left_l_bracket:
                wr.recycle(w);
                wr.retractWord();
                break;

            default:
                throw wr.err("unexpected:" + w.val() + ":extends, implements, {");
        }
        dest.parent = parent;

        if(w.type() == Keyword.IMPLEMENTS) {
            final List<String> itfs = dest.interfaces;
            out:
            while (true) {
                itfs.add(resolveClass(tmp, false, true));
                w = wr.nextWord();
                if(w.type() == Symbol.lss) { // implements A<B>
                    wr.recycle(w);
                    appendGeneric(true);
                    w = wr.nextWord();
                }

                switch (w.type()) {
                    case Symbol.comma:
                        continue out;
                    case Symbol.left_l_bracket:
                        break out;
                    default:
                        throw wr.err("unexpected:" + w.val() + ":\\,, {");
                }
            }
        }

        final int FIELD_ACC = AccessFlag.TRANSIENT_OR_VARARGS | AccessFlag.VOLATILE_OR_BRIDGE;
        final int METHOD_ACC = AccessFlag.STRICTFP | AccessFlag.NATIVE;

        boolean methodDefaulted = false;

        // ## 5. field, method and/or inner classes
        List<Annotation> pending = new ArrayList<>();
        while (wr.hasNext()) {
            w = wr.nextWord(); // if annotation
            wr.recycle(w);
            if (w.type() == WordPresets.EOF) break;
            if(w.type() == Symbol.at) {
                resolveAnnotations(pending);
            } else {
                // ## 5.1 type public static void main(String[] args
                //                           ^
                acc = resolveAccessFlag(wr, AccessFlag.PUBLIC | AccessFlag.STATIC | AccessFlag.NATIVE | AccessFlag.PRIVATE | AccessFlag.PROTECTED | AccessFlag.TRANSIENT_OR_VARARGS | AccessFlag.VOLATILE_OR_BRIDGE | AccessFlag.STRICTFP | AccessFlag.FINAL | AccessFlag.ABSTRACT);

                // default TYPE xxx
                w = wr.nextWord();
                wr.recycle(w);
                wr.retractWord();
                if(w.type() == Keyword.DEFAULT) {
                    if(!dest.accesses.hasAny(AccessFlag.INTERFACE)) {
                        throw wr.err("unexpected:default");
                    } else {
                        if((acc & AccessFlag.ABSTRACT) != 0) {
                            fireDiagnostic(Diagnostic.Kind.ERROR, "illegal_modifier_compound");
                        }
                        methodDefaulted = true;
                    }
                }

                tmp.clear();
                boolean pr = resolveType(wr, tmp, true);

                w = wr.nextWord(); // probably name
                switch (w.type()) {
                    case Symbol.left_l_bracket: // static initializator
                        pendingToStage2.add(new ClInitParser(dest, wr.index, skipBrackets(wr)));
                        continue;
                    case Keyword.CLASS: // inner class
                        System.err.println("// TODO: Inner Class!");
                        // todo recursion

                        continue;
                    case WordPresets.LITERAL: // method or field
                        break;
                    default:
                        throw wr.err("unexpected:" + w.val());
                }

                Word nx = wr.nextWord();
                wr.recycle(nx);
                Type type = toType(pr, tmp);
                if (nx.type() == Symbol.left_s_bracket) { // method
                    Method method = new Method(acc, dest, w.val(), null);
                    method.setReturnType(type);

                    if(!pending.isEmpty()) {
                        // todo filter and add
                    }

                    w = wr.nextWord();
                    wr.recycle(w);

                    List<String> paramNames;
                    if(w.type() != Symbol.right_s_bracket) {
                        LinkedMyHashMap<String, Boolean> paramName = new LinkedMyHashMap<>();

                        boolean lsVarargs = false;
                        AttrMethodParameters paramAttr = null;
                        while (wr.hasNext()) {
                            if (lsVarargs) {
                                fireDiagnostic(Diagnostic.Kind.ERROR, "vararg_on_last");
                            }

                            acc = resolveAccessFlag(wr, AccessFlag.FINAL);

                            tmp.clear();
                            boolean pr1 = resolveType(wr, tmp, true);
                            if (tmp.equals("void"))
                                fireDiagnostic(Diagnostic.Kind.ERROR, "param_is_void");

                            method.parameters().add(toType(pr1, tmp));

                            w = wr.nextWord();
                            wr.recycle(w);

                            // String... args
                            if (w.type() == Symbol.varargs) {
                                lsVarargs = true;

                                w = wr.nextWord();
                                wr.recycle(w);
                            }

                            if (w.type() == WordPresets.LITERAL) {
                                if (paramName.put(w.val(), true) != null) {
                                    fireDiagnostic(Diagnostic.Kind.ERROR, "dup_param_name");
                                }
                            } else {
                                throw wr.err("unexpected:" + w.val());
                            }

                            if (acc != 0) {
                                if (paramAttr == null) {
                                    paramAttr = new AttrMethodParameters();
                                    method.attributes.add(paramAttr);
                                }
                                paramAttr.flags.put(w.val(), AccessFlag.of(acc));
                            }

                            w = wr.nextWord();
                            wr.recycle(w);
                            if (w.type() == Symbol.right_s_bracket) {
                                if (lsVarargs)
                                    method.accesses.add(AccessFlag.TRANSIENT_OR_VARARGS);

                                break;
                            }
                            wr.retractWord();
                        }
                        paramNames = new ArrayList<>(paramName.keySet());
                    } else {
                        paramNames = Collections.emptyList();
                    }


                    boolean isAbst = !methodDefaulted && (dest.accesses.hasAny(AccessFlag.INTERFACE) || method.accesses.hasAny(AccessFlag.ABSTRACT));

                    if(!isAbst) {
                        w = wr.nextWord();
                        wr.recycle(w);
                        switch (w.type()) {
                            case Symbol.left_l_bracket:
                                break;
                            case Keyword.THROWS:
                                while (wr.hasNext()) {
                                    tmp.clear();
                                    resolveType(wr, tmp, false);

                                    w = wr.nextWord();
                                    wr.recycle(w);
                                    if (w.type() == Symbol.left_l_bracket)
                                        break;
                                    else if (w.type() != Symbol.comma)
                                        throw wr.err("unexpected:" + w.val());
                                }
                                break;
                        }

                        pendingToStage2.add(new MethodParser(method.code = new AttrCode(method), paramNames, wr.index, skipBrackets(wr)));
                        if (!finder.add(method.name + method.rawDesc())) {
                            fireDiagnostic(Diagnostic.Kind.ERROR, /*generic ?*/ "duplicate_method");
                        }
                        dest.methods.add(method);
                    }

                    w = wr.nextWord();
                    wr.recycle(w);

                    if(w.type() != Symbol.semicolon)
                        wr.retractWord();

                } else { // field
                    Field field = new Field(AccessFlag.of(acc), w.val(), type);
                    if (!finder.add(field.name)) {
                        fireDiagnostic(Diagnostic.Kind.ERROR, /*generic ?*/ "duplicate_field");
                    }
                    dest.fields.add(field);
                    // todo

                    w = wr.nextWord();
                    wr.recycle(w);
                    System.out.println("WD " + w);

                    if(w.type() != Symbol.semicolon)
                        wr.retractWord();
                }

                pending.clear();
                methodDefaulted = false;
            }
        }

    }

    private static int skipBrackets(JavaLexer wr) throws ParseException {
        int brLvl = 0;
        do {
            Word w = wr.nextWord();
            switch (w.type()) {
                case Symbol.left_l_bracket:
                    brLvl++;
                    break;
                case Symbol.right_l_bracket:
                    brLvl--;
                    break;
            }
        } while (wr.hasNext() && brLvl >= 0);
        return wr.index;
    }

    private static Type toType(boolean prim, CharList tmp) {
        int al = 0;
        while (tmp.charAt(tmp.length() - 1) == ']') { // xxx[][][]
            tmp.setIndex(tmp.length() - 2);
            al++;
        }
        if(prim) {
            char c;
            switch (tmp.toString()) {
                case "int":
                    c = 'I';
                    break;
                case "char":
                    c = 'C';
                    break;
                case "byte":
                    c = 'B';
                    break;
                case "boolean":
                    c = 'Z';
                    break;
                case "short":
                    c = 'S';
                    break;
                case "double":
                    c = 'D';
                    break;
                case "long":
                    c = 'J';
                    break;
                case "float":
                    c = 'F';
                    break;
                case "void":
                    c = 'V';
                    break;
                default:
                    throw new IllegalStateException("Should not reach here " + tmp);
            }
            if(al == 0) {
                return Type.std(c);
            } else {
                return new Type(c, al);
            }
        } else {
            return new Type(tmp.toString(), al);
        }
    }

    public int getClass(JavaLexer wr, int acc) throws ParseException {
        Word w;
        w = wr.nextWord();
        switch (w.type()) {
            case Keyword.CLASS: // class
                if((acc & (AccessFlag.INTERFACE | AccessFlag.ENUM)) != 0)
                    throw wr.err("illegal_modifier:class:interface,enum");
                if((acc & AccessFlag.SUPER_OR_SYNC) != 0)
                    throw wr.err("duplicate_modifier:class");
                acc |= AccessFlag.SUPER_OR_SYNC;
                break;
            case Keyword.INTERFACE: // interface
                if((acc & (AccessFlag.SUPER_OR_SYNC | AccessFlag.FINAL | AccessFlag.ENUM)) != 0)
                    throw wr.err("illegal_modifier:interface:class,final,enum");
                if((acc & AccessFlag.INTERFACE) != 0)
                    throw wr.err("duplicate_modifier:interface");
                acc |= AccessFlag.ABSTRACT | AccessFlag.INTERFACE;
                break;
            case Keyword.ENUM: // enum
                if((acc & (AccessFlag.ABSTRACT | AccessFlag.INTERFACE | AccessFlag.SUPER_OR_SYNC)) != 0)
                    throw wr.err("illegal_modifier:enum:abstract,class,interface");
                if((acc & AccessFlag.ENUM) != 0)
                    throw wr.err("duplicate_modifier:enum");
                acc |= AccessFlag.ENUM | AccessFlag.FINAL;
                break;
            case Symbol.at: // @interface
                if((acc & (AccessFlag.SUPER_OR_SYNC | AccessFlag.INTERFACE | AccessFlag.ENUM | AccessFlag.ABSTRACT)) != 0)
                    throw wr.err("illegal_modifier:@interface:class,interface,enum,abstract");
                if((acc & AccessFlag.ANNOTATION) != 0)
                    throw wr.err("duplicate_modifier:@interface");
                w = wr.nextWord();
                if(w.type() == Keyword.INTERFACE) {
                    acc |= AccessFlag.ANNOTATION;
                } else {
                    throw wr.err("unexpected:" + w.val() + ":interface");
                }
                break;
            default:
                throw wr.err("unexpected:" + w.val() + ":MODIFIER");
        }
        return acc;
    }

    private void appendGeneric(boolean itf) {
        Signature sign = dest.signature;
        if(sign == null) {
            dest.signature = sign = new Signature(Signature.CLASS);
        }

        AbstLexer.Snapshot snapshot = wr.snapshot();
        pendingToStage2.add(() -> {
            AbstLexer.Snapshot snapshot1 = wr.snapshot();
            wr.restore(snapshot);

            // do op

            wr.restore(snapshot1);
        });

    }

    private String resolveClass(CharList tmp, boolean reqItf, boolean reqStatic) throws ParseException {
        JavaLexer wr = this.wr;
        resolveType(wr, tmp, false);
        if(tmp.length() == 0) {
            throw wr.err("empty:extends");
        }
        FlagList acc = ctx.access(importMap.getOrDefault(tmp, tmp));
        while (acc == null) {
            int lid = TextUtil.limitedLastIndexOf(tmp, '/', 32767);
            if(lid != -1) {
                tmp.set(lid, '$');
                acc = ctx.access(tmp);
            } else {
                break;
            }
        }

        if(acc == null) {
            throw wr.err("unable_resolve:" + tmp.toString());
        }

        tmp.clear();

        if(!acc.hasAny(AccessFlag.STATIC) && reqStatic) {
            fireDiagnostic(Diagnostic.Kind.ERROR, "inherit:non-static_inner_class");
        }

        if(acc.hasAny(AccessFlag.FINAL | AccessFlag.ENUM)) {
            fireDiagnostic(Diagnostic.Kind.ERROR, "inherit:final,enum");
        }

        if(acc.hasAny(AccessFlag.INTERFACE) == reqItf) {
            fireDiagnostic(Diagnostic.Kind.ERROR, "inherit:" + (reqItf ? "non-" : "") + "interface");
        }

        if(!acc.hasAny(AccessFlag.PUBLIC)) {
            int lid = TextUtil.limitedLastIndexOf(tmp, ',', 32767);
            int lid2 = dest.name.lastIndexOf('/');
            if(lid != lid2)
                throw wr.err("inherit:package-private");
        }

        if(tmp.equals("java/lang/Object")) {
            fireDiagnostic(Diagnostic.Kind.WARNING, "extends_object");
        }
        return tmp.toString();
    }

    private static void expect(JavaLexer wr, short k) throws ParseException {
        Word w = wr.nextWord();
        if(w.type() != k) {
            throw wr.err("unexpected:" + w.val() + ':' + Symbol.byId(k));
        }
    }

    private static int resolveAccessFlag(final JavaLexer wr, final int allows) throws ParseException {
        Word w;
        int acc = 0, kind = 0, target;
        out:
        while (true) {
            w = wr.nextWord();
            switch (w.type()) {
                case Keyword.PUBLIC:
                    target = (1 << 16) | (AccessFlag.PUBLIC & 0xFFFF);
                    break;
                case Keyword.PROTECTED:
                    target = (1 << 16) | (AccessFlag.PROTECTED & 0xFFFF);
                    break;
                case Keyword.PRIVATE:
                    target = (1 << 16) | (AccessFlag.PRIVATE & 0xFFFF);
                    break;

                case Keyword.ABSTRACT:
                    target = (1 << 17) | (AccessFlag.ABSTRACT & 0xFFFF);
                    break;
                case Keyword.STATIC:
                    target = (AccessFlag.STATIC & 0xFFFF);
                    break;
                case Keyword.STRICTFP:
                    target = (AccessFlag.STRICTFP & 0xFFFF);
                    break;
                case Keyword.VOLATILE:
                    target = (AccessFlag.VOLATILE_OR_BRIDGE & 0xFFFF);
                    break;
                case Keyword.TRANSIENT:
                    target = (AccessFlag.TRANSIENT_OR_VARARGS & 0xFFFF);
                    break;
                case Keyword.NATIVE:
                    target = (AccessFlag.NATIVE & 0xFFFF);
                    break;
                case Keyword.SYNCHRONIZED:
                    target = (AccessFlag.SUPER_OR_SYNC & 0xFFFF);
                    break;
                case Keyword.FINAL:
                    target = (1 << 17) | (AccessFlag.FINAL & 0xFFFF);
                    break;
                default:
                    wr.retractWord();
                    break out;
            }

            if((kind & (target >>> 16)) != 0) {
                throw wr.err("illegal_modifier:" + w.val());
            }

            kind |= target >>> 16;

            target &= 0xFFFF;

            if((target & allows) == 0) {
                throw wr.err("unsupported_modifier:" + w.val());
            }

            if((acc & target) != 0)
                throw wr.err("duplicate_modifier:" + w.val());
            acc |= target;
        }

        return acc;
    }

    private static boolean resolveType(final JavaLexer wr, final CharList sb, boolean primitive) throws ParseException {
        Word w;

        int fl = 0;
        while (true) {
            w = wr.nextWord();
            wr.recycle(w);
            switch (w.type()) {
                case Keyword.VOID:
                case Keyword.BOOLEAN:
                case Keyword.DOUBLE:
                case Keyword.FLOAT:
                case Keyword.LONG:
                case Keyword.INT:
                case Keyword.SHORT:
                case Keyword.BYTE:
                case Keyword.CHAR:
                    if(primitive && sb.length() == 0) {
                        sb.append(w.val());
                        fl = 2; // primitive array
                    } else
                        throw wr.err("unexpected:" + w.val());
                    break;
                case Symbol.left_m_bracket: // array: []
                    if(fl != 0 && fl != 2)
                        throw wr.err("unexpected:" + w.val());
                    fl = 2;
                    if(wr.nextWord().type() != Symbol.right_m_bracket) {
                        throw wr.err("unexpected:" + w.val());
                    }

                    sb.append("[]");

                    break;
                case WordPresets.LITERAL:
                    if(fl == 1)
                        throw wr.err("unexpected:" + w.val());
                    else if (fl == 2) {
                        wr.retractWord();
                        return true;
                    }
                    fl = 1;
                    sb.append(w.val());
                    break;
                case Symbol.dot:
                    if(fl != 1)
                        throw wr.err("unexpected:.");
                    fl = 0;
                    sb.append('/');
                    break;
                default:
                    wr.retractWord();
                    return fl == 2;
            }
        }
    }

    public List<Annotation> resolveAnnotations() {
        return resolveAnnotations(new ArrayList<>());
    }

    public List<Annotation> resolveAnnotations(List<Annotation> annotations) {
        AbstLexer.Snapshot snapshot = wr.snapshot();
        pendingToStage2.add(() -> {
            AbstLexer.Snapshot snapshot1 = wr.snapshot();
            wr.restore(snapshot);

            // do op

            wr.restore(snapshot1);
        });

        // todo skip


        return annotations;
    }

    public static Signature resolveGeneric(JavaLexer wr, int type) {
        Signature s = new Signature(type);

        // <T extends YYY & ZZZ, V extends T & XXX>
        // Error => V out of bound

        // YYY can be itf / clazz
        // ZZZ can only be itf
        // Feature => can order be not required ?

        // recursion =>
        // <T extends OtherClass<T>>
        // todo
        return s;
    }

    public void stage2Compile() throws ParseException {

    }

    public void stage3Optimize() {

    }

    public String getFilePath() {
        return path;
    }

    public String getContext() {
        return wr == null ? "~IO 错误~" : wr.getText().toString();
    }

    public Diagnostic<? extends ClassContext> fireDiagnostic(Diagnostic.Kind kind, String code) {
        wr.err(code).printStackTrace();
        return null;
        //return wr.fireDiagnostic(kind, this, code, LavacI18n.INSTANCE);
    }

    public ByteList getBytes() {
        return Parser.toByteArrayShared(dest);
    }

    public String getFullName() {
        return dest.name;
    }
}
