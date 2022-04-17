package roj.asm.evc;

import roj.asm.TransformException;
import roj.asm.cst.CstString;
import roj.asm.tree.*;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.*;
import roj.asm.type.Type;
import roj.asm.util.*;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.util.Helpers;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static roj.asm.Opcodes.*;
import static roj.asm.type.Type.*;

/**
 * @author Roj233
 * @since 2022/4/29 18:08
 */
public final class EVCBuilder {
    public static EVCBuilder from(Context ctx) throws TransformException {
        ConstantData data = ctx.getData();
        List<Annotation> annotations = AttrHelper.getAnnotations(data.cp, data, false);
        if (annotations == null) throw new TransformException("没有找到EVC注解");
        block:{
            for (int i = 0; i < annotations.size(); i++) {
                Annotation a = annotations.get(i);
                if (a.clazz.endsWith("EnumViaConfig")) {
                    break block;
                }
            }
            throw new TransformException("没有找到EVC注解");
        }
        EVCBuilder info = new EVCBuilder();
        info.ctx = ctx;

        Method clInit = data.getUpgradedMethod("<clinit>", "()V");
        if (clInit == null) {
            clInit = new Method(AccessFlag.STATIC, data, "<clinit>", "()V");
            clInit.code = new AttrCode(clInit);
            data.methods.add(Helpers.cast(clInit));
        } else {
            // return
            InsnList insn = clInit.code.instructions;
            insn.remove(insn.size() - 1);
            for (int i = 0; i < insn.size(); i++) {
                InsnNode node = insn.get(i);
                if (node.getOpcode() == INVOKESPECIAL) {
                    InvokeInsnNode iin = (InvokeInsnNode) node;
                    if (iin.owner.equals(data.name) && iin.name.equals("<init>")) {
                        throw new TransformException("不应手动创建任何" + data.name + "对象");
                    }
                }
            }
        }
        info.clinit = clInit.code;
        clInit.code.interpretFlags = AttrCode.COMPUTE_SIZES | AttrCode.COMPUTE_FRAMES;

        int id = info.valuesId = data.fields.size();
        Field array = new Field(AccessFlag.STATIC, "^"+id, "[L" + data.name + ";");
        data.fields.add(Helpers.cast(array));

        InsnList insn = clInit.code.instructions;
        info.ANewArrayIndex = insn.size();
        insn.add(null);
        insn.add(new ClassInsnNode(ANEWARRAY, data.name));
        insn.add(new FieldInsnNode(PUTSTATIC, data, id));

        Method values = data.getUpgradedMethod("values", "()[L" + data.name + ";");
        if (values == null) throw new TransformException("不存在返回数组的values方法");
        if ((values.accesses & AccessFlag.STATIC) == 0) throw new TransformException("values()不是静态的");
        AttrCode code = AttrHelper.getOrCreateCode(data.cp, values);
        code.localSize = 0;
        code.stackSize = 1;
        code.exceptions = null;
        code.frames = null;
        insn = code.instructions;
        insn.clear();
        insn.add(new FieldInsnNode(GETSTATIC, data, id));
        insn.add(NPInsnNode.of(ARETURN));

        Method byId = data.getUpgradedMethod("byId", "(I)L" + data.name + ";");
        if (byId != null) {
            if ((byId.accesses & AccessFlag.STATIC) == 0) throw new TransformException("byId()不是静态的");
            code = AttrHelper.getOrCreateCode(data.cp, byId);
            code.localSize = 1;
            code.stackSize = 2;
            code.exceptions = null;
            code.interpretFlags = AttrCode.COMPUTE_FRAMES;
            insn = code.instructions;
            insn.clear();

            LabelInsnNode returnNull = new LabelInsnNode();

            // if (id < 0) return null;
            insn.add(NPInsnNode.of(ILOAD_0));
            insn.add(new IfInsnNode(IFLT, returnNull));

            // if (id >= values.length) return null;
            insn.add(NPInsnNode.of(ILOAD_0));
            insn.add(new FieldInsnNode(GETSTATIC, data, id));
            insn.add(NPInsnNode.of(ARRAYLENGTH));
            insn.add(new IfInsnNode(IF_icmpge, returnNull));

            // return values[id]
            insn.add(new FieldInsnNode(GETSTATIC, data, id));
            insn.add(NPInsnNode.of(ILOAD_0));
            insn.add(NPInsnNode.of(AALOAD));
            insn.add(NPInsnNode.of(ARETURN));

            // return null;
            insn.add(returnNull);
            insn.add(NPInsnNode.of(ACONST_NULL));
            insn.add(NPInsnNode.of(ARETURN));
        }

        if (data.getUpgradedMethod("byName", "(Ljava/lang/String;)L" + data.name + ";") != null)
            throw new TransformException("byName方法已被弃用, 请使用valueOf");

        Method valueOf = data.getUpgradedMethod("valueOf", "(Ljava/lang/String;)L" + data.name + ";");
        if (valueOf != null) {
            int id1 = data.fields.size();
            Field map = new Field(AccessFlag.STATIC, "^"+id1, "Lroj/collect/MyHashMap;");
            data.fields.add(Helpers.cast(map));

            if ((valueOf.accesses & AccessFlag.STATIC) == 0) throw new TransformException("valueOf()不是静态的");
            code = AttrHelper.getOrCreateCode(data.cp, valueOf);
            code.localSize = 1;
            code.stackSize = 2;
            code.exceptions = null;
            code.frames = null;
            insn = code.instructions;
            insn.clear();

            insn.add(new FieldInsnNode(GETSTATIC, data, id1));
            insn.add(NPInsnNode.of(ALOAD_0));
            insn.add(new InvokeItfInsnNode("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"));
            insn.add(new ClassInsnNode(CHECKCAST, data.name));
            insn.add(NPInsnNode.of(ARETURN));

            info.mapId = id1;

            insn = clInit.code.instructions;
            insn.add(new ClassInsnNode(NEW, "roj/collect/MyHashMap"));
            insn.add(NPInsnNode.of(DUP));
            info.NewMapSizeIndex = insn.size();
            insn.add(null);
            insn.add(new InvokeInsnNode(INVOKESPECIAL, "roj/collect/MyHashMap", "<init>", "(I)V"));
            insn.add(new FieldInsnNode(PUTSTATIC, data, id1));
        }

        List<? extends MethodNode> ms = data.methods;
        for (int i = 0; i < ms.size(); i++) {
            MethodNode m = ms.get(i);
            List<Annotation> anns = AttrHelper.getAnnotations(data.cp, m, false);
            if (anns == null) continue;
            for (int j = 0; j < anns.size(); j++) {
                Annotation ann = anns.get(j);
                if (ann.clazz.endsWith("EnumViaConfig$Constructor")) {
                    if (!m.name().equals("<init>")) throw new TransformException("构造器注解必须应用于构造器");

                    CstrInfo cstr = new CstrInfo();
                    cstr.index = i;
                    List<AnnVal> array1 = ann.getArray("value");
                    List<Type> par = m.parameters();
                    if (array1.size() != par.size() - 2) throw new TransformException("构造器注解的名称数量不匹配");

                    Param p = new Param();
                    p.name = "name";
                    p.type = new Type("java/lang/String");
                    cstr.param.add(p);

                    for (int k = 2; k < par.size(); k++) {
                        p = new Param();
                        p.name = array1.get(k-2).asString();
                        p.type = par.get(k);
                        cstr.param.add(p);
                    }

                    info.constructors.add(cstr);
                    break;
                }
            }
        }

        List<? extends FieldNode> fs = data.fields;
        for (int i = 0; i < fs.size(); i++) {
            FieldNode f = fs.get(i);
            List<Annotation> anns = AttrHelper.getAnnotations(data.cp, f, false);
            if (anns == null) continue;
            for (int j = 0; j < anns.size(); j++) {
                Annotation ann = anns.get(j);
                if (ann.clazz.endsWith("EnumViaConfig$Holder")) {
                    if ((f.accessFlag() & AccessFlag.STATIC) == 0) throw new TransformException("@Holder的所属不是静态字段");
                    int val = ann.getInt("optional", 0);
                    info.holders.putInt(ann.getString("value"), val == 0 ? i : -i-1);
                    break;
                }
            }
        }
        return info;
    }

