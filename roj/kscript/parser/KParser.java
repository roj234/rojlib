package roj.kscript.parser;

import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.ReuseStack;
import roj.collect.ToIntMap;
import roj.config.ParseException;
import roj.config.word.Lexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.io.IOUtil;
import roj.kscript.api.API;
import roj.kscript.api.ErrorHandler;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.node.LabelNode;
import roj.kscript.func.KFunction;
import roj.kscript.parser.expr.Expression;
import roj.kscript.type.ContextPrimer;
import roj.kscript.type.KInteger;
import roj.kscript.type.KType;
import roj.kscript.type.Type;
import roj.kscript.util.LabelInfo;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 语法分析器
 *
 * @author solo6975
 * @since 2020/10/3 19:20
 */
public class KParser implements PContext {
    /**
     * todo 其他的优化策略：常量（自定义来没有修改过的变量）传播， 删除未使用的变量, ASTree
     */

    JSLexer wr = new JSLexer();
    ASTree tree;
    String file;
    /**
     * 变量作用域
     */
    ContextPrimer context;

    private final int depth;
    public ErrorHandler handler = (type, file, e) -> {
        System.err.println(type + ": " + file + "   " + e.toString());
        e.getCause().printStackTrace();
    };

    public KParser(ContextPrimer context) {
        this.context = context;
        this.depth = -1;
        this.undefVars = new MyHashMap<>();
    }

    public KParser(int depth) {
        this.depth = depth;
    }

    public KParser reset(KParser parent) {
        this.file = parent.file;
        this.context = new ContextPrimer(parent.context);
        this.wr = parent.wr;

        this.success = true;
        this.undefVars = parent.undefVars;

        this.arguments.clear();
        this.inBlock = false;

        this.namedLabels.clear();
        this.lastLabel = null;
        this.labelCount = 0;
        this.labels.clear();

        return this;
    }

    public KFunction parse(File file) throws IOException, ParseException {
        wr.init(IOUtil.readFile(file));
        this.file = file.getName();
        return parse();
    }

    /// region 解析
    public KFunction parse(String file, String text) throws ParseException {
        wr.init(text);
        this.file = file;
        return parse();
    }

    private KFunction parse() throws ParseException {
        tree = ASTree.builder(file);
        wr.setLineHandler(tree);

        Word w;
        while (true) {
            w = wr.nextWord();
            if (w.type() == WordPresets.EOF) {
                break;
            } else {
                statement(w);
            }
        }

        _checkDel();

        _checkUndefVar();

        if (success) {
            //System.out.println("---编译成功---");
            return tree.build(context);
        } else {
            throw new ParseException(new RuntimeException("Failed to parse file"));
        }
    }

    private void _checkDel() throws ParseException {
        if ("DEL".equals(tree.lastRegionName())) {
            wr.retractWord();
            _onWarning(wr.nextWord(), "compiler.useless_delete");
        }
    }

    private KFunction parseInner(ASTree parent, String sub) throws ParseException {
        tree = ASTree.builder(parent, sub);
        wr.setLineHandler(tree);

        functionParser();

        _checkDel();

        return success ? tree.build(this.context) : null;
    }

    //endregion
    //region 函数

    private void functionParser() throws ParseException {
        except(WordPresets.VARIABLE, "compiler.type.name");
        except(Symbol.left_s_bracket);
        arguments();
        except(Symbol.right_s_bracket);
        except(Symbol.left_l_bracket);
        body();
        //"DEL".equals(tree.lastRegionName())
        except(Symbol.right_l_bracket);
    }

    /**
     * 当前方法的参数
     */
    ToIntMap<String> arguments = new ToIntMap<>();

    boolean inBlock;

    /// 参数
    private void arguments() throws ParseException {
        arguments.clear();

        Word word;

        int i = 0;
        byte flag = 0;

        o:
        while (true) {
            word = wr.nextWord();
            switch (word.type()) {
                case Symbol.right_s_bracket:
                    wr.retractWord();
                    break o;
                case WordPresets.VARIABLE:
                    if (flag == 1)
                        _onError(word, "缺少逗号分隔");
                    arguments.put(word.val(), i++);
                    flag = 1;
                    break;
                case Symbol.comma:
                    if (flag == 0) {
                        _onError(word, "多余的逗号");
                    }
                    flag = 0;
                    break;
            }
        }
    }

