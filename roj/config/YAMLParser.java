package roj.config;

import roj.config.data.*;
import roj.config.word.Lexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;

import java.io.File;
import java.io.IOException;

import static roj.config.JSONParser.*;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: YAMLParser.java
 */
public class YAMLParser {
    public static final short
            TRUE = 0,
            FALSE = 1,
            NULL = 2,
            left_l_bracket = 3,
            right_l_bracket = 4,
            left_m_bracket = 5,
            right_m_bracket = 6,
            comma = 7, // ,
            colon = 8, // :
            sub = 9; // -

    public static void main(String[] args) throws ParseException, IOException {
        //String yaml = TextUtil.concat(args, ' ');

        CharList yaml = new CharList();
        ByteReader.decodeUTF(-1, yaml, new ByteList(IOUtil.readFile(new File(args[0]))));

        //System.out.println("INPUT = " + yaml);

        System.out.print("YML = " + parse(yaml).toJSON());
    }

    public static CMapping parse(CharSequence string) throws ParseException {
        return parse((YAMLLexer) new YAMLLexer().init(string));
    }

    public static CMapping parse(YAMLLexer wr) throws ParseException {
        try {
            CMapping ce = yamlRead(wr, (byte) 0, true).asMap();
            if (wr.hasNext()) {
                throw wr.err("期待 /EOF");
            }
            return ce;
        } catch (ParseException e) {
            throw wr.getExceptionDetails(e);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Invalid yaml format", e);
        }
    }

    private static ConfEntry yamlRead(YAMLLexer wr, byte flag, boolean isOut) throws ParseException {
        if(true) {
            while (wr.hasNext()) {
                System.out.print(wr.readWord());
                System.out.print(' ');
                System.out.println(wr.off);
            }
            return null;
        }

        ConfEntry cur = null;

        Word w;

        o:
        while (true) {
            w = wr.nextWord();
            switch (w.type()) {
                case left_m_bracket: { // flow
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

    private static final class YAMLLexer extends Lexer {
        @Override
        public Word readWord() throws ParseException {
            while (hasNext()) {
                int c = next();
                switch (c) {
                    case '\'':
                    case '"':
                        return readConstString((char) c);
                    case '#':
                        int ln = line;
                        while (line == ln && hasNext()) { // 单行注释
                            next();
                        }
                        break;
                    default: {
                        if (!WHITESPACE.contains(c)) {
                            retract();
                            if (SPECIAL.contains(c)) {
                                if(hasNext() && NUMBER.contains(offset(0))) { // ... may need fix
                                    switch (c) {
                                        case '+':
                                            next();
                                        case '-':
                                            return readDigit().negative();
                                    }
                                }
                                return readSpecial();
                            } else if (NUMBER.contains(c)) {
                                return readDigit();
                            } else {
                                return readAlphabet();
                            }
                        }
                    }
                }
            }
            return eof();
        }


        /**
         * @return 标识符 or 变量
         */
        protected Word readAlphabet() {
            CharList temp = this.found;
            temp.clear();

            while (hasNext()) {
                int c = next();
                if ((!SPECIAL.contains(c) || c == '-' || c == '_') && !WHITESPACE.contains(c)) {
                    temp.append((char) c);
                } else {
                    retract();
                    break;
                }
            }
            if (temp.length() == 0) {
                return eof();
            }

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
                case '-':
                    id = sub;
                    break;
                default:
                    throw err("无效字符 '" + c + '\'');
            }

            return formClip(id, String.valueOf(c));
        }

        public char next() {
            final CharSequence in = this.input;
            char c = in.charAt(index++);
            lineOffset++;
            if (c == '\r' || c == '\n') {
                final int len = in.length();
                if(c == '\r' && index < len && in.charAt(index) == '\n')
                    index++;
                line++;
                prevLineEnd = lineOffset;
                lineOffset = 0;

                int off = 0;
                while (index < len) {
                    c = in.charAt(index++);
                    if(c != ' ') {
                        if(c != '#') {
                            if(c != '\r' && c != '\n') {
                                lineOffset = off;
                                c = in.charAt(--index - 1);
                                break;
                            } else {
                                line++;
                                prevLineEnd = off;
                                off = 0;
                            }
                        } else {
                            while (index < len) {
                                c = in.charAt(index++);
                                if(c != '\r' && c != '\n')
                                    off++;
                                else
                                    break;
                            }
                            if(c == '\r' && index < len && in.charAt(index) == '\n') {
                                index++;
                            }
                            line++;
                            prevLineEnd = off;
                            off = 0;
                        }
                    } else {
                        off++;
                    }
                }

                this.lastOff = this.off;
                this.off = off;
            }
            return c;
        }

        int lastOff = -1, off;
    }
}
