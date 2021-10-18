package roj.asm.visitor;

import roj.asm.cst.CstUTF;
import roj.asm.util.AccessFlag;
import roj.asm.util.ConstantPool;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

/**
 * Class visitor
 * 默认实现都是As-is的，你需要自己修改
 *
 * @author Roj233
 * @since 2021/7/21 2:28
 */
public class ClassVisitor {
    protected final ConstantPool cw = new ConstantPool();
    protected final ByteWriter   bw = new ByteWriter();
    protected final ByteReader     br = new ByteReader();

    protected final ByteList poolBuf = new ByteList(), klassBuf = new ByteList();

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

        ByteList pb = bw.list = this.poolBuf;
        pb.ensureCapacity(r.length());
        pb.clear();

        if (r.readInt() != 0xcafebabe) {
            throw new IllegalArgumentException("Illegal header");
        }

        visitBegin(r.readUnsignedShort(), r.readUnsignedShort());

        ConstantPool pool = new ConstantPool(r.readUnsignedShort());
        pool.read(r);

        if (!clearConstant)
            cw.init(pool);

        visitConstants(pool);

        ByteList kb = bw.list = this.klassBuf;
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

        bw.list = pb;
        cw.write(bw);
        cw.clear();
        pb.addAll(kb, 0, kb.pos());
        return pb;
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
        bw.writeInt(0xCAFEBABE).writeShort(major).writeShort(minor);
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
        bw.writeShort(acc).writeShort(cw.getClassId(name)).writeShort(cw.getClassId(parent)).writeShort(interfaces.length);
        for(String s : interfaces)
            bw.writeShort(cw.getClassId(s));
    }

    public void visitAttributes() {
        attrAmountIndex = bw.list.pos();
        attrAmount = 0;
        bw.writeShort(0);
    }

    public void visitAttribute(String name, int length) {
        int end = br.index + length;
        if (attributeVisitor != null) {
            if (attributeVisitor.visit(name, length)) {
                attrAmount++;
            }
        }
        br.index = end;
    }

    public void visitEnd() {
        int pos = bw.list.pos();
        bw.list.pos(attrAmountIndex);
        bw.writeShort(attrAmount);
        bw.list.pos(pos);
    }
}
