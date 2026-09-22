package io.ensox.exlua.vm2;

import java.util.Arrays;

/** Runtime signatures retain closure defaults without a global registry. */
public final class ExLuaSignature {
    public interface Provider {
        ExLuaSignature signature();
    }

    public final LuaClosure source;
    public final int skip;

    public ExLuaSignature(LuaClosure source, int skip) {
        if (skip < 0 || skip > source.p.numparams) throw new LuaError("invalid signature offset");
        this.source = source;
        this.skip = skip;
    }

    public static ExLuaSignature of(LuaValue function) {
        if (!(function instanceof Provider)) throw new LuaError("named arguments require a Lua function with an ExLua signature");
        return ((Provider) function).signature();
    }

    public static LuaValue defaultValue(LuaClosure function, int parameter) {
        int index = parameter - (function.p.numparams - function.p.numdefaults);
        if (index < 0) return LuaValue.NIL;
        if (function.defaultValues == null)
            throw new LuaError("default argument values unavailable; reload the enclosing chunk");
        return function.defaultValues[index];
    }

    public static Varargs bind(LuaValue function, LuaValue[] stack, int start,
                               int positional, int named, String descriptor) {
        ExLuaSignature signature = of(function);
        Prototype prototype = signature.source.p;
        int parameters = prototype.numparams - signature.skip;
        LuaValue[] values = new LuaValue[Math.max(parameters, positional)];
        boolean[] supplied = new boolean[values.length];
        Arrays.fill(values, LuaValue.NIL);
        for (int i = 0; i < positional; i++) {
            values[i] = stack[start + i];
            supplied[i] = true;
        }
        String[] names = descriptor.split("\u0000", -1);
        if (names.length != named) throw new LuaError("invalid named argument descriptor");
        for (int i = 0; i < named; i++) {
            int parameter = -1;
            for (int j = signature.skip; j < prototype.parameterNames.length; j++) {
                if (prototype.parameterNames[j] != null && names[i].equals(prototype.parameterNames[j].tojstring())) {
                    if (parameter >= 0) throw new LuaError("ambiguous parameter name '" + names[i] + "'");
                    parameter = j - signature.skip;
                }
            }
            if (parameter < 0) throw new LuaError("unknown named argument '" + names[i] + "'");
            if (supplied[parameter]) throw new LuaError("multiple values for argument '" + names[i] + "'");
            supplied[parameter] = true;
            values[parameter] = stack[start + positional + i];
        }
        for (int i = 0; i < parameters; i++) {
            if (!supplied[i]) values[i] = defaultValue(signature.source, i + signature.skip);
        }
        return LuaValue.varargsOf(values);
    }
}