    /**
     * 函数体
     */
    @SuppressWarnings("fallthrough")
    private void body() throws ParseException {
        inBlock = true;

        Word word;
        o:
        while (true) {
            word = wr.nextWord();
            switch (word.type()) {
                case WordPresets.EOF:
                    _onError(word, "compiler.eof");
                case Symbol.right_l_bracket:
                    wr.retractWord();
                    break o;
                default:
                    statement(word);
            }
        }
    }

    /**
     * 语句
     */
    private void statement(Word word) {
        if (lastLabel != null && labelCount-- == 0) {
            lastLabel = null;
        }

        switch (tree.lastRegionName()) {
            case "DEL":
                if (word.type() != Keyword.DELETE) {
                    context.enterRegion(tree.NextRegion());
                }
                break;
            case "ARG_LOAD":
                switch (word.type()) {
                    default:
                        context.enterRegion(tree.NextRegion());
                        break;
                    case Keyword.VAR:
                    case WordPresets.VARIABLE:
                }
                break;
        }


        try {
            switch (word.type()) {
                case Keyword.VAR:
                    define(false);
                    break;
                case Keyword.FINAL:
                    define(true);
                    break;
                case Keyword.CONTINUE:
                    _break(false);
                    break;
                case Keyword.BREAK:
                    _break(true);
                    break;
                case Keyword.GOTO:
                    _goto();
                    break;
                case Keyword.SWITCH:
                    _switch();
                    break;
                case Keyword.RETURN:
                    _return();
                    break;
                case Keyword.DELETE:
                    _delete();
                    break;
                case Keyword.FOR:
                    _for();
                    break;
                case Keyword.WHILE:
                    _while();
                    break;
                case WordPresets.VARIABLE:
                    wr.retractWord();
                    assign();
                    break;
                case Keyword.THROW:
                    _throw();
                    break;
                case Keyword.IF:
                    _if();
                    break;
                case Keyword.DO:
                    _do();
                    break;
                case Keyword.FUNCTION:
                case Keyword.EXPORT:
                    _func(word.type() == Keyword.EXPORT);
                    break;
                case Keyword.TRY:
                    _try();
                    break;
                case Symbol.semicolon:
                    _onWarning(word, "compiler.empty_statement");
                    break;
                case Symbol.left_l_bracket:
                    context.recordCreation();
                    body();
                    except(Symbol.right_l_bracket);
                    context.restoreCreation(tree);
                    break;
                default:
                    boolean flag = Symbol.isSymbol(word);
                    if(!flag) {
                        switch(word.type()) {
                            case Keyword.NEW:
                            case Keyword.NULL:
                            case Keyword.UNDEFINED:
                            case Keyword.THIS:
                                flag = true;
                        }
                    }

                    if (flag) {
                        wr.retractWord();
                        Expression expr = API.getParser(0).read(this, (short) 0);
                        if (expr != null) {
                            expr.write(tree);
                            tree.Std(ASTCode.POP); // 清除返回值
                            except(Symbol.semicolon);
                        }
                    } else {
                        _onError(word, "compiler.not_statement");
                    }
                    break;
            }
        } catch (ParseException e) {
            _onError(e);
        }
    }

    private void _delete() throws ParseException {
        // delete xx;
        Word w = wr.nextWord();
        if (w.type() == WordPresets.VARIABLE) {// delete xxx;
            if (!"DEL".equals(tree.lastRegionName())) {
                context.enterRegion(tree.NextRegion("DEL"));
            }
            context.delete(w.val());
            except(Symbol.semicolon);
        } else {
            _onError(w, "compiler.unexpected:" + w.val());
        }
    }

    /**
     * 返回
     */
    private void _return() throws ParseException {
        Word w = wr.nextWord();
        if (w.type() == Symbol.semicolon) {
            tree.Std(ASTCode.RETURN_EMPTY);
        } else {
            wr.retractWord();
            Expression expr = API.getParser(0).read(this, (short) 0);
            if (expr == null) {
                _onError(w, "What return it is??");
                return;
            }

            expr.write(tree);
            tree.Std(ASTCode.RETURN);

            except(Symbol.semicolon);
        }
        if (inBlock) {
            except(Symbol.right_l_bracket, "compiler.unreachable_statement");
            wr.retractWord();
        }
    }