    private Context ctx;

    private final List<CstrInfo> constructors = new SimpleList<>();
    private final ToIntMap<String> holders = new ToIntMap<>();

    private final MyHashSet<String> have = new MyHashSet<>();

    private final List<CstrInfo> tmp = new SimpleList<>();

    private AttrCode clinit;

    private int ANewArrayIndex, NewMapSizeIndex, Count;

    private int mapId, valuesId;

    public Context build() {
        if (!holders.isEmpty()) {
            for (Iterator<ToIntMap.Entry<String>> itr = holders.selfEntrySet().iterator(); itr.hasNext(); ) {
                ToIntMap.Entry<String> entry = itr.next();
                if (entry.v < 0) itr.remove();
            }
            if (!holders.isEmpty()) {
                throw new NullPointerException("缺失Holder的名称: " + holders.keySet());
            }
        }

        InsnList list = clinit.instructions;
        list.set(ANewArrayIndex, NodeHelper.loadInt(Count));
        if (NewMapSizeIndex > 0) {
            list.set(NewMapSizeIndex, NodeHelper.loadInt(Count));
        }
        list.add(NPInsnNode.of(RETURN));
        return ctx;
    }

    private EVCBuilder() {}

    public EVCBuilder addEntry(CMapping map) throws TransformException {
        if (!map.containsKey("name")) throw new TransformException("参数列表必须包含name");
        if (!have.add(map.getString("name"))) throw new TransformException("唯一名称 " + map.getString("name") + " 已存在");
        ConstantData data = ctx.getData();

        InsnList list = clinit.instructions;

        list.add(new ClassInsnNode(NEW, data.name));
        list.add(NPInsnNode.of(DUP));
        list.add(NodeHelper.loadInt(Count));
        create(list, constructors, map);

        Integer fieldId = holders.remove(map.getString("name"));
        if (fieldId != null) {
            int fid = fieldId;
            list.add(NPInsnNode.of(DUP));
            list.add(new FieldInsnNode(PUTSTATIC, data, fid < 0 ? -fid-1 : fid));
        }
        list.add(NPInsnNode.of(ASTORE_0));

        list.add(new FieldInsnNode(GETSTATIC, data, valuesId));
        list.add(NodeHelper.loadInt(Count));
        list.add(NPInsnNode.of(ALOAD_0));
        list.add(NPInsnNode.of(AASTORE));

        if (NewMapSizeIndex > 0) {
            list.add(new FieldInsnNode(GETSTATIC, data, mapId));
            list.add(new LdcInsnNode(LDC, new CstString(map.getString("name"))));
            list.add(NPInsnNode.of(ALOAD_0));
            list.add(new InvokeItfInsnNode("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"));
            list.add(NPInsnNode.of(POP));
        }

        Count++;
        return this;
    }

