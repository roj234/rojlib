package roj.asm.visitor;

import roj.asm.cst.CstUTF;
import roj.asm.util.AccessFlag;
import roj.asm.util.ConstantPool;
import roj.util.ByteList;
import roj.util.ByteReader;

/**
 * Class visitor
 * 默认实现都是As-is的，你需要自己修改
 *
 * @author Roj233
 * @since 2021/7/21 2:28
 */
public class ClassVisitor {
    public final ConstantPool cw = new ConstantPool();
    public final ByteList     bw = new ByteList();
    public final ByteReader   br = new ByteReader();

    protected final ByteList poolBuf = new ByteList();

    public IVisitor fieldVisitor, methodVisitor;
    public AttributeVisitor attributeVisitor;
    public boolean clearConstant;

    private int attrAmountIndex;
    protected int attrAmount;

    public void preVisit() {
        fieldVisitor.preVisit(this);
        methodVisitor.preVisit(this);
        if (attributeVisitor != null)
            attributeVisitor.preVisit(this);
    }

    public ByteList visit(ByteList b) {
        ByteReader r = this.br;
        r.refresh(b);

        ByteList pb = this.poolBuf;
        pb.ensureCapacity(r.length());
        pb.clear();

        if (r.readInt() != 0xcafebabe) {
            throw new IllegalArgumentException("Illegal header");
        }

        visitBegin(r.readUnsignedShort(), r.readUnsignedShort());

        ConstantPool pool = new ConstantPool(r.readUnsignedShort());
        pool.read(r);

        if (!clearConstant) cw.init(pool);

        visitConstants(pool);

        ByteList kb = bw;
        kb.ensureCapacity(r.length());
        kb.clear();

        int acc = r.readUnsignedShort();
        String self = pool.getName(r);

        boolean module = (acc & AccessFlag.MODULE) != 0;
        if(module && acc != AccessFlag.MODULE)
            throw new IllegalArgumentException("Module should only have 'module' flag");

        String parent = pool.getName(r);
        if (parent == null && (!"java/lang/Object".equals(self) || module)) {
            throw new IllegalArgumentException("No father found");
        }

        int len0 = r.readUnsignedShort();
        String[] arr = new String[len0];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = ((CstUTF) pool.get(r)).getString();
        }
        visitClass(acc, self, parent, arr);

        try {
            fieldVisitor.visit(pool);
        } catch (Throwable e) {
            fieldVisitor.visitEndError(e);
        }
        try {
            methodVisitor.visit(pool);
        } catch (Throwable e) {
            methodVisitor.visitEndError(e);
        }

        int attrLen = r.readUnsignedShort();

        visitAttributes();
        if (attributeVisitor != null) {
            attributeVisitor.visitConstant(pool);
        }
        for (int j = 0; j < attrLen; j++) {
            visitAttribute(((CstUTF) pool.get(r)).getString(), r.readInt());
        }
        visitEnd();

        cw.write(pb);
        cw.clear();
        return pb.put(kb);
    }

    public void postVisit() {
        fieldVisitor.postVisit();
        methodVisitor.postVisit();
        if (attributeVisitor != null)
            attributeVisitor.postVisit();
        cw.clear();
    }

    /**
     * 开始读取一个class
     * @param major 主版本号
     * @param minor 次版本号
     */
    public void visitBegin(int major, int minor) {
        poolBuf.putInt(0xCAFEBABE).putShort(major).putShort(minor);
    }

    /**
     * 读取完了常量池
     * @param pool 常量池
     */
    public void visitConstants(ConstantPool pool) {}

    /**
     *
     * @param acc 访问权限
     * @param name 名字
     * @param parent 父类
     * @param interfaces 接口
     */
    public void visitClass(int acc, String name, String parent, String[] interfaces) {
        bw.putShort(acc).putShort(cw.getClassId(name)).putShort(cw.getClassId(parent)).putShort(interfaces.length);
        for(String s : interfaces)
            bw.putShort(cw.getClassId(s));
    }

    public void visitAttributes() {
        attrAmountIndex = bw.wIndex();
        attrAmount = 0;
        bw.putShort(0);
    }

    public void visitAttribute(String name, int length) {
        int end = br.rIndex + length;
        if (attributeVisitor != null) {
            if (attributeVisitor.visit(name, length)) {
                attrAmount++;
            }
        }
        br.rIndex = end;
    }

    public void visitEnd() {
        int pos = bw.wIndex();
        bw.wIndex(attrAmountIndex);
        bw.putShort(attrAmount);
        bw.wIndex(pos);
    }
}
