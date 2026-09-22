package io.ensox.exlua.vm2.compiler;

import io.ensox.exlua.vm2.LuaString;
import io.ensox.exlua.vm2.LuaValue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static io.ensox.exlua.vm2.Lua.*;
import static io.ensox.exlua.vm2.compiler.LexState.*;

/** Declaration lowering shares the parser's scopes and register allocator. */
final class ExLuaDeclarations {
    private final ExLuaLexState parser;
    ExLuaDeclarations(ExLuaLexState parser) { this.parser = parser; }

    private static final class Decorators {
        int base, count;
    }

    private Decorators readDecorators() {
        Decorators result = new Decorators(); result.base = parser.fs.freereg;
        while (parser.testnext('@')) {
            expdesc value = new expdesc(); parser.expr(value); parser.fs.exp2nextreg(value); result.count++;
        }
        return result;
    }

    private void apply(Decorators decorators, expdesc value) {
        if (decorators.count == 0) return;
        FuncState fs = parser.fs;
        fs.exp2nextreg(value);
        int target = value.u.info;
        for (int i = decorators.count - 1; i >= 0; i--) {
            int base = fs.freereg; fs.reserveregs(2);
            fs.codeABC(OP_MOVE, base, decorators.base + i, 0);
            fs.codeABC(OP_MOVE, base + 1, target, 0);
            fs.codeABC(OP_CALL, base, 2, 2);
            fs.codeABC(OP_MOVE, target, base, 0);
            fs.freereg = (short) base;
        }
    }

    private expdesc local(LuaString name, Decorators decorators) {
        FuncState fs = parser.fs;
        int slot = fs.nactvar;
        fs.reserveregs(1);
        for (int i = decorators.count - 1; i >= 0; i--)
            fs.codeABC(OP_MOVE, decorators.base + i + 1, decorators.base + i, 0);
        decorators.base++;
        fs.nil(slot, 1);
        parser.new_localvar(name); parser.adjustlocalvars(1);
        return parser.expression(VLOCAL, slot);
    }

    void localClass() { classDeclaration(true, readDecorators(), parser.linenumber); }

    void declaration() {
        int line = parser.linenumber;
        Decorators decorators = readDecorators();
        boolean local = parser.testnext(TK_LOCAL);
        if (parser.word("class")) { classDeclaration(local, decorators, line); return; }
        parser.checknext(TK_FUNCTION);
        expdesc target = new expdesc();
        boolean method = false;
        if (local) target = local(parser.str_checkname(), decorators);
        else method = parser.funcname(target);
        expdesc value = new expdesc(); parser.body(value, method, line);
        apply(decorators, value); parser.fs.storevar(target, value); parser.fs.fixline(line);
    }

