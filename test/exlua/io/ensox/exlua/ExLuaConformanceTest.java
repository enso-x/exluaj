package io.ensox.exlua;

import io.ensox.exlua.vm2.Globals;
import io.ensox.exlua.vm2.LuaTable;
import io.ensox.exlua.vm2.LuaValue;
import io.ensox.exlua.vm2.lib.jse.JsePlatform;
import org.junit.Test;

/** The reference language's tests run through the public embedding API. */
public class ExLuaConformanceTest {
    @Test public void referenceLanguageSuite() {
        Globals globals = JsePlatform.debugGlobals();
        LuaTable args = new LuaTable();
        args.set(0, "test/exlua/fixtures/tests/run.lua");
        globals.set("arg", args);
        globals.loadfile(args.get(0).tojstring()).call();
    }
}
