package roj.asm.util;

import roj.asm.Parser;
import roj.asm.tree.Attributed;
import roj.asm.tree.IClass;
import roj.asm.tree.Method;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.*;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/4/23 14:18
 */
public class AttrHelper {
    public static AttrCode getOrCreateCode(ConstantPool cp, MethodNode node) {
        if (node instanceof Method) {
            Method m = (Method) node;
            if (m.code != null) return m.code;
            else {
                return m.code = new AttrCode(m);
            }
        } else {
            Attribute attr = node.attrByName("Code");
            if (attr == null) {
                AttrCode code = new AttrCode(node);
                node.attributes().putByName(code);
                return code;
            } else if (attr instanceof AttrCode) {
                return (AttrCode) attr;
            } else {
                AttrCode code = new AttrCode(node, Parser.reader(attr), cp);
                node.attributes().putByName(code);
                return code;
            }
        }
    }

    public static List<Annotation> getAnnotations(ConstantPool cp, Attributed node, boolean vis) {
        Attribute a = node.attrByName(vis ? AttrAnnotation.VISIBLE : AttrAnnotation.INVISIBLE);
        if (a == null) return null;
        if (a instanceof AttrAnnotation) return ((AttrAnnotation) a).annotations;
        AttrAnnotation anno = new AttrAnnotation(vis ? AttrAnnotation.VISIBLE : AttrAnnotation.INVISIBLE,
                                                 Parser.reader(a), cp);
        node.attributes().putByName(anno);
        return anno.annotations;
    }

    public static List<AttrInnerClasses.InnerClass> getInnerClasses(ConstantPool cp, IClass node) {
        Attribute a = node.attrByName("InnerClasses");
        if (a == null) return null;

        AttrInnerClasses ic;
        if (a instanceof AttrInnerClasses) ic = (AttrInnerClasses) a;
        else node.attributes().putByName(ic = new AttrInnerClasses(Parser.reader(a), cp));
        return ic.classes;
    }

    public static AttrBootstrapMethods getBootstrapMethods(ConstantPool cp, IClass node) {
        Attribute a = node.attrByName("BootstrapMethods");
        if (a == null) return null;

        AttrBootstrapMethods bm;
        if (a instanceof AttrBootstrapMethods) bm = (AttrBootstrapMethods) a;
        else node.attributes().putByName(bm = new AttrBootstrapMethods(Parser.reader(a), cp));
        return bm;
    }
}
