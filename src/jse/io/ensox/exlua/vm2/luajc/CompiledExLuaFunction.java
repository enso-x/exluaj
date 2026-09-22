package io.ensox.exlua.vm2.luajc;

import io.ensox.exlua.vm2.ExLuaSignature;
import io.ensox.exlua.vm2.LoadState;
import io.ensox.exlua.vm2.LuaClosure;
import io.ensox.exlua.vm2.LuaError;
import io.ensox.exlua.vm2.LuaValue;
import io.ensox.exlua.vm2.Prototype;
import io.ensox.exlua.vm2.Varargs;
import io.ensox.exlua.vm2.lib.VarArgFunction;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

/** LuaJC artifact adapter retaining signatures, defaults and ExLua VM semantics. */
public class CompiledExLuaFunction extends VarArgFunction implements ExLuaSignature.Provider {
    private final Prototype prototype;
    private LuaClosure closure;

    protected CompiledExLuaFunction(String[] encoded) {
        StringBuilder data = new StringBuilder();
        for (String part : encoded) data.append(part);
        try {
            prototype = LoadState.undump(new ByteArrayInputStream(Base64.getDecoder().decode(data.toString())), "compiled.exl");
            closure = new LuaClosure(prototype, NIL);
        } catch (IOException error) { throw new LuaError(error); }
    }

    @Override public void initupvalue1(LuaValue environment) { closure = new LuaClosure(prototype, environment); }
    @Override public Varargs invoke(Varargs arguments) { return closure.invoke(arguments); }
    @Override public ExLuaSignature signature() { return closure.signature(); }
}