    /**
     * 内部函数
     */
    private void _func(boolean export) throws ParseException {
        String name;
        Word w = wr.nextWord();

        if (export) {
            _onWarning(w, "compiler.export");
            if (w.type() != Keyword.FUNCTION) {
                _onError(w, "compiler.unexpected:" + w.val() + ":function");
                wr.retractWord();
                return;
            }
            w = wr.nextWord();
        }
        if (w.type() != WordPresets.VARIABLE) {
            if (w.type() != Symbol.left_s_bracket) {
                _onError(w, "compiler.unexpected:" + w.val() + ":compiler.type.name");
                wr.retractWord();
                return;
            } else {
                name = "<anonymous>";
            }
        } else {
            name = w.val();
        }

        wr.retractWord();

        context.define(name);

        KFunction func = API.getFileParser(depth + 1, this).parseInner(tree, name);

        wr.setLineHandler(tree);

        if (func == null) {
            success = false;
            return;
        }

        context.define(name, func);

        if (export) {
            context.setFinalCopy(name);
        }
    }

    // endregion
    // region 条件

    private final Map<String, LabelInfo> namedLabels = new MyHashMap<>();
    private final ReuseStack<LabelInfo> labels = new ReuseStack<>();

    private LabelInfo lastLabel;
    private byte labelCount = 0;

    private void _label(String val) {
        namedLabels.put(val, lastLabel = new LabelInfo(tree.Label(Integer.toString(labels.size()))));
        labelCount = 1;
    }

    private void _try() throws ParseException {
        except(Symbol.left_l_bracket);

        LabelNode catchNode = new LabelNode();
        LabelNode finallyNode = new LabelNode();
        LabelNode end = new LabelNode();

        tree.TryEnter(catchNode, finallyNode, end);

        context.recordCreation();
        body();
        context.restoreCreation(tree);

        except(Symbol.right_l_bracket);

        tree.Std(ASTCode.TRY_EXIT)/*.Goto(end)*/;

        byte flag = 0;

        Word w;
        o:
        while (true) {
            w = wr.nextWord();
            switch (w.type()) {
                case Keyword.FINALLY:
                    if ((flag & 1) != 0)
                        throw wr.err("compiler.duplicate.finally");
                    tree.node0(finallyNode);
                    except(Symbol.left_l_bracket);
                    body();
                    except(Symbol.right_l_bracket);
                    // END ?
                    tree.Goto(LabelNode.TRY_CATCH_ENDPOINT);
                    flag |= 1;
                    break;
                case Keyword.CATCH:
                    if ((flag & 2) != 0)
                        throw wr.err("compiler.duplicate.catch");

                    w = wr.nextWord();
                    boolean hasVar = false;
                    switch (w.type()) {
                        case Symbol.left_s_bracket: // (
                            w = wr.nextWord();
                            if (w.type() != WordPresets.VARIABLE)
                                throw wr.err("compiler.unexpected:" + w);
                            except(Symbol.right_s_bracket);
                            except(Symbol.left_l_bracket);

                            hasVar = true;
                            break;
                        case Symbol.left_l_bracket: // {
                            break;
                        default:
                            _onError(w, "compiler.unexpected:" + w.val() + ':' + Symbol.byId(Symbol.left_s_bracket));
                            wr.retractWord();
                            return;
                    }

                    tree.node0(catchNode);

                    if (hasVar) {
                        //context.beginRegion();
                        context.enterRegion(tree.NextRegion("_CATCH"));
                        context.define(w.val());
                        // end try | e = pop();

                        tree/*.Node(catchNode)*//*.Std(ASTCode.TRY_EXIT)*/.Set(w.val());
                    }

                    body();
                    except(Symbol.right_l_bracket);

                    if (hasVar) {
                        context.enterRegion(tree.NextRegion("_CATCH"));
                        //context.endRegion(tree);
                    }

                    // END ?
                    tree.Goto(LabelNode.TRY_CATCH_ENDPOINT);

                    flag |= 2;
                    break;
                default:
                    wr.retractWord();
                    break o;
            }
        }

        if (flag == 0) {
            // 孤立的try
            _onError(w, "compiler.standalone.try");
            return;
        }

        tree.node0(end);
    }

