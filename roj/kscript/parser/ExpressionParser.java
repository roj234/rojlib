package roj.kscript.parser;

import roj.collect.Int2IntMap;
import roj.collect.MyHashMap;
import roj.concurrent.OperationDone;
import roj.config.ParseException;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.kscript.api.API;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.node.LabelNode;
import roj.kscript.parser.expr.*;
import roj.kscript.type.KNull;
import roj.kscript.type.KType;
import roj.text.TextUtil;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 操作符优先级靠它实现
 *
 * @author Roj233
 * @since 2020/10/13 22:14
 */
public final class ExpressionParser {
    private static final class PZEntry extends Int2IntMap.Entry {
        PZEntry(int k, int v) {
            super(k, v);
        }

        @Override
        protected void setKey(int k) {
            super.setKey(k);
        }

        PZEntry next(PZEntry newNext) {
            PZEntry t = (PZEntry) next;
            next = newNext;
            return t;
        }
    }

    private final Int2IntMap ordered = new Int2IntMap() {
        @Override
        protected Entry getCachedEntry(int id, int val) {
            PZEntry cached = (PZEntry) this.notUsing;
            if (cached != null) {
                cached.setKey(id);
                cached.v = val;
                this.notUsing = cached.next(null);
                return cached;
            }

            return new PZEntry(id, val);
        }
    };

    private final ArrayList<Expression> words = new ArrayList<>();
    private final ArrayList<PZEntry> sort = new ArrayList<>();

    private final int depth;

