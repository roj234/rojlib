package roj.lavac.parser;

import roj.asm.tree.attr.AttrCode;

import java.util.List;

/**
 * Method语法分析器
 *
 * @author Roj233
 * @since 2021/5/11 1:43
 */
public class MethodParser implements Runnable {
    JavaLexer    wr;
    ClassContext owner;
    int          lexerBegin, lexerEnd;

    AttrCode   code;
    InsnHelper pull;

    /**
     * 缓存索引
     */
    private final int depth;

    public MethodParser(AttrCode code, List<String> paramNames, int start, int end) {
        this.depth = -1;
        this.code = code;
        pull = new InsnHelper(this.code.instructions);
        System.out.println("MP " + paramNames + "  " + start + "  " + end);
    }

    @Override
    public void run() {
        wr.setLineHandler(pull);

    }
}