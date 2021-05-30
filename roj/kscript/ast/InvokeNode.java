package roj.kscript.ast;

import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.util.InsnList;
import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;
import roj.kscript.vm.ResourceManager;

import java.util.List;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 23:27
 */
public final class InvokeNode extends Node {
    final short argc;

    public InvokeNode(boolean staticCall, int argCount, boolean noRet) {
        super(staticCall ? Opcode.INVOKE : Opcode.INVOKE_NEW);
        if(argCount > 32767 || argCount < 0)
            throw new IndexOutOfBoundsException("KScript only support at most 32767 parameters, got " + argCount);
        if(noRet)
            argCount |= 32768;
        this.argc = (short) argCount;
    }

    @Override
    public Node execute(Frame frame) {
        ResourceManager.get().pushStack();

        List<KType> argsL;

        int argc = this.argc & 32767;
        if (argc != 0) {
            argsL = ResourceManager.retainArgHolder(argc);

            for (int i = argc - 1; i >= 0; i--) {
                argsL.set(i, frame.pop().setFlag(1));
            }
        } else {
            argsL = null;
        }

        KFunction fn = frame.last().asFunction();
        ArgList args = ResourceManager.retainArgList(this, frame, argsL);

        boolean v = this.argc >= 0;
        switch (code) {
            case INVOKE:
                KType kb = fn.invoke(frame.$this, args);
                if (v)
                    frame.setLast(kb);
                else
                    frame.pop();
                break;
            case INVOKE_NEW:
                KType instance = fn.createInstance(args);
                if (instance instanceof IObject)
                    fn.invoke((IObject) instance, args);

                if (v)
                    frame.setLast(instance);
                else
                    frame.pop();
                break;
        }

        if (argc > 0)
            ResourceManager.releaseArgObjs(args, argsL);

        ResourceManager.get().popStack();

        return next;
    }

    @Override
    public String toString() {
        return (argc < 0 ? "void " : "") + "Invoke(" + (argc & 32767) + ')';
    }

    @Override
    public void toVMCode(Clazz clazz, Method method, InsnList list) {

    }
}
