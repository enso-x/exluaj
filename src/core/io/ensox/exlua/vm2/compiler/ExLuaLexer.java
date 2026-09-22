package io.ensox.exlua.vm2.compiler;

import io.ensox.exlua.vm2.LuaString;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/** ExLua tokens layered over the unchanged Lua 5.2 scanner. */
class ExLuaLexer extends LexState {
    static final int ARROW = 300, COALESCE = 301, OPTIONAL = 302, PIPE = 303,
        ADD_EQ = 304, SUB_EQ = 305, MUL_EQ = 306, DIV_EQ = 307,
        TEMPLATE = 308, TEMPLATE_END = 309;
    private static final String[] TOKENS = {"=>", "??", "?.", "|>", "+=", "-=", "*=", "/=",
        "<template segment>", "<template end>"};
    private final Deque<Template> templates = new ArrayDeque<Template>();
    private static final class Template {
        final int quote;
        int braces;
        boolean text = true;
        Template(int quote) { this.quote = quote; }
    }

    ExLuaLexer(LuaC.CompileState state, InputStream input) { super(state, input); }

    @Override String token2str(int token) {
        return token >= ARROW && token <= TEMPLATE_END ? TOKENS[token - ARROW] : super.token2str(token);
    }

    int peek() {
        if (lookahead.token == TK_EOS) lookahead();
        return lookahead.token;
    }

    @Override int llex(SemInfo info) {
        if (!templates.isEmpty() && templates.peek().text)
            return readText(templates.peek().quote, info, true, false);
        int token = super.llex(info);
        int combined = 0;
        if (token == '=' && current == '>') combined = ARROW;
        else if (token == '?' && current == '?') combined = COALESCE;
        else if (token == '?' && current == '.') combined = OPTIONAL;
        else if (token == '|' && current == '>') combined = PIPE;
        else if (current == '=') {
            if (token == '+') combined = ADD_EQ;
            else if (token == '-') combined = SUB_EQ;
            else if (token == '*') combined = MUL_EQ;
            else if (token == '/') combined = DIV_EQ;
        }
        if (combined != 0) { nextChar(); return combined; }
        if (token == TK_NAME && "f".equals(info.ts.tojstring()) && (current == '"' || current == '\'')) {
            check_condition(templates.size() < 32, "too many nested template strings (limit is 32)");
            templates.push(new Template(current));
            return readText(current, info, true, true);
        }
        if (!templates.isEmpty()) {
            Template template = templates.peek();
            if (token == '{') template.braces++;
            else if (token == '}') {
                if (template.braces == 0) template.text = true;
                else template.braces--;
            } else if (token == TK_EOS) syntaxerror("unfinished template expression");
        }
        return token;
    }

    @Override void read_string(int delimiter, SemInfo info) {
        readText(delimiter, info, false, true);
    }

    private int readText(int quote, SemInfo info, boolean template, boolean first) {
        nbuff = 0;
        if (first) nextChar();
        while (current != quote) {
            if (current < 0 || current == '\r' || current == '\n') syntaxerror("unfinished string");
            if (template && (current == '{' || current == '}')) {
                int brace = current;
                nextChar();
                if (current == brace) { save_and_next(); continue; }
                check_condition(brace == '{', "single '}' in template string");
                info.ts = L.newTString(LuaString.valueOf(buff, 0, nbuff));
                templates.peek().text = false;
                return TEMPLATE;
            }
            if (current != '\\') { save_and_next(); continue; }
            nextChar();
            int c = current;
            switch (c) {
                case 'a': save(7); nextChar(); break;
                case 'b': save(8); nextChar(); break;
                case 'f': save(12); nextChar(); break;
                case 'n': save(10); nextChar(); break;
                case 'r': save(13); nextChar(); break;
                case 't': save(9); nextChar(); break;
                case 'v': save(11); nextChar(); break;
                case '\\': case '\'': case '"': save_and_next(); break;
                case '\r': case '\n': inclinenumber(); save('\n'); break;
                case 'z':
                    nextChar();
                    while (current >= 0 && Character.isWhitespace((char) current)) {
                        if (current == '\r' || current == '\n') inclinenumber(); else nextChar();
                    }
                    break;
                case 'x':
                    nextChar();
                    int value = hexDigit(); nextChar();
                    value = value * 16 + hexDigit(); nextChar();
                    save(value);
                    break;
                case 'u':
                    nextChar();
                    check_condition(current == '{', "missing '{' in Unicode escape");
                    nextChar();
                    long codepoint = 0;
                    int digits = 0;
                    while (current != '}') {
                        codepoint = codepoint * 16 + hexDigit();
                        check_condition(codepoint <= 0x10ffff, "UTF-8 value too large");
                        digits++;
                        nextChar();
                    }
                    check_condition(digits > 0, "hexadecimal digit expected");
                    nextChar();
                    byte[] bytes = new String(Character.toChars((int) codepoint)).getBytes(StandardCharsets.UTF_8);
                    for (byte b : bytes) save(b & 255);
                    break;
                default:
                    check_condition(c >= '0' && c <= '9', "invalid escape sequence");
                    int decimal = 0;
                    for (int i = 0; i < 3 && current >= '0' && current <= '9'; i++) {
                        decimal = decimal * 10 + current - '0'; nextChar();
                    }
                    check_condition(decimal <= 255, "decimal escape too large");
                    save(decimal);
            }
        }
        nextChar();
        info.ts = L.newTString(LuaString.valueOf(buff, 0, nbuff));
        if (template) { templates.pop(); return TEMPLATE_END; }
        return TK_STRING;
    }

    private int hexDigit() {
        int digit = Character.digit((char) current, 16);
        check_condition(digit >= 0, "hexadecimal digit expected");
        return digit;
    }
}
