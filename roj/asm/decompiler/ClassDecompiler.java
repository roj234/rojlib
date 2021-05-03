package roj.asm.decompiler;

import roj.asm.constant.*;
import roj.asm.struct.Method;
import roj.asm.struct.attr.AttrCode;
import roj.asm.struct.insn.IIndexInsnNode;
import roj.asm.struct.insn.InvokeInsnNode;
import roj.asm.util.AccessFlag;
import roj.asm.util.NodeHelper;
import roj.asm.util.type.LocalVariable;
import roj.asm.util.type.Type;
import roj.collect.ReuseStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

import static roj.asm.Opcodes.*;
import static roj.asm.constant.CstType.*;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 */
public class ClassDecompiler {
    private String generateMethodBod(Method method, List<Type> types, Type returns) {
        StringBuilder javaCode = new StringBuilder();

        String currentMethodName = method.name;

        //寻找CodeAttribute
        AttrCode code = method.code;
        if (code == null) {
            return ""; // abstract
        }

        ReuseStack<String> opStack = new ReuseStack<>(/*code.stackLength*/);
        List<String> varNames = new ArrayList<>(code.localSize);

        // todo signature
        List<LocalVariable> LVT = code.getLVT() == null ? null : code.getLVT().list;

        //初始化本地变量表名，首先如果是实例方法，需要把this放入第一个，然后依次将方法参数名放入
        boolean isStaticMethod = method.access().contains(AccessFlag.STATIC);
        if (!isStaticMethod) {
            varNames.add("this");
        }
        for (int x = 0; x < types.size(); x++) {
            varNames.add("var" + (x + 1));
        }

        for (roj.asm.struct.insn.InsnNode node : code.instructions) {
            byte opc = node.code;
            if ((opc >= 0x15 && opc <= 0x2d) || (opc >= 0x36 && opc <= 0x4e)) {
                node = NodeHelper.decompress(node);
            }
            switch (opc) {
                case ALOAD:
                case ILOAD:
                case LLOAD:
                case DLOAD:
                case FLOAD: {
                    roj.asm.struct.insn.IIndexInsnNode node1 = (roj.asm.struct.insn.IIndexInsnNode) node;
                    System.out.println("Xload_" + node1.getIndex());
                    opStack.push(varNames.get(node1.getIndex()));
                }
                break;
                case INVOKEVIRTUAL:
                case INVOKEINTERFACE: {
                    InvokeInsnNode node1 = (InvokeInsnNode) node;

                    String[] paramNames = getParam(opStack, node1);
                    String targetInstance = opStack.pop();

                    StringBuilder line = new StringBuilder();
                    line.append(targetInstance).append('.').append(node1.name());
                    buildMethodParam(paramNames, line);

                    opStack.push(line.toString());
                }
                break;
                case INVOKESTATIC: {
                    InvokeInsnNode node1 = (InvokeInsnNode) node;

                    String[] paramNames = getParam(opStack, node1);

                    StringBuilder line = new StringBuilder();
                    line.append(node1.owner()).append('.').append(node1.name());
                    buildMethodParam(paramNames, line);

                    opStack.push(line.toString());
                }
                break;
                case INVOKESPECIAL: {
                    InvokeInsnNode node1 = (InvokeInsnNode) node;

                    String[] paramNames = getParam(opStack, node1);
                    String targetInstance = opStack.pop();

                    StringBuilder line = new StringBuilder();
                    if (currentMethodName.equals("<init>")) {
                        if (targetInstance.equals("this")) {
                            line.append("super");
                        } else {
                            line.append("new ").append(targetInstance);
                        }
                    } else if (!targetInstance.equals("this")) {
                        // todo super.xxx
                    }
                    buildMethodParam(paramNames, line);

                    opStack.push(line.toString());
                    break;
                }
                case GETSTATIC:
                    System.out.println("getstatic");
                    break;
                case RETURN:
                    System.out.println("return");
                    break;
                case NEW: {
                    roj.asm.struct.insn.ClassInsnNode node1 = (roj.asm.struct.insn.ClassInsnNode) node;
                    opStack.push(node1.owner().replace('/', '.'));
                    break;
                }
                case DUP:
                    String top = opStack.pop();
                    opStack.push(top);
                    opStack.push(top);
                    break;
                case LDC2_W:
                case LDC_W:
                case LDC: {
                    roj.asm.struct.insn.LoadConstInsnNode node1 = (roj.asm.struct.insn.LoadConstInsnNode) node;

                    String o = "<INTERNAL ERROR: unsupported ldc node>";

                    switch (node1.c.type) {
                        case STRING:
                            o = ((CstString) node1.c).getValue().getString();
                            break;
                        case FLOAT:
                            o = String.valueOf(((CstFloat) node1.c).value);
                            break;
                        case INT:
                            o = String.valueOf(((CstInt) node1.c).value);
                            break;
                        case CLASS:
                            o = ((CstClass) node1.c).getValue().getString().replace('/', '.').concat(".class");
                            break;
                        case DOUBLE:
                            o = String.valueOf(((CstDouble) node1.c).value);
                            break;
                        case LONG:
                            o = String.valueOf(((CstLong) node1.c).value);
                            break;
                    }
                    opStack.push(o);
                }
                break;
                case FADD:
                case LADD:
                case DADD:
                case IADD:
                    opStack.push(opStack.pop() + " + " + opStack.pop());
                    break;
                case FSUB:
                case LSUB:
                case DSUB:
                case ISUB:
                    opStack.push(opStack.pop() + " - " + opStack.pop());
                    break;
                case FMUL:
                case LMUL:
                case DMUL:
                case IMUL:
                    opStack.push(opStack.pop() + " * " + opStack.pop());
                    break;
                case FDIV:
                case LDIV:
                case DDIV:
                case IDIV:
                    opStack.push(opStack.pop() + " / " + opStack.pop());
                    break;
                case FNEG:
                case LNEG:
                case DNEG:
                case INEG:
                    opStack.push(" -" + opStack.pop());
                    break;
                case FRETURN:
                case ARETURN:
                case DRETURN:
                case LRETURN:
                case IRETURN:
                    System.out.println("return");
                    opStack.push("return " + opStack.pop());
                    break;
                case ICONST_0:
                    System.out.println("iconst_0");
                    opStack.push("0");
                    break;
                case ICONST_1:
                    opStack.push("1");
                    break;
                case ICONST_2:
                    opStack.push("2");
                    break;
                case ICONST_3:
                    opStack.push("3");
                    break;
                case ICONST_4:
                    opStack.push("4");
                    break;
                case ICONST_5:
                    opStack.push("5");
                    break;
                case ICONST_M1:
                    opStack.push("-1");
                    break;
                case DSTORE:
                case LSTORE:
                case FSTORE:
                case ISTORE:
                case ASTORE: {
                    roj.asm.struct.insn.IIndexInsnNode node1 = (IIndexInsnNode) node;

                    String obj = opStack.pop();
                    String className = "CLASSName(WIP)";
                    if (varNames.size() < node1.getIndex()) {
                        varNames.add(node1.getIndex(), LVT == null ? "lvt_" + node1.getIndex() : LVT.get(node1.getIndex()).name);
                    }
                    // todo slot reuse
                    opStack.push(className + ' ' + varNames.get(node1.getIndex()) + '=' + obj);
                }
                break;
                case POP2:
                case POP:
                    System.out.println("pop / pop2");
                    //opStack.pop();
                    break;
                default:
                    throw new RuntimeException("Unknow opCode:0x" + node.getOpcode() + " at " + currentMethodName);
            }
        }

        for (Object s : opStack) {
            javaCode.append("       ").append(s).append("\r\n");
        }

        return javaCode.toString();
    }

    @Nonnull
    public static String[] getParam(ReuseStack<String> opStack, InvokeInsnNode node1) {
        int paramSize = node1.parameters().size();

        String[] parameterNames = new String[paramSize];

        for (int i = 0; i < paramSize; i++) {
            parameterNames[paramSize - i - 1] = opStack.pop();
        }
        return parameterNames;
    }

    public static void buildMethodParam(String[] parameterNames, StringBuilder sb) {
        sb.append('(');
        for (String parameterName : parameterNames) {
            sb.append(parameterName).append(',');
        }
        sb.deleteCharAt(sb.length() - 1).append(");");
    }
}