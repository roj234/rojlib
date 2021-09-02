package roj.asm.visitor;

import roj.asm.cst.CstUTF;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/21 2:28
 */
public class ClassVisitor {
    public ConstantWriter cw = new ConstantWriter();
    public ByteWriter bw = new ByteWriter();

    public ByteList poolBuf = new ByteList(), klassBuf = new ByteList();

    public IVisitor fieldVisitor;
    public IVisitor methodVisitor;

    protected int attrAmountIndex, attrAmount;

    public ByteList visit(String clazz, ByteReader r) {
        ByteList pb = bw.list = this.poolBuf;
        pb.ensureCapacity(r.length());
        pb.clear();

        visitBegin(r.readUnsignedShort(), r.readUnsignedShort());

        ConstantPool pool = new ConstantPool(r.readUnsignedShort());
        pool.read(r);
        pool.valid();

        cw.init(pool);

        visitConstants(pool);

        ByteList kb = bw.list = this.klassBuf;
        kb.ensureCapacity(r.length());
        kb.clear();

        int acc = r.readUnsignedShort();
        String ccc = ((CstUTF) pool.get(r)).getString();
        int t = r.readUnsignedShort();
        String parent = t == 0 ? null : ((CstUTF) pool.array(t)).getString();

        int len0 = r.readUnsignedShort();
        String[] arr = new String[len0];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = ((CstUTF) pool.get(r)).getString();
        }
        visitClass(acc, ccc, parent, arr);

        try {
            fieldVisitor.visit(r, pool, bw, cw);
        } catch (Throwable e) {
            fieldVisitor.visitEndError(e);
        }
        try {
            methodVisitor.visit(r, pool, bw, cw);
        } catch (Throwable e) {
            methodVisitor.visitEndError(e);
        }

        int attrLen = r.readUnsignedShort();

        for (int j = 0; j < attrLen; j++) {
            String name0 = ((CstUTF) pool.get(r)).getString();
            int len1 = r.readInt();
            visitAttribute(name0, r.readBytesDelegated(len1));
        }

        kb.addAll(r.getBytes(), r.index, r.remain());
        bw.list = pb;
        cw.write(bw);
        cw.clear();
        pb.addAll(kb, 0, kb.pos());
        return pb;
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
        bw.writeShort(acc).writeShort(cw.getUtfId(name)).writeShort(cw.getUtfId(parent)).writeShort(interfaces.length);
        for(String s : interfaces)
            bw.writeShort(cw.getUtfId(s));
    }

    public void visitAttributes() {
        attrAmountIndex = bw.list.pos();
        attrAmount = 0;
        bw.writeShort(0);
    }

    public void visitAttribute(String name, ByteList data) {
        bw.writeShort(cw.getUtfId(name)).writeInt(data.pos()).writeBytes(data);
    }

    public void visitEnd() {
        int pos = bw.list.pos();
        bw.list.pos(attrAmountIndex);
        bw.writeShort(attrAmount);
        bw.list.pos(pos);
    }
}
