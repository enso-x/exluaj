package io.ensox.exlua.vm2.compiler;

import io.ensox.exlua.vm2.Lua;
import io.ensox.exlua.vm2.LuaString;
import io.ensox.exlua.vm2.LuaValue;
import io.ensox.exlua.vm2.Prototype;
import io.ensox.exlua.vm2.compiler.FuncState.BlockCnt;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Native register-code lowering of ExLua syntax; no generated source or runtime eval. */
final class ExLuaLexState extends ExLuaLexer {
    private boolean expressionCall;

    ExLuaLexState(LuaC.CompileState state, InputStream input) { super(state, input); }

    @Override void new_localvar(LuaString name) {
        check_condition(dyd.n_actvar - fs.firstlocal < 200, "too many local variables (limit is 200)");
        super.new_localvar(name);
    }

    expdesc expression(int kind, int info) {
        expdesc value = new expdesc(); value.init(kind, info); return value;
    }

    expdesc string(String text) {
        expdesc value = new expdesc(); codestring(value, LuaString.valueOf(text)); return value;
    }

    void variable(LuaString name, expdesc value) {
        if (FuncState.singlevaraux(fs, name, value, 1) == VVOID) {
            FuncState.singlevaraux(fs, envn, value, 1);
            expdesc key = new expdesc(); codestring(key, name); fs.indexed(value, key);
        }
    }

    @Override void parlist() {
        FuncState function = fs;
        Prototype prototype = function.f;
        int count = 0;
        LuaString varargName = null;
        if (t.token != ')') do {
            if (t.token == TK_NAME) {
                new_localvar(str_checkname()); count++;
                if (testnext('=')) {
                    if (prototype.numdefaults == 0) function.defaultbase = function.prev.freereg;
                    fs = function.prev;
                    expdesc value = new expdesc(); expr(value); fs.exp2nextreg(value);
                    fs = function;
                    prototype.numdefaults++;
                } else check_condition(prototype.numdefaults == 0,
                    "parameter without default follows default parameter");
            } else if (testnext(TK_DOTS)) {
                prototype.is_vararg = 1;
                if (t.token == TK_NAME) varargName = str_checkname();
                break;
            } else syntaxerror("<name> or '...' expected");
        } while (testnext(','));
        adjustlocalvars(count);
        prototype.numparams = fs.nactvar;
        fs.reserveregs(fs.nactvar);
        if (varargName != null) {
            int register = fs.freereg;
            new_localvar(varargName); adjustlocalvars(1); fs.reserveregs(1);
            fs.codeABC(OP_NEWTABLE, register, 0, 0);
            fs.reserveregs(1);
            fs.codeABC(OP_VARARG, register + 1, 0, 0);
            fs.codeABC(OP_SETLIST, register, 0, 1);
            fs.freereg = (short) (register + 1);
        }
    }

    @Override void codeclosure(expdesc value) {
        FuncState child = fs;
        FuncState parent = child.prev;
        // Default expressions can append sibling prototypes after this function.
        int index = 0;
        while (parent.f.p[index] != child.f) index++;
        value.init(VRELOCABLE, parent.codeABx(OP_CLOSURE, 0, index));
        parent.exp2nextreg(value);
        if (child.f.numdefaults != 0) {
            parent.codeABC(OP_SETDEFAULTS, value.u.info, child.defaultbase, child.f.numdefaults);
            parent.codeABC(OP_MOVE, child.defaultbase, value.u.info, 0);
            parent.freereg = (short) (child.defaultbase + 1);
            value.u.info = child.defaultbase;
        }
    }

    @Override void localfunc() {
        int slot = fs.nactvar;
        new_localvar(str_checkname()); adjustlocalvars(1);
        fs.nil(fs.freereg, 1); fs.reserveregs(1);
        expdesc value = new expdesc(); body(value, false, linenumber);
        fs.storevar(expression(VLOCAL, slot), value);
        fs.getlocvar(slot).startpc = fs.pc;
    }

    private int arguments(int base, int line) {
        List<String> names = new ArrayList<String>();
        int positional = fs.freereg - base - 1;
        boolean multiple = false;
        checknext('(');
        if (t.token != ')') do {
            expdesc value = new expdesc();
            if (t.token == TK_NAME && peek() == '=') {
                String name = t.seminfo.ts.tojstring();
                check_condition(!names.contains(name), "duplicate named argument");
                names.add(name); next(); next();
                expr(value); fs.exp2nextreg(value);
            } else {
                check_condition(names.isEmpty(), "positional argument follows named argument");
                expr(value);
                if (t.token != ',' && hasmultret(value.k)) { fs.setmultret(value); multiple = true; }
                else fs.exp2nextreg(value);
                positional++;
            }
        } while (testnext(','));
        check(')');
        if (!names.isEmpty()) {
            fs.exp2nextreg(string(String.join("\u0000", names)));
            fs.codeABC(OP_NAMEDARGS, base, positional, names.size());
            multiple = true;
        }
        check_match(')', '(', line);
        return multiple ? LUA_MULTRET : positional;
    }

