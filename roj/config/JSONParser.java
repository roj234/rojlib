package roj.config;

import roj.collect.MyHashSet;
import roj.config.data.*;
import roj.config.word.AbstLexer;
import roj.config.word.Lexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.text.CharList;
import roj.text.TextUtil;

/**
 * JSON - JavaScript 对象表示法（JavaScript Object Notation）
 * <BR>
 * 它是JavaScript的子集
 * 可以直接复制到JavaScript中
 * <p>
 * Author: Asyncorized_MC
 * Filename: JSONParser.java
 */
public final class JSONParser {
    public static final short
            TRUE = 0,
            FALSE = 1,
            NULL = 2,
            left_l_bracket = 3,
            right_l_bracket = 4,
            left_m_bracket = 5,
            right_m_bracket = 6,
            comma = 7,
            colon = 8;

    public static void main(String[] args) throws ParseException {
        String json = TextUtil.concat(args, ' ');

        System.out.println("INPUT = " + json);

        System.out.print("JSON = " + parse(json).toJSON());
    }

    public static ConfEntry parse(CharSequence string) throws ParseException {
        return parse(new JSONLexer().init(string), 0);
    }

    public static ConfEntry parse(CharSequence string, int flag) throws ParseException {
        return parse(new JSONLexer().init(string), flag);
    }

    public static ConfEntry parseIntern(CharSequence string) throws ParseException {
        return parse(new JSONLexer() {
            final MyHashSet<String> set = new MyHashSet<>();

            @Override
            protected Word formClip(short id, CharSequence string) {
                return new Word(id, line, lineOffset, set.intern(string.toString()));
            }

        }.init(string), 0);
    }

    public static ConfEntry parse(AbstLexer wr, int flag) throws ParseException {
        try {
            ConfEntry ce = jsonRead(wr, flag & 253, true);
            if (wr.hasNext()) {
                throw wr.err("期待 /EOF");
            }
            return ce;
        } catch (ParseException e) {
            throw wr.getExceptionDetails(e);
        }
    }

    /**
     * 解析数组定义 <BR>
     * [xxx, yyy, zzz] or []
     */
    static CList jsonArray(AbstLexer wr, int flag) throws ParseException {
        CList list = new CList();

        boolean hasMore = true;
        try {
            hasMore = wr.offset(1) != ']';
        } catch (ArrayIndexOutOfBoundsException ignored) {
        }

        Word w;
        o:
        while (true) {
            w = wr.nextWord();
            switch (w.type()) {
                case right_m_bracket:
                    break o;
                case comma:
                    if (hasMore) {
                        unexpected(wr, ",");
                    }
                    hasMore = true;
                    continue;
                default:
                    wr.retractWord();
                    break;
            }

            hasMore = false;

            ConfEntry result = jsonRead(wr, 2 | (flag & 253), false);

            if (result != null) {
                list.add(result);
            } else {
                if (wr.nextWord().type() != right_m_bracket) {
                    unexpected(wr, "<空>");
                }
                break;
            }
        }

        return list;
    }

    /**
     * 解析对象定义 <BR>
     * {xxx: yyy, zzz: uuu}
     */
    static CMapping jsonObject(AbstLexer wr, int flag) throws ParseException {
        CMapping map = new CMapping();

        boolean hasMore = wr.remain() <= 1 || wr.offset(1) != '}';

        o:
        while (true) {
            Word name = wr.nextWord();
            switch (name.type()) {
                case right_l_bracket:
                    break o;
                case comma:
                    if (hasMore) {
                        unexpected(wr, ",");
                    }
                    hasMore = true;
                    continue;

                case WordPresets.STRING:
                    break;
                case WordPresets.VARIABLE:
                    if((flag & 128) != 0)
                        break;
                default:
                    unexpected(wr, name.val(), "字符串");
            }

            hasMore = false;

            final Word word = wr.nextWord();
            if (word.type() != colon)
                unexpected(wr, word.val(), ":");

            ConfEntry result = jsonRead(wr, 1 | (flag & 253), false);

            boolean end = wr.nextWord().type() == right_l_bracket;

            if (result != null) {
                if((flag & 64) != 0 && map.containsKey(name.val()))
                    throw wr.err("重复的key: " + name.val());
                map.put(name.val(), result);
            } else {
                unexpected(wr, "empty_statement");
            }

            if (end) {
                break;
            }
            wr.retractWord();
        }

        if (map.containsKey("==", Type.STRING)) {
            ObjectSerializer<?> deserializer = ObjectSerializer.REGISTRY.get(map.getString("=="));
            if (deserializer != null) {
                return new CObject<>(map, deserializer);
            }/* else {
                // warning
            }*/
        }

        return map;
    }