    private void classDeclaration(boolean local, Decorators decorators, int line) {
        FuncState fs = parser.fs;
        parser.next();
        LuaString name = parser.str_checkname();
        expdesc target = new expdesc();
        if (local) target = local(name, decorators); else parser.variable(name, target);
        expdesc parent = parser.expression(VNIL, 0);
        if (parser.word("extends")) { parser.next(); parser.expr(parent); }
        fs.exp2nextreg(parent);
        int parentRegister = parent.u.info;
        int table = fs.freereg; fs.reserveregs(1); fs.codeABC(OP_NEWTABLE, table, 0, 0);
        // The class binding is visible to its methods and definition-time defaults.
        int copy = fs.freereg; fs.reserveregs(1); fs.codeABC(OP_MOVE, copy, table, 0);
        fs.storevar(target, parser.expression(VNONRELOC, copy));
        fs.freereg = (short) (table + 1);
        int saved = fs.freereg;
        Set<String> members = new HashSet<String>();
        parser.testnext(TK_DO);
        while (parser.t.token != TK_END) {
            if (parser.testnext(';')) continue;
            int memberLine = parser.linenumber;
            Decorators memberDecorators = readDecorators();
            boolean isStatic = parser.word("static");
            if (isStatic) parser.next();
            boolean constructor = !isStatic && parser.word("constructor") && parser.peek() == '(';
            String member;
            expdesc value = new expdesc();
            if (constructor) {
                parser.next(); member = "__init"; parser.body(value, true, memberLine);
            } else if (parser.testnext(TK_FUNCTION)) {
                member = parser.str_checkname().tojstring(); parser.body(value, !isStatic, memberLine);
            } else {
                parser.check_condition(isStatic && memberDecorators.count == 0,
                    "constructor, function, or static member expected");
                member = parser.str_checkname().tojstring(); parser.checknext('='); parser.expr(value);
            }
            parser.check_condition(!member.equals("new") && !member.equals("super") && !member.equals("__index")
                && (constructor || !member.equals("__init")), "reserved class member name");
            parser.check_condition(members.add(member), "duplicate class member");
            parser.check_condition(members.size() <= 200, "too many class members (limit is 200)");
            apply(memberDecorators, value); fs.exp2nextreg(value);
            expdesc entry = parser.expression(VNONRELOC, table);
            fs.indexed(entry, parser.string(member));
            if (value.u.info + 1 < fs.freereg) {
                int temp = fs.freereg; fs.reserveregs(1); fs.codeABC(OP_MOVE, temp, value.u.info, 0); value.u.info = temp;
            }
            fs.storevar(entry, value); fs.freereg = (short) saved;
        }
        parser.check_match(TK_END, TK_NAME, line);
        expdesc factory = new expdesc(); parser.variable(LuaString.valueOf("exlua"), factory);
        fs.exp2anyregup(factory); fs.indexed(factory, parser.string("class")); fs.exp2nextreg(factory);
        int call = factory.u.info; fs.reserveregs(2);
        fs.codeABC(OP_MOVE, call + 1, table, 0); fs.codeABC(OP_MOVE, call + 2, parentRegister, 0);
        fs.codeABC(OP_CALL, call, 3, 2); fs.freereg = (short) (call + 1);
        factory.init(VNONRELOC, call); apply(decorators, factory); fs.storevar(target, factory); fs.fixline(line);
    }

    private static final class Pattern {
        final List<Part> parts = new ArrayList<Part>();
        final Set<String> names = new HashSet<String>();
        int count;
    }

    private static final class Part {
        LuaString key, name;
        int index, binding;
        List<Part> children;
    }

    private List<Part> readPattern(Pattern pattern) {
        parser.enterlevel();
        int opening = parser.t.token, closing = opening == '{' ? '}' : ']';
        parser.next();
        List<Part> parts = new ArrayList<Part>();
        while (parser.t.token != closing) {
            parser.check_condition(++pattern.count <= 200, "too many destructuring entries (limit is 200)");
            Part part = new Part(); part.index = parts.size() + 1; parts.add(part);
            if (opening == '{') {
                part.key = parser.str_checkname();
                if (!parser.testnext(':')) part.name = part.key;
            }
            if (part.name == null) {
                if (parser.t.token == '{' || parser.t.token == '[') part.children = readPattern(pattern);
                else part.name = parser.str_checkname();
            }
            if (part.name != null) {
                part.binding = pattern.names.size();
                parser.check_condition(pattern.names.add(part.name.tojstring()), "duplicate destructuring binding");
                parser.new_localvar(part.name);
            }
            if (!parser.testnext(',')) break;
        }
        parser.checknext(closing); parser.leavelevel(); return parts;
    }

    private void extract(List<Part> parts, int source, int base) {
        FuncState fs = parser.fs;
        for (Part part : parts) {
            int saved = fs.freereg;
            expdesc key = new expdesc();
            if (part.key != null) parser.codestring(key, part.key);
            else { key.init(VKNUM, 0); key.u.setNval(LuaValue.valueOf(part.index)); }
            int index = fs.exp2RK(key);
            int value = fs.freereg; fs.reserveregs(1);
            fs.codeABC(OP_GETTABLE, value, source, index);
            if (part.name != null) fs.codeABC(OP_MOVE, base + part.binding, value, 0);
            else extract(part.children, value, base);
            fs.freereg = (short) saved;
        }
    }

    void pattern() {
        FuncState fs = parser.fs;
        int base = fs.freereg;
        Pattern pattern = new Pattern();
        List<Part> parts = readPattern(pattern);
        parser.checknext('='); fs.reserveregs(pattern.names.size());
        if (!pattern.names.isEmpty()) fs.nil(base, pattern.names.size());
        expdesc source = new expdesc(); parser.expr(source); fs.exp2nextreg(source);
        extract(parts, source.u.info, base); parser.adjustlocalvars(pattern.names.size());
    }
}
