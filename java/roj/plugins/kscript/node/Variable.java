package roj.plugins.kscript.node;

import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.plugins.kscript.KCompiler;

/**
 * 变量
 * @author Roj233
 * @since 2020/10/30 19:14
 */
final class Variable implements VarNode {
    private final String name;
    public Variable(String name) {this.name = name;}

    @Override public String toString() {return name;}

	public boolean setDeletion() {return false;}

    @Override public CEntry eval(CMap ctx) {return ctx.get(name);}
    @Override public void evalStore(CMap ctx, CEntry val) {ctx.put(name, val);}

    @Override
    public void compile(KCompiler tree, boolean noRet) {
        if(noRet) throw new NotStatementException();
    }
    @Override
    public void compileLoad(KCompiler tree) {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Variable variable = (Variable) o;
		return name.equals(variable.name);
	}

    @Override
    public int hashCode() {return name.hashCode()+1;}
}