    @Override void funcargs(expdesc function, int line) {
        if (t.token == '(') {
            int base = function.u.info;
            int parameters = arguments(base, line);
            function.init(VCALL, fs.codeABC(OP_CALL, base, parameters + 1, 2));
            fs.freereg = (short) (base + 1); fs.fixline(line);
        } else if (t.token == TEMPLATE || t.token == TEMPLATE_END) {
            int base = function.u.info;
            expdesc value = new expdesc(); template(value); fs.exp2nextreg(value);
            function.init(VCALL, fs.codeABC(OP_CALL, base, fs.freereg - base, 2));
            fs.freereg = (short) (base + 1); fs.fixline(line);
        } else super.funcargs(function, line);
    }

    private void vararg(expdesc value) {
        check_condition(fs.f.is_vararg != 0, "cannot use '...' outside a vararg function");
        value.init(VVARARG, fs.codeABC(OP_VARARG, 0, 1, 0));
    }

    @Override void primaryexp(expdesc value) {
        if (t.token != '(') { super.primaryexp(value); return; }
        int line = linenumber;
        next();
        if (t.token == ')' || (t.token == TK_NAME && (peek() == ',' || peek() == '='))
            || (t.token == TK_DOTS && peek() == TK_NAME)) {
            lambda(value, null, false, line); return;
        }
        if ((t.token == TK_NAME || t.token == TK_DOTS) && peek() == ')') {
            boolean dots = t.token == TK_DOTS;
            LuaString name = dots ? null : t.seminfo.ts;
            next(); next();
            if (t.token == ARROW) lambda(value, name, dots, line);
            else {
                if (dots) vararg(value); else variable(name, value);
                fs.dischargevars(value);
            }
            return;
        }
        expr(value); check_match(')', '(', line); fs.dischargevars(value);
    }

    private void lambda(expdesc value, LuaString parameter, boolean dots, int line) {
        FuncState function = new FuncState();
        function.f = addprototype(); function.f.linedefined = line;
        open_func(function, new BlockCnt());
        if (parameter != null) {
            new_localvar(parameter); adjustlocalvars(1); fs.reserveregs(1); fs.f.numparams = 1;
        } else if (dots) fs.f.is_vararg = 1;
        else { parlist(); check_match(')', '(', line); }
        checknext(ARROW);
        if (testnext(TK_DO)) { statlist(); check_match(TK_END, TK_DO, line); }
        else {
            expdesc result = new expdesc(); expr(result);
            fs.ret(fs.exp2anyreg(result), 1);
        }
        function.f.lastlinedefined = linenumber;
        codeclosure(value); close_func();
    }

    /** Emit EQ/JMP without consuming the receiver's register or calling __eq. */
    private int nilJump(int register, boolean whenNil) {
        int saved = fs.freereg;
        expdesc nil = expression(VNIL, 0);
        int key = fs.exp2RK(nil);
        fs.codeABC(OP_EQ, whenNil ? 1 : 0, register, key);
        int jump = fs.jump();
        fs.freereg = (short) saved;
        return jump;
    }

    @Override void suffixedexp(expdesc value) {
        int line = linenumber;
        primaryexp(value);
        expressionCall = false;
        for (;;) {
            switch (t.token) {
                case '.': fieldsel(value); expressionCall = false; break;
                case '[':
                    expdesc index = new expdesc(); fs.exp2anyregup(value); yindex(index); fs.indexed(value, index);
                    expressionCall = false; break;
                case ':':
                    next(); expdesc key = new expdesc(); checkname(key); fs.self(value, key); funcargs(value, line);
                    expressionCall = true; break;
                case OPTIONAL:
                    next(); fs.exp2nextreg(value);
                    int register = value.u.info;
                    int jump = nilJump(register, true);
                    if (t.token == TK_NAME) {
                        expdesc field = new expdesc(); checkname(field); fs.indexed(value, field);
                        fs.exp2reg(value, register); expressionCall = false;
                    } else if (t.token == '(') {
                        funcargs(value, line); expressionCall = true;
                    } else syntaxerror("<name> or function arguments expected");
                    fs.patchtohere(jump); value.init(VNONRELOC, register);
                    fs.freereg = (short) (register + 1); break;
                case '(': case TK_STRING: case '{': case TEMPLATE: case TEMPLATE_END:
                    fs.exp2nextreg(value); funcargs(value, line); expressionCall = true; break;
                default: return;
            }
        }
    }

    @Override void simpleexp(expdesc value) {
        if (t.token == TK_NAME && peek() == ARROW) {
            LuaString name = t.seminfo.ts; int line = linenumber; next();
            lambda(value, name, false, line); expressionCall = false;
        } else if (t.token == TEMPLATE || t.token == TEMPLATE_END) {
            template(value); expressionCall = false;
        } else {
            boolean suffixed = t.token == '(' || t.token == TK_NAME;
            super.simpleexp(value);
            if (!suffixed) expressionCall = false;
        }
    }