    void create(InsnList list, List<CstrInfo> csts, CMapping map) throws TransformException {
        List<CstrInfo> cst = tmp;
        cst.clear();
        cst.addAll(csts);

        for (Map.Entry<String, CEntry> entry : map.entrySet()) {
            if (cst.isEmpty()) throw new TransformException("没有找到合适的构造器");
            check:
            for (int i = cst.size() - 1; i >= 0; i--) {
                List<Param> params1 = cst.get(i).param;
                for (int j = params1.size() - 1; j >= 0; j--) {
                    if (params1.get(j).name.equals(entry.getKey())) break check;
                }
                cst.remove(i);
            }
        }

        for (int i = cst.size() - 1; i >= 0; i--) {
            if (cst.get(i).param.size() != map.size()) cst.remove(i);
        }

        if (cst.size() != 1) {
            throw new TransformException((cst.isEmpty() ? "没有找到合适的构造器" : "多个构造器均符合所给参数") + ": " + map.toShortJSONb() + "\n" +
                                             "可用的构造器: " + csts);
        }

        CstrInfo selected = cst.get(0);

        List<Param> params = selected.param;
        try {
            for (int i = 0; i < params.size(); i++) {
                Type target = params.get(i).type;
                insert(list, target, map.get(params.get(i).name));
            }
        } catch (Throwable e) {
            throw new TransformException("构造器不适配给定的参数类型: " + map.toShortJSONb() + "\n构造器: " + selected, e);
        }
        list.add(new InvokeInsnNode(INVOKESPECIAL, ctx.getData(), selected.index));
    }

