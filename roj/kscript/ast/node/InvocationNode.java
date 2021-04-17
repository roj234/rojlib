package roj.kscript.ast.node;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.kscript.Arguments;
import roj.kscript.api.IGettable;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.Frame;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;
import roj.kscript.util.Stack;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 23:27
 */
public class InvocationNode extends Node {
    final short argc;
    private KType[] arguments;

    public InvocationNode(boolean staticCall, int argCount) {
        super(staticCall ? ASTCode.INVOKE_FUNCTION : ASTCode.INVOKE_NEW);
        this.argc = (short) argCount;
    }

    @Override
    public Node execute(Frame frame) {
        KType[] arguments = this.arguments;

        Stack stack = frame.stack;
        if (argc != 0) {
            if (arguments == null)
                this.arguments = arguments = new KType[argc];

            for (int i = argc - 1; i >= 0; i--) {
                arguments[i] = stack.pop();
            }
        }

        KFunction func = stack.last().asFunction();
        Arguments args = new Arguments(frame, arguments, argc);

        switch (getCode()) {
            case INVOKE_FUNCTION:
            case INVOKE_FUNC_NORET:
                KType kb = func.invoke(frame.getThis(), args);
                if (getCode() == ASTCode.INVOKE_FUNCTION)
                    stack.setLast(kb);
                else
                    stack.pop();
                break;
            case INVOKE_NEW:
                KType instance = func.createInstance(args);
                if (instance instanceof IGettable)
                    func.invoke((IGettable) instance, args);

                stack.setLast(instance);
                break;
        }

        if (argc > 0)
            for (int i = argc - 1; i >= 0; i--) {
                arguments[i] = null;
            }

        return next;
    }

    @Override
    public String toString() {
        return (getCode() == ASTCode.INVOKE_FUNC_NORET ? "void " : "") + "Invoke(" + argc + ')';
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }
}
