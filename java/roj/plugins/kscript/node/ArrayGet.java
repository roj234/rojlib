package roj.plugins.kscript.node;

import org.jetbrains.annotations.NotNull;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.config.data.Type;
import roj.plugins.kscript.KCompiler;

/**
 * 获取数组或对象动态属性
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class ArrayGet implements VarNode {
    private ExprNode array, index;
    private boolean delete;

    public ArrayGet(ExprNode array, ExprNode index) {
        this.array = array;
        this.index = index;
    }

    @Override public String toString() {return array+"["+index+']';}

    @NotNull
    @Override
    public ExprNode resolve() {
        array = array.resolve();
        index = index.resolve();
        return this;
    }

    @Override public byte type() {return -1;}

    @Override public boolean setDeletion() {return delete = true;}

    @Override public CEntry eval(CMap ctx) {
        var a = array.eval(ctx);
        var b = index.eval(ctx);
        return a.mayCastTo(Type.LIST) && b.mayCastTo(Type.INTEGER) ? a.asList().get(b.asInt()) : a.asMap().get(b.asString());
    }
    @Override public void evalStore(CMap ctx, CEntry val) {
        var a = array.eval(ctx);
        var b = index.eval(ctx);

        if (a.mayCastTo(Type.LIST) && b.mayCastTo(Type.INTEGER)) {
            a.asList().set(b.asInt(), val);
        } else {
            a.asMap().put(b.asString(), val);
        }
    }

    @Override public void compile(KCompiler tree, boolean noRet) {
        if(noRet && !delete) throw new NotStatementException();

        array.compile(tree, false);
        index.compile(tree, false);
        //tree.Std(delete ? Opcode.DELETE_OBJ : Opcode.GET_OBJ);
    }
    @Override public void compileLoad(KCompiler tree) {
        array.compile(tree, false);
        index.compile(tree, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ArrayGet get = (ArrayGet) o;

        if (delete != get.delete) return false;
        if (!array.equals(get.array)) return false;
		return index.equals(get.index);
	}

    @Override
    public int hashCode() {
        int result = array.hashCode();
        result = 31 * result + index.hashCode();
        result = 31 * result + (delete ? 1 : 0);
        return result;
    }
}