    private void insert(InsnList list, Type target, CEntry e) throws TransformException {
        if (target.array > 0) {
            CList list1 = e.asList();
            NodeHelper.newArray(list, target, list1.size());
            target.array--;
            for (int i = 0; i < list1.size(); i++) {
                list.add(NPInsnNode.of(DUP));
                list.add(NodeHelper.loadInt(i));
                insert(list, target, list1.get(i));
                list.add(putArray(target));
            }
            target.array++;
            return;
        }

        if (e.getType() == roj.config.data.Type.NULL && target.type != CLASS) throw new TransformException("无法将Null转换为基本类型");

        switch (target.type) {
            case CLASS:
                if (e.getType() == roj.config.data.Type.NULL) {
                    list.add(NPInsnNode.of(ACONST_NULL));
                } else {
                    if (!target.owner.equals("java/lang/String")) throw new TransformException("不知如何转换 " + e + " 为对象 " + target.owner);
                    list.add(new LdcInsnNode(LDC, new CstString(e.asString())));
                }
                break;
            case BYTE:
                int num = e.asInteger();
                if (num != (byte) num) throw new TransformException("数字溢出: " + num + " as byte");
                list.add(new U1InsnNode(BIPUSH, num));
                break;
            case SHORT:
                num = e.asInteger();
                if (num != (short) num) throw new TransformException("数字溢出: " + num + " as short");
                list.add(NodeHelper.loadInt(num));
                break;
            case CHAR:
                if (e.getType() == roj.config.data.Type.STRING) {
                    String v = e.asString();
                    if (v.length() != 1) throw new TransformException("char length should be 1");
                    num = v.charAt(0);
                } else {
                    num = e.asInteger();
                    if (num < 0 || num > 0xFFFF) throw new TransformException("数字溢出: " + num + " as char");
                }
                list.add(NodeHelper.loadInt(num));
                break;
            case BOOLEAN:
                list.add(NPInsnNode.of(e.asBool() ? ICONST_1 : ICONST_0));
                break;
            case DOUBLE:
                list.add(NodeHelper.loadDouble(e.asDouble()));
                break;
            case INT:
                list.add(NodeHelper.loadInt(e.asInteger()));
                break;
            case FLOAT:
                list.add(NodeHelper.loadFloat((float) e.asDouble()));
                break;
            case LONG:
                list.add(NodeHelper.loadLong(e.asLong()));
                break;
            default:
                throw new TransformException("不支持的参数类型 " + target.type);
        }
    }

    static InsnNode putArray(Type t) {
        if (t.array == 0) {
            switch (t.type) {
                case CLASS:
                    break;
                case BYTE:
                case BOOLEAN:
                    return NPInsnNode.of(BASTORE);
                case SHORT:
                    return NPInsnNode.of(SASTORE);
                case CHAR:
                    return NPInsnNode.of(CASTORE);
                case DOUBLE:
                    return NPInsnNode.of(DASTORE);
                case INT:
                    return NPInsnNode.of(IASTORE);
                case FLOAT:
                    return NPInsnNode.of(FASTORE);
                case LONG:
                    return NPInsnNode.of(LASTORE);
                default:
                    throw new IllegalStateException("不支持的参数类型 " + t.type);
            }
        }
        return NPInsnNode.of(AASTORE);
    }

    static final class CstrInfo {
        int index;
        List<Param> param = new SimpleList<>();

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder().append("#").append(index).append(" (");
            for (int i = 0; i < param.size(); ) {
                Param p = param.get(i);
                sb.append(p.name).append(" : ").append(p.type);
                if (++i == param.size()) break;
                sb.append(", ");
            }
            sb.append(')');
            return sb.toString();
        }
    }

    static final class Param {
        String name;
        Type type;
        int index;
    }
}