    /**
     * goto <x>
     */
    private void _goto() throws ParseException {
        Word w = wr.nextWord();
        if (w.type() != WordPresets.VARIABLE) {
            _uLabel(w);
            return;
        }
        LabelInfo info = namedLabels.get(w.val());
        if (info != null && info.head != null) {
            tree.Goto(info.head);
        } else {
            _onError(w, "compiler.goto.unknown_label");
        }
        except(Symbol.semicolon);
    }

    /**
     * break [x] and continue [x]
     */
    private void _break(boolean isBreak) throws ParseException {
        Word w = wr.nextWord();
        switch (w.type()) {
            case WordPresets.VARIABLE: {
                LabelInfo info = namedLabels.get(w.val());
                if (info != null) {
                    __break(isBreak, w, info);

                    except(Symbol.semicolon);
                } else {
                    _onError(w, "compiler.goto.unknown_label");
                }
            }
            break;
            case Symbol.semicolon:
                if (labels.isEmpty()) {
                    _onError(w, "compiler.break.not_in_label");
                } else {
                    __break(isBreak, w, labels.last());
                }
                break;
            default:
                _uLabel(w);
                break;
        }
    }

    private void __break(boolean isBreak, Word w, LabelInfo info) throws ParseException {
        if (isBreak) {
            if (info.onBreak == null) {
                _uLabel(w);
            } else {
                tree.Goto(info.onBreak);
            }
        } else {
            if (info.onContinue == null) {
                _uLabel(w);
            } else {
                tree.Goto(info.onContinue);
            }
        }
        if (inBlock) {
            except(Symbol.right_l_bracket, "compiler.unreachable_statement");
            wr.retractWord();
        }
    }

    private void _uLabel(Word w) throws ParseException {
        _onError(w, "compiler.goto.illegal_label");
        except(Symbol.semicolon);
    }

    private void _throw() throws ParseException {
        Word w = wr.nextWord();
        wr.retractWord();

        Expression expr = API.getParser(0).read(this, (short) 0);
        if(expr == null/* || expr.type() != -1*/) {
            _onError(w, "compiler.empty_statement");
            return;
        }
        expr.write(tree);
        tree.Std(ASTCode.THROW);

        except(Symbol.semicolon);

        if (inBlock) {
            except(Symbol.right_l_bracket, "compiler.unreachable_statement");
            wr.retractWord();
        }
    }

    /**
     * if 条件判断语句
     */
    private void _if() throws ParseException {
        LabelNode ifFalse = condition(true, Symbol.right_s_bracket);
        if (ifFalse == null) return;

        block();

        _else(ifFalse);
    }

    /**
     * 语句块 or 单行语句
     */
    private void block() throws ParseException {
        Word word = wr.nextWord();
        if (word.type() == Symbol.left_l_bracket) {
            body();
            except(Symbol.right_l_bracket);
        } else {
            inBlock = false;
            statement(word);
        }
    }

    /**
     * 条件判断
     *
     * @return false 跳转点
     */
    @Nullable
    private LabelNode condition(boolean checkBracket, short end) throws ParseException {
        if (checkBracket)
            except(Symbol.left_s_bracket);

        ExpressionParser parser = API.getParser(0);

        LabelNode ifFalse = new LabelNode();

        Expression equ = parser.read(this, (short) (checkBracket ? 16 : 0), ifFalse);
        if (equ == null) {
            _onError(wr.readWord(), "compiler.empty_if_statement");
            return null;
        }

        equ.write(tree);

        except(end);

        return ifFalse;
    }


    /**
     * else 语句 <BR>
     * 和数学书上写的一样, 它是可选的
     *
     * @param ifFalse false跳转点
     */
    private void _else(LabelNode ifFalse) throws ParseException {
        Word word = wr.nextWord();
        if (word.type() != Keyword.ELSE) {
            wr.retractWord();
            // if false goto : false
            //   ifTrue
            // : false
            tree.node0(ifFalse);
            return;
        }

        LabelNode end = new LabelNode();


        word = wr.nextWord();
        tree.Goto(end).node0(ifFalse);
        if (word.type() == Symbol.left_l_bracket) {
            body();
            except(Symbol.right_l_bracket);
        } else {
            //wr.retractWord();
            statement(word);

        }
        tree.node0(end);

        // if (xx) {} else if() {}
        //      is equals to
        // if (xx) {} else { if() {} }

    }