    /**
     * @param flag <BR>
     *             1  : in 定义对象 <BR>
     *             2  : in 定义数组 <BR>
     *             64 : 对重复的key报错
     *             128: 仁慈模式 (忽略key中的literal string)
     */
    @SuppressWarnings("fallthrough")
    private static ConfEntry jsonRead(AbstLexer wr, int flag, boolean isOut) throws ParseException {
        ConfEntry cur = null;

        Word w;

        o:
        while (true) {
            w = wr.nextWord();
            switch (w.type()) {
                case left_m_bracket: {
                    if (cur != null) unexpected(wr, w.val());
                    cur = jsonArray(wr, flag);
                }
                break;
                case WordPresets.STRING:
                    if (cur != null) unexpected(wr, w.val());
                    cur = CString.valueOf(w.val());
                    break;
                case WordPresets.DECIMAL_D:
                case WordPresets.DECIMAL_F:
                    if (cur != null) unexpected(wr, w.val());
                    cur = CDouble.valueOf(w.val());
                    break;
                case WordPresets.INTEGER:
                    if (cur != null) unexpected(wr, w.val());
                    cur = CInteger.valueOf(w.val());
                    break;
                case TRUE:
                case FALSE:
                    if (cur != null) unexpected(wr, w.val());
                    cur = CBoolean.valueOf(w.val());
                    break;
                case NULL:
                    if (cur != null) unexpected(wr, w.val());
                    cur = CNull.NULL;
                    break;
                case left_l_bracket: {
                    if (cur != null) unexpected(wr, "{");
                    cur = jsonObject(wr, flag);
                }
                break;

                case WordPresets.EOF:
                    if (isOut && cur != null) {
                        return cur;
                    }

                default:
                    unexpected(wr, w.val());
                    break;

                case right_m_bracket: {
                    if ((flag & 2) == 0) {
                        unexpected(wr, "]");
                    } else {
                        wr.retractWord();
                        break o;
                    }
                }
                break;

                case right_l_bracket: {
                    if ((flag & 1) == 0) {
                        unexpected(wr, "}");
                    } else {
                        wr.retractWord();
                        break o;
                    }
                }
                break;

                case comma: {
                    if ((flag & 3) == 0) {
                        unexpected(wr, ",");
                    } else {
                        wr.retractWord();
                        break o;
                    }
                }
                break;
            }
        }

        return cur;
    }

    static void unexpected(AbstLexer wr, String word, String expecting) throws ParseException {
        throw wr.err("未预料的: " + word + ", 期待: " + expecting);
    }

    static void unexpected(AbstLexer wr, String s) throws ParseException {
        throw wr.err("未预料的: " + s);
    }

    private static class JSONLexer extends Lexer {
        @Override
        protected Word formAlphabetClip(CharList temp) {
            String s = temp.toString();

            short id = WordPresets.VARIABLE;
            switch (s) {
                case "true":
                    id = TRUE;
                    break;
                case "false":
                    id = FALSE;
                    break;
                case "null":
                    id = NULL;
                    break;
            }

            return formClip(id, s);
        }

        @Override
        protected Word readSpecial() throws ParseException {
            char c = next();

            short id;
            switch (c) {
                case '{':
                    id = left_l_bracket;
                    break;
                case '}':
                    id = right_l_bracket;
                    break;
                case '[':
                    id = left_m_bracket;
                    break;
                case ']':
                    id = right_m_bracket;
                    break;
                case ':':
                    id = colon;
                    break;
                case ',':
                    id = comma;
                    break;
                default:
                    throw err("无效字符 '" + c + '\'');
            }

            return formClip(id, String.valueOf(c));
        }
    }
}
