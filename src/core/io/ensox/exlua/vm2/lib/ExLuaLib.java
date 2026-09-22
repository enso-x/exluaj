package io.ensox.exlua.vm2.lib;

import io.ensox.exlua.vm2.ExLuaSignature;
import io.ensox.exlua.vm2.LuaTable;
import io.ensox.exlua.vm2.LuaValue;
import io.ensox.exlua.vm2.Varargs;

/** Table-based classes and forwarding decorators, also available in custom sandboxes. */
public final class ExLuaLib {
    private ExLuaLib() {}

    static void install(LuaValue environment) {
        LuaTable exlua = new LuaTable();
        exlua.set("class", new TwoArgFunction() {
            public LuaValue call(LuaValue members, LuaValue parent) {
                LuaTable type = members.checktable();
                if (!parent.isnil()) {
                    parent.checktable();
                    LuaTable meta = new LuaTable();
                    meta.set("__index", parent);
                    type.setmetatable(meta);
                }
                type.set("__index", type);
                type.set("super", parent);
                LuaValue initializer = type.get("__init");
                if (!initializer.isnil() && !initializer.isfunction())
                    throw new io.ensox.exlua.vm2.LuaError("constructor must be a function");
                type.set("new", new Constructor(type, initializer));
                return type;
            }
        });
        environment.set("exlua", exlua);
        environment.set("wraps", new TwoArgFunction() {
            public LuaValue call(LuaValue original, LuaValue wrapper) {
                original.checkfunction();
                wrapper.checkfunction();
                return new Forwarder(ExLuaSignature.of(original), wrapper);
            }
        });
    }

    private static final class Constructor extends VarArgFunction implements ExLuaSignature.Provider {
        private final LuaTable type;
        private final LuaValue initializer;
        Constructor(LuaTable type, LuaValue initializer) {
            this.type = type;
            this.initializer = initializer;
        }
        public ExLuaSignature signature() {
            ExLuaSignature signature = ExLuaSignature.of(initializer);
            return new ExLuaSignature(signature.source, signature.skip + 1);
        }
        public Varargs invoke(Varargs args) {
            LuaTable instance = new LuaTable();
            instance.setmetatable(type);
            if (!initializer.isnil()) initializer.invoke(varargsOf(instance, args));
            return instance;
        }
    }

    private static final class Forwarder extends VarArgFunction implements ExLuaSignature.Provider {
        private final ExLuaSignature signature;
        private final LuaValue wrapper;
        Forwarder(ExLuaSignature signature, LuaValue wrapper) {
            this.signature = signature;
            this.wrapper = wrapper;
        }
        public ExLuaSignature signature() { return signature; }
        public Varargs invoke(Varargs args) { return wrapper.invoke(args); }
    }
}