    /**
     * for循环
     */
    private void _for() throws ParseException {
        final ExpressionParser parser = API.getParser(0);

        LabelNode continueTo = new LabelNode();
        tree.node0(continueTo);

        boolean createdVar;

        except(Symbol.left_s_bracket);

        Word w = wr.nextWord();
        switch (w.type()) {
            case Symbol.semicolon:
                createdVar = false;
                break;
            case Keyword.VAR:
                context.recordCreation();
                createdVar = true;
                define(false);
                break;
            default:
                _onError(w, "compiler.unexpected:" + w.val() + ":var or ;");
                return;
        }

        LabelNode breakTo = condition(false, Symbol.semicolon);
        if (breakTo == null) {
            _onError(w, "compiler.not_condition");
            return;
        }

        List<Expression> execLast = new ArrayList<>();

        do {
            Expression expr = parser.read(this, (short) (16 | 1024));
            if (expr == null)
                break;
            execLast.add(expr);

        } while (wr.nextWord().type() == Symbol.colon);
        wr.retractWord();

        except(Symbol.right_s_bracket);

        enterCycle(continueTo, breakTo);

        block();

        endCycle();

        if (!execLast.isEmpty()) {
            LabelNode ol = new LabelNode(continueTo);
            tree.node0(continueTo);
            for (Expression expr : execLast) {
                expr.write(tree);
                tree.Std(ASTCode.POP); // 清除返回值
            }
            tree.Goto(ol);
        } else {
            tree.Goto(continueTo);
        }

        tree.node0(breakTo);

        if (createdVar) {
            context.restoreCreation(tree);
        }
    }

    private void endCycle() {
        labels.pop();
    }

    private void enterCycle(LabelNode continueTo, LabelNode breakTo) {
        LabelInfo info;
        if (lastLabel != null) {
            info = lastLabel;
            lastLabel.onBreak = breakTo;
            lastLabel.onContinue = continueTo;
        } else {
            info = new LabelInfo(null, breakTo, continueTo);
        }

        labels.push(info);
    }

    /**
     * do-while循环
     */
    private void _do() throws ParseException {
        LabelNode continueTo = new LabelNode();
        tree.node0(continueTo);

        LabelNode breakTo = new LabelNode();

        enterCycle(continueTo, breakTo);

        block(); // do {}

        endCycle();

        except(Keyword.WHILE, "while");
        LabelNode breakTo1 = condition(true, Symbol.right_s_bracket);
        if (breakTo1 == null) {
            return;
        }

        tree.Goto(continueTo).Node(breakTo).node0(breakTo1);

        except(Symbol.semicolon);
    }

    /**
     * a
     * while循环
     */
    private void _while() throws ParseException {
        LabelNode continueTo = new LabelNode();
        tree.node0(continueTo);

        LabelNode breakTo = condition(true, Symbol.right_s_bracket);
        if (breakTo == null)
            return;

        enterCycle(continueTo, breakTo);

        block();

        endCycle();

        tree.Goto(continueTo).node0(breakTo);
    }

    /**
     * swich
     * // todo
     */
    private void _switch() throws ParseException {

        _onError(wr.readWord(), "暂未支持");
        if (true)
            return;

        Expression expr = API.getParser(0).read(this, (short) 256);

        expr.write(tree); // on stack now

        except(Symbol.left_l_bracket);

        switch (expr.type()) {
            case 0:
            case 1:
                // int
                switchInt(expr);
                break;
            case -1:
            case 2:
                // string
                switchString(expr);
                break;
            case 3:
                wr.retractWord();
                _onError(wr.readWord(), "布尔值无法用做switch");
        }
    }

    private void switchInt(Expression expr) throws ParseException {
        IntMap<LabelNode> nodes = new IntMap<>();
        body();

        except(Symbol.right_l_bracket);

    }

    private void switchString(Expression expr) throws ParseException {
        Map<String, LabelNode> nodes = new MyHashMap<>();
        body();

        except(Symbol.right_l_bracket);
    }