    private void template(expdesc value) {
        int base = fs.freereg;
        do {
            boolean last = t.token == TEMPLATE_END;
            codestring(value, t.seminfo.ts); fs.exp2nextreg(value); next();
            if (last) break;
            int call = fs.freereg;
            expdesc convert = new expdesc(); variable(LuaString.valueOf("tostring"), convert); fs.exp2nextreg(convert);
            expdesc hole = new expdesc(); expr(hole); fs.exp2nextreg(hole);
            fs.codeABC(OP_CALL, call, 2, 2); fs.freereg = (short) (call + 1);
            checknext('}');
            check_condition(t.token == TEMPLATE || t.token == TEMPLATE_END, "template segment expected");
        } while (true);
        if (fs.freereg - base > 1) fs.codeABC(OP_CONCAT, base, base, fs.freereg - 1);
        fs.freereg = (short) (base + 1); value.init(VNONRELOC, base);
    }

    private void pipeline(expdesc value) {
        int line = linenumber;
        next();
        if (testnext(':')) {
            expdesc key = new expdesc(); checkname(key); fs.self(value, key); funcargs(value, line);
        } else {
            fs.exp2nextreg(value);
            int first = value.u.info;
            expdesc callee = new expdesc(); primaryexp(callee);
            while (t.token == '.' || t.token == '[') {
                if (t.token == '.') fieldsel(callee);
                else { expdesc key = new expdesc(); fs.exp2anyregup(callee); yindex(key); fs.indexed(callee, key); }
            }
            fs.exp2nextreg(callee);
            int base = callee.u.info;
            fs.reserveregs(1); fs.codeABC(OP_MOVE, base + 1, first, 0);
            int count = t.token == '(' ? arguments(base, line) : 1;
            fs.codeABC(OP_CALL, base, count + 1, 2);
            fs.codeABC(OP_MOVE, first, base, 0);
            fs.freereg = (short) (first + 1); value.init(VNONRELOC, first);
        }
        expressionCall = true;
    }

    @Override int subexpr(expdesc value, int limit) {
        enterlevel();
        int unary = getunopr(t.token);
        if (unary != OPR_NOUNOPR) {
            int line = linenumber; next(); subexpr(value, UNARY_PRIORITY); fs.prefix(unary, value, line);
            expressionCall = false;
        } else simpleexp(value);
        for (;;) {
            int op = getbinopr(t.token);
            if (t.token == PIPE && limit < 1) { pipeline(value); continue; }
            if (t.token == COALESCE && limit < 1) {
                next(); fs.exp2nextreg(value);
                int register = value.u.info;
                int jump = nilJump(register, false);
                expdesc right = new expdesc(); subexpr(right, 1);
                fs.exp2reg(right, register); fs.freereg = (short) (register + 1);
                fs.patchtohere(jump); value.init(VNONRELOC, register); expressionCall = false;
                continue;
            }
            if (op == OPR_NOBINOPR || priority[op].left <= limit) break;
            int line = linenumber; next(); fs.infix(op, value);
            expdesc right = new expdesc(); subexpr(right, priority[op].right);
            fs.posfix(op, value, right, line); expressionCall = false;
        }
        leavelevel(); return getbinopr(t.token);
    }

    @Override void exprstat() {
        LHS_assign target = new LHS_assign();
        expr(target.v);
        if (t.token == '=' || t.token == ',') assignment(target, 1);
        else if (t.token >= ADD_EQ && t.token <= DIV_EQ) {
            check_condition(vkisvar(target.v.k), "invalid compound assignment target");
            int operation = t.token - ADD_EQ;
            int line = linenumber; next();
            // Loading an indexed lvalue must leave its table/key pinned until the store.
            expdesc old = new expdesc(); old.setvalue(target.v);
            if (old.k == VINDEXED) {
                old.init(VRELOCABLE, fs.codeABC(target.v.u.ind_vt == VUPVAL ? OP_GETTABUP : OP_GETTABLE,
                    0, target.v.u.ind_t, target.v.u.ind_idx));
            }
            fs.infix(operation, old);
            expdesc right = new expdesc(); expr(right); fs.posfix(operation, old, right, line);
            fs.storevar(target.v, old);
        } else {
            check_condition(expressionCall, "syntax error");
            if (target.v.k == VCALL) SETARG_C(fs.getcodePtr(target.v), 1);
        }
    }

    @Override void localstat() {
        if (t.token == '{' || t.token == '[') declarations().pattern();
        else super.localstat();
    }

    @Override void statement() {
        if (t.token == TK_LOCAL) {
            enterlevel(); next();
            if (word("class") && peek() == TK_NAME) declarations().localClass();
            else if (testnext(TK_FUNCTION)) localfunc();
            else localstat();
            fs.freereg = fs.nactvar; leavelevel();
        } else if (t.token == '@' || word("class") && peek() == TK_NAME) {
            enterlevel();
            declarations().declaration();
            fs.freereg = fs.nactvar;
            leavelevel();
        } else super.statement();
    }

    boolean word(String value) { return t.token == TK_NAME && value.equals(t.seminfo.ts.tojstring()); }
    private ExLuaDeclarations declarations() { return new ExLuaDeclarations(this); }
}