    public static void main(String[] args) throws ParseException, IOException {
        System.out.println("JvavScript(KScript) Interpreter 2.0.3 debug/demo\n");

        JSLexer wr = new JSLexer();
        ExpressionParser epr = new ExpressionParser(0);

        if(args.length > 0) {
            String expression = TextUtil.concat(args, ' ');

            System.out.print("Output = ");

            wr.init(expression);

            Expression expr = epr.read(() -> wr, (short) 0);

            System.out.println(expr);

            System.out.print("AST = ");
            ASTree ast = ASTree.builder("");
            expr.write(ast);
            System.out.println(ast);

            return;
        }

        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        String line;
        MyHashMap<String, KType> env = new MyHashMap<>();

        System.out.print(">");
        while ((line = r.readLine()) != null) {
            if(!line.endsWith(";"))
                line += ';';
            wr.init(line);

            try {
                Expression expr = epr.read(() -> wr, (short) 0);

                if (expr == null) {
                    System.out.println();
                } else {
                    System.out.println("Expr: " + expr.toString());
                    System.out.println(expr.compute(env, KNull.NULL));
                }
            } catch (ParseException e) {
                System.out.println(e.toString());
                e.getCause().printStackTrace();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            System.out.print('>');
        }
    }

    public ExpressionParser(int depth) {
        this.depth = depth;
    }

    /**
     * 解析数组定义 <BR>
     * [xxx, yyy, zzz] or []
     */
    public Expression defineArray(PContext ctx, short flag) throws ParseException {
        JSLexer wr = ctx.getLexer();
        List<Expression> expr = new ArrayList<>();

        boolean hasMore = true;
        try {
            hasMore = wr.offset(1) != ']';
        } catch (ArrayIndexOutOfBoundsException ignored) {
        }

        /*
          ArrayGet tmp0 = new ArrayGet();
          tmp0.add(Expr xxx);
          ...
         */
        Word w;
        o:
        while (true) {
            w = wr.nextWord();
            switch (w.type()) {
                case Symbol.right_m_bracket:
                    break o;
                case Symbol.comma:
                    if (hasMore) {
                        unexpected(ctx, ",");
                    }
                    hasMore = true;
                    continue;
                default:
                    wr.retractWord();
                    break;
            }

            hasMore = false;

            Expression result = read(ctx, (short) 128);
            if (result != null) {
                expr.add(result);
            } else {
                if (wr.nextWord().type() != Symbol.right_m_bracket) {
                    unexpected(ctx, "compiler.empty_statement");
                }
                break;
            }
        }

        return new ArrayDefine(expr.isEmpty() ? Collections.emptyList() : expr);
    }

    /**
     * 解析对象定义 <BR>
     * {xxx: yyy, zzz: uuu}
     */
    public Expression defineObject(PContext ctx, short flag) throws ParseException {
        JSLexer wr = ctx.getLexer();
        MyHashMap<String, Expression> map = new MyHashMap<>();

        boolean hasMore = true;
        try {
            hasMore = wr.offset(1) != '}';
        } catch (ArrayIndexOutOfBoundsException ignored) {
        }

        /*
          Map map = new Map();
          map.put(LoadData name, Expr xxx);
          ...
         */
        Word w;
        o:
        while (true) {
            Word name = wr.nextWord();
            switch (name.type()) {
                case Symbol.right_l_bracket:
                    break o;
                case Symbol.comma:
                    if (hasMore) {
                        unexpected(ctx, ",");
                    }
                    hasMore = true;
                    continue;

                case WordPresets.STRING:
                case WordPresets.VARIABLE:
                    break;
                default:
                    unexpected(ctx, name.val(), "compiler.type.string");
            }

            final Word wd = wr.nextWord();
            if (wd.type() != Symbol.colon)
                unexpected(ctx, wd.val(), ":");

            Expression result = read(ctx, (short) 64);

            boolean end = wr.nextWord().type() == Symbol.right_l_bracket;

            if (result != null) {
                map.put(name.val(), result);
            } else {
                unexpected(ctx, "compiler.empty_statement");
            }

            if (end) {
                break;
            }
            wr.retractWord();
        }

        return new ObjectDefine(map.isEmpty() ? Collections.emptyMap() : map);
    }

    @Nullable
    public Expression read(PContext ctx, short exprFlag) throws ParseException {
        return read(ctx, exprFlag, null);
    }

    /**
     * Expression parser(表达式解析器) <BR>
     * 逻辑烦死人 <BR>
     * 但是做好了之后感觉我太NB了! <BR>
     *
     * @param exprFlag <BR>
     *                 8   : in 解析数组索引 <BR>
     *                 16  : in 解析函数调用参数 <BR>
     *                 32  : in 解析if中的内容(为了压缩) <BR>
     *                 64  : in 定义对象 {xxx} <BR>
     *                 128 : in 定义数组 <BR>
     *                 256 : in 解析括号优先 <BR>
     *                 512 : in 三元运算符 - ? <BR>
     *                 1024: in for <BR>
     * @throws ParseException if error occurs.
     */
    @SuppressWarnings("fallthrough")
    @Nullable
    public Expression read(PContext ctx, short exprFlag, LabelNode ifFalse) throws ParseException {
        JSLexer wr = ctx.getLexer();

        ArrayList<Expression> tmp = words;
        Int2IntMap tokens = ordered;
        if(!tmp.isEmpty() || !tokens.isEmpty()) // 使用中
            return API.getParser(depth + 1).read(ctx, exprFlag);

        Expression cur = null;
        UnaryPrefix pf = null;

        Word w;

        /**
         *    1   : 有'.' <BR>
         *    2   : 当前是对象类型 <BR>
         *    4   : 当前是基本类型 <BR>
         *    8   : new
         */
        byte opFlag = 0;

        o:
        while (true) {
            w = wr.nextWord();
            switch (w.type()) {
                case Symbol.lambda:
                    throw wr.err("Functional interface(lambda) is unsupported during this time.");
                case Keyword.NEW:
                    if (cur != null || (opFlag != 0)) unexpected(ctx, w.val(), "compiler.empty");
                    /*if ((opFlag & 1) != 0)
                        unexpected(ctx, ".");
                    if ((opFlag & 4) != 0)
                        unexpected(ctx, "new");*/

                    opFlag |= 8;

                    break;
                // [ x ]
                case Symbol.left_m_bracket: {// array start
                    if ((opFlag & 1) != 0)
                        unexpected(ctx, ".");
                    if ((opFlag & 4) != 0)
                        unexpected(ctx, "[");

                    if (cur == null) {
                        // define array
                        cur = deeper().defineArray(ctx, exprFlag);
                        opFlag |= 2;
                    } else {
                        if ((opFlag & 2) == 0)
                            unexpected(ctx, "[");
                        // get array
                        Expression index = deeper().read(ctx, (short) 8);
                        if (index == null) {
                            unexpected(ctx, "compiler.empty_statement.invalid_array_index");
                        }

                        cur = new ArrayGet(cur, index);
                    }
                }
                break;
                // a.b.c.d
                case WordPresets.VARIABLE: {
                    if (cur == null) {
                        cur = new Variable(w.val(), ctx);
                    } else {
                        if ((opFlag & 1) == 0)
                            unexpected(ctx, w.val(), ".");
                        if ((opFlag & 4) != 0) {
                            unexpected(ctx, w.val());
                        }
                        opFlag &= ~1;
                        cur = new Field(cur, w.val());
                    }
                    opFlag |= 2;
                }
                break;
                // this
                case Keyword.THIS: {
                    if (cur == null) cur = new Std(1);
                    else unexpected(ctx, "this", "compiler.empty");
                }
                break;
                // constant
                //case Keyword.HEX:       // 十六进制
                //case Keyword.BINARY:    // 二进制
                //case Keyword.OCTAL:     // 八进制
                case WordPresets.INTEGER: { // 整数
                    if (cur != null) unexpected(ctx, w.val(), "compiler.type.get_able");
                    else {
                        cur = Constant.valueOf(w);
                        opFlag |= 4;
                    }
                }
                break;
                case WordPresets.CHARACTER:
                case WordPresets.STRING:
                case WordPresets.DECIMAL_D:
                case WordPresets.DECIMAL_F:
                case Keyword.TRUE:
                case Keyword.NULL:
                case Keyword.UNDEFINED:
                case Keyword.NAN:
                case Keyword.INFINITY:
                case Keyword.FALSE: {
                    if (cur != null) unexpected(ctx, w.val(), "compiler.type.get_able");
                    else {
                        cur = Constant.valueOf(w);
                        // string
                        switch (cur.type()) {
                            case 2:
                            case -1:
                                opFlag |= 2;
                                break;
                            default:
                                opFlag |= 4;
                                break;
                        }
                    }
                }
                break;
                case Symbol.colon:
                    if ((exprFlag & 1536) == 0)
                        unexpected(ctx, w.val());
                    if ((exprFlag & 1024) != 0)
                        wr.retractWord();
                    // ? :
                    break o;
                // assign
                case Symbol.assign:
                    if (cur == null || (opFlag & 2) == 0)
                        unexpected(ctx, w.val());
                    Expression right = deeper().read(ctx, exprFlag);

                    if (right == null) {
                        unexpected(ctx, "compiler.empty_statement.invalid_right_value");
                    }

                    /*if(cur instanceof Variable)
                        ctx.assignVariable(((Variable)cur).getName(), right);*/
                    cur = new Assign(cur, right);
                    break;
                case Symbol.add_assign:
                case Symbol.div_assign:
                case Symbol.and_assign:
                case Symbol.lsh_assign:
                case Symbol.mod_assign:
                case Symbol.mul_assign:
                case Symbol.rsh_assign:
                case Symbol.rsh_unsigned_assign:
                case Symbol.sub_assign:
                case Symbol.xor_assign:
                    // a &= 3;
                    // =>
                    // a = a & 3;
                    if (cur == null || (opFlag & 2) == 0)
                        unexpected(ctx, w.val());

                    final Expression expr = deeper().read(ctx, exprFlag);
                    if (expr == null) {
                        unexpected(ctx, "compiler.empty_statement.invalid_right_value");
                    }
                    cur = new Assign(cur, new Binary(assign2op(w.type()), cur, expr, null));
                    break;
                case Symbol.dot: {
                    if ((opFlag & 1) == 0) {
                        opFlag |= 1;
                        opFlag &= ~2;
                    } else unexpected(ctx, ".");
                }
                break;
                case Symbol.left_l_bracket: {
                    if ((opFlag & 1) != 0)
                        unexpected(ctx, ".");
                    if (cur != null) // at beginning
                        unexpected(ctx, "{");
                    cur = deeper().defineObject(ctx, exprFlag);
                    opFlag |= 2;
                }
                break;

                // ( x )
                case Symbol.left_s_bracket: {
                    if ((opFlag & 1) != 0)
                        unexpected(ctx, ".");
                    if (cur != null && (opFlag & 2) == 0)
                        unexpected(ctx, "(");

                    // bracket
                    if (cur == null) {
                        cur = deeper().read(ctx, (short) 256);
                        if(cur == null)
                            throw wr.err("compiler.empty_bracket");
                    } else {
                        if ((opFlag & 2) == 0)
                            unexpected(ctx, "(");
                        // function call
                        List<Expression> args = new LinkedList<>();
                        ExpressionParser next = deeper();

                        while (true) {
                            Expression result = next.read(ctx, (short) 16);
                            if (result != null) {
                                args.add(result);
                            } else {
                                w = wr.nextWord();
                                if (w.type() != Symbol.right_s_bracket) {
                                    unexpected(ctx, w.val(), ")");
                                }
                                break;
                            }
                        }

                        cur = new Method(cur, args, (opFlag & 8) != 0);
                        opFlag &= ~8;
                    }
                }
                break;
                default:
                    if (Keyword.isKeyword(w)) {
                        unexpected(ctx, w.val(), "compiler.type.expr");
                    }
                    if (Symbol.isSymbol(w)) {
                        switch (Symbol.argumentCount(w.type())) {
                            case 1:
                                switch (w.type()) {
                                    case Symbol.inc:
                                    case Symbol.dec:
                                        if (cur != null) { // i++
                                            if ((opFlag & 2) == 0)
                                                unexpected(ctx, w.val(), "compiler.type.object");
                                            cur = new UnaryAppendix(w.type(), cur);
                                            continue o;
                                        } // ++i
                                }

                                {
                                UnaryPrefix ul = new UnaryPrefix(w.type());
                                if(pf != null) {
                                    pf.setRight(ul);
                                } else {
                                    pf = ul;
                                }
                                }

                                break;
                            case 2:
                                if (cur != null) { // has right value
                                    if (pf != null) {
                                        pf.setRight(cur);
                                        String msg = pf.validate();
                                        if (msg != null)
                                            throw wr.err(msg);
                                        cur = pf;
                                        pf = null;
                                    }
                                    tmp.add(cur/*.compress()*/);
                                    opFlag = 0;
                                    cur = null;
                                } else {
                                    switch (w.type()) {
                                        case Symbol.add:
                                            continue o;
                                        case Symbol.sub: {
                                            //if(pf.isNegative()) {
                                            //    pf = null;
                                            //} else {
                                                UnaryPrefix ul = new UnaryPrefix(Symbol.sub);
                                                if(pf != null) {
                                                    pf.setRight(ul);
                                                } else {
                                                    pf = ul;
                                                }
                                            //}
                                        }
                                        continue o;
                                        default:
                                            unexpected(ctx, w.val(), "compiler.type.get_able");
                                    }
                                }

                                tokens.put(tmp.size(), Symbol.priorityFor(w));
                                tmp.add(new SymboL(w.type()));
                                break;
                            case 3:
                                // ? :
                                if (cur == null) {
                                    unexpected(ctx, w.val(), "compiler.type.get_able");
                                }
                                ExpressionParser next = deeper();
                                Expression mid = next.read(ctx, (short) 512);
                                if (mid == null) {
                                    unexpected(ctx, "compiler.empty_statement.illegal");
                                }
                                Expression r = next.read(ctx, exprFlag);
                                if (r == null) {
                                    unexpected(ctx, "compiler.empty_statement.illegal");
                                }
                                tmp.add(new TrueIf(cur, mid, r));
                                break;
                            case 0:
                                unexpected(ctx, w.val());
                                break;
                        }
                    } else {
                        unexpected(ctx, w.val());
                    }
                    break;
                case WordPresets.EOF:
                    unexpected(ctx, "compiler.eof");
                    break o; // useless

                case Symbol.right_m_bracket: {
                    if ((exprFlag & 136) == 0) { // 128 / 8
                        unexpected(ctx, "]");
                    } else {
                        if ((exprFlag & 128) != 0) // define array
                            wr.retractWord();
                        break o;
                    }
                }
                break;

                case Symbol.right_l_bracket: {
                    if ((exprFlag & 64) == 0)
                        unexpected(ctx, "}");
                    else {
                        wr.retractWord();
                        break o;
                    }
                }
                break;

                case Symbol.comma: {
                    if ((exprFlag & 472) == 0) // 8 / 16 / 64 / 128 / 256
                        unexpected(ctx, ",");
                    else {
                        if ((exprFlag & 8) != 0) // is /*function*/ / array
                            wr.retractWord();
                        break o;
                    }
                }
                break;
                case Symbol.right_s_bracket: {
                    if ((exprFlag & 272) == 0) // 16 / 256
                        unexpected(ctx, ")");
                    else {
                        if ((exprFlag & 16) != 0) // is function
                            wr.retractWord();
                        break o;
                    }
                }
                break;
                case Symbol.semicolon:
                    break o;
            }
        }

        if (cur != null) {
            if (pf != null) {
                pf.setRight(cur);
                String msg = pf.validate();
                if (msg != null)
                    throw wr.err(msg);
                cur = pf;
            }
            tmp.add(cur/*.compress()*/);
        } else if (pf != null) {
            throw wr.err("compiler.missing_operand");
        }

        if (w.type() == Symbol.semicolon) {
            if ((opFlag & 8) != 0) unexpected(ctx, ";", "compiler.type.invoke");
            if ((exprFlag /*& ~2048*/) != 0) unexpected(ctx, ";", description(exprFlag));
            wr.retractWord();
        }

        System.out.println(tmp);

        Expression result = null;
        if (!tokens.isEmpty()) {
            List<PZEntry> sort = this.sort;

            sort.addAll(Helpers.cast(tokens.entrySet()));
            sort.sort((a, b) -> Integer.compare(b.v, a.v));

            for (int i = 0, e = sort.size(); i < e; i++) {
                if (i > 0) {
                    int lv = sort.get(i - 1).getKey();
                    for (int j = i; j < e; j++) {
                        PZEntry pzEntry = sort.get(j);
                        if (pzEntry.getKey() > lv) {
                            pzEntry.setKey(pzEntry.getKey() - 2);
                        }
                    }
                }

                int v = sort.get(i).getKey();

                Expression l = tmp.get(v - 1);
                Expression op = tmp.remove(v);
                Expression r = tmp.remove(v);

                result = new Binary(((SymboL) op).operator, l, r, i == e - 1 ? ifFalse : LabelNode.TRY_CATCH_ENDPOINT).compress();
                tmp.set(v - 1, result);
            }

            tokens.clear();
            sort.clear();
        } else {
            result = tmp.isEmpty() ? null : tmp.get(0).compress();
        }

        tmp.clear();

        return result;
    }

    private static String description(short exprFlag) {
        if((exprFlag & 8) != 0)
            return "compiler.delimiter.array_index  ']' ";
        if((exprFlag & 16) != 0)
            return "compiler.delimiter.func  ',' ";
        if((exprFlag & 32) != 0)
            return "compiler.delimiter.if  ')' ";
        if((exprFlag & 64) != 0)
            return "}";
        if((exprFlag & 128) != 0)
            return "]";
        if((exprFlag & 256) != 0)
            return ")";
        if((exprFlag & 512) != 0)
            return "compiler.delimiter.triple  ':' ";
        if((exprFlag & 1024) != 0)
            return "compiler.delimiter.for  ';' ";
        return null;
    }

    public void reset() {
        this.words.clear();
        this.sort.clear();
        this.ordered.clear();
    }

    private static short assign2op(short type) {
        switch (type) {
            case Symbol.add_assign:
                return Symbol.add;
            case Symbol.div_assign:
                return Symbol.divide;
            case Symbol.and_assign:
                return Symbol.and;
            case Symbol.lsh_assign:
                return Symbol.lsh;
            case Symbol.mod_assign:
                return Symbol.mod;
            case Symbol.mul_assign:
                return Symbol.mul;
            case Symbol.rsh_assign:
                return Symbol.rsh;
            case Symbol.rsh_unsigned_assign:
                return Symbol.rsh_unsigned;
            case Symbol.sub_assign:
                return Symbol.sub;
            case Symbol.xor_assign:
                return Symbol.xor;
        }
        throw OperationDone.NEVER;
    }

    private ExpressionParser deeper() {
        return this;//API.getParser(depth + 1);
    }

    private static void unexpected(PContext context, String word, String expecting) throws ParseException {
        throw context.getLexer().err("compiler.unexpected_expect:" + word + ':' + expecting);
    }

    private static void unexpected(PContext context, String s) throws ParseException {
        throw context.getLexer().err("compiler.unexpected:" + s);
    }
}