    // endregion
    // region 表达式和变量

    /**
     * 定义变量(可选的赋值)
     */
    private void define(boolean isFinal) throws ParseException {
        Word word = wr.nextWord();
        if (word.type() != WordPresets.VARIABLE) {
            _onError(word, "compiler.variable.not_name");
            wr.retractWord();
            return;
        }

        if (context.exists(word.val()) || arguments.containsKey(word.val())) {
            _onError(word, "compiler.variable.exists");
        }

        context.define(word.val());
        String name = word.val();

        word = wr.nextWord();
        if (word.type() == Symbol.assign) {
            Expression expr = API.getParser(0).read(this, (short) 0);
            if (expr == null) {
                _onError(word, "compiler.not_statement");
                return;
            }
            if (expr.type() != -1) {
                context.define(name, expr.asCst().val());
            } else {
                expr.write(tree);
                tree.Set(name);
            }
            word = wr.nextWord();
        }

        if (word.type() == Symbol.comma) {
            define(isFinal);
        } else if (word.type() != Symbol.semicolon) {
            _onError(word, "compiler.unexpected:" + word.val() + ":;");
            wr.retractWord();
        }
    }

    /**
     * 标准单个表达式
     */
    private void assign() throws ParseException {
        { // label
            Lexer.Snapshot snapshot = wr.snapshot();
            Word k = wr.readWord();
            Word w = wr.readWord();
            if (k.type() == WordPresets.VARIABLE && w.type() == Symbol.colon) {
                _label(k.val());
                return;
            } else {
                wr.restore(snapshot);
            }
        }

        Expression expr = API.getParser(0).read(this, (short) 0);
        if (expr == null || expr.type() != -1) {
            _onError(wr.nextWord(), "compiler.not_statement");
            return;
        } else {
            expr.write(tree);
            tree.Std(ASTCode.POP); // 清除返回值, 任何表达式都有且仅有一个返回值
        }

        except(Symbol.semicolon);
    }

    @Override
    public KType useVariable(String name) {
        if (!context.exists(name)) {
            if (arguments.containsKey(name)) {
                if (!"ARG_LOAD".equals(tree.lastRegionName()))
                    context.enterRegion(tree.NextRegion("ARG_LOAD"));
                context.define(name);

                tree.Load(KInteger.valueOf(arguments.getInt(name))).Std(ASTCode.LOAD_ARGUMENT).Set(name);
            } else {
                _currentUndefVar(wr, name);
                // 函数lazy定义
            }
            return null;
        }
        // todo
        return labels.isEmpty() ? context.getNullable(name) : null;
    }

    @Override
    public void assignVariable(String name, Expression right) {
        if ("ARG_LOAD".equals(tree.lastRegionName()) && arguments.containsKey(name) && right.isConstant())
            context.enterRegion(tree.NextRegion());
        this.context.put(name, right.isConstant() ? right.asCst().val() : null);
    }

    @Override
    public JSLexer getLexer() {
        return wr;
    }

    //endregion
    // region 输出错误

    private boolean success = true;//语法解析结果
    private Map<String, ParseException> undefVars;

    private void _currentUndefVar(JSLexer wr, String name) {
        undefVars.put(name, wr.err("compiler.undefined_variable:" + name));
    }

    private void _checkUndefVar() {
        for (String name : undefVars.keySet()) {
            KType type = context.getNullable(name);
            if (type == null || type.getType() != Type.FUNCTION) {
                _onError(undefVars.get(name));
            }
        }
        undefVars.clear();
    }

    private void _onError(ParseException e) {
        success = false;
        handler.handle("ERR", file, e);
    }

    private void _onError(Word word, String v) {
        success = false;
        handler.handle("ERR", file, wr.getDetails(v, word));
    }

    private void _onWarning(Word word, String v) {
        handler.handle("WARN", file, wr.getDetails(v, word));
    }

    private void except(short id) throws ParseException {
        except(id, Symbol.byId(id));
    }

    private void except(short id, String s) throws ParseException {
        Word w = wr.nextWord();
        if (w.type() != id) {
            _onError(w, "compiler.unexpected:" + w.val() + ':' + s);
            wr.retractWord();
        }
    }

    //endregion

}
