package io.ensox.exlua;

import io.ensox.exlua.vm2.Globals;
import io.ensox.exlua.vm2.LuaFunction;
import io.ensox.exlua.vm2.LuaClosure;
import io.ensox.exlua.vm2.Print;
import io.ensox.exlua.vm2.lib.jse.JsePlatform;
import io.ensox.exlua.vm2.luajc.LuaJC;
import io.ensox.exlua.vm2.script.LuajContext;
import org.junit.Test;
import java.io.StringReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.Assert.*;

public class ApiCompatibilityTest {
    @Test public void publicAndProtectedApiMatchesLuaJ301AfterPackageRename() throws Exception {
        try (JarFile jar = new JarFile(new java.io.File(org.luaj.vm2.Globals.class
                .getProtectionDomain().getCodeSource().getLocation().toURI()))) {
            Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            int checked = 0;
            while (entries.hasMoreElements()) {
                String entry = entries.nextElement().getName();
                if (!entry.startsWith("org/luaj/") || !entry.endsWith(".class")) continue;
                String name = entry.substring(0, entry.length() - 6).replace('/', '.');
                Class<?> before = Class.forName(name, false, getClass().getClassLoader());
                if (!visible(before.getModifiers())) continue;
                Class<?> after = Class.forName(rename(name), false, getClass().getClassLoader());
                Set<String> actual = signatures(after);
                for (String signature : signatures(before))
                    assertTrue("Missing API: " + signature, actual.contains(rename(signature)));
                checked++;
            }
            assertTrue("Expected the full published API", checked > 100);
        }
    }

    private static boolean visible(int modifiers) { return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers); }
    private static String rename(String value) { return value.replace("org.luaj", "io.ensox.exlua"); }
    private static Set<String> signatures(Class<?> type) {
        Set<String> result = new HashSet<String>();
        for (Method method : type.getDeclaredMethods()) if (visible(method.getModifiers())) result.add(method.toGenericString());
        for (Field field : type.getDeclaredFields()) if (visible(field.getModifiers())) result.add(field.toGenericString());
        for (Constructor<?> constructor : type.getDeclaredConstructors())
            if (visible(constructor.getModifiers())) result.add(constructor.toGenericString());
        return result;
    }

    @Test public void luaJcLoadingAndExportRetainExLuaSemantics() throws Exception {
        String source = "local f = (a, b = 2) => a + b; return f(b = 3, a = 39)";
        Globals globals = JsePlatform.standardGlobals();
        LuaJC.install(globals);
        assertEquals(42, globals.load(source, "jit.exl").call().toint());
        Hashtable classes = LuaJC.instance.compileAll(new StringReader(source), "compiled.exl", "compiled.exl", globals, true);
        String name = (String) classes.keys().nextElement();
        byte[] bytes = (byte[]) classes.get(name);
        Class<?> exported = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(name, bytes, 0, bytes.length); }
        }.define();
        LuaFunction function = (LuaFunction) exported.getDeclaredConstructor().newInstance();
        function.initupvalue1(globals);
        assertEquals(42, function.call().toint());
        assertNotNull(exported.getMethod("main", String[].class));
    }

    @Test public void disassemblerUnderstandsExtendedInstructions() throws Exception {
        LuaClosure closure = (LuaClosure) JsePlatform.standardGlobals().load(
            "local f = (a = 1) => a; return f(a = 42)", "listing.exl");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(output, true, "UTF-8")) {
            for (int pc = 0; pc < closure.p.code.length; pc++)
                Print.printOpCode(stream, closure.p, pc);
        }
        String listing = output.toString("UTF-8");
        assertTrue(listing, listing.contains("SETDEFAULTS"));
        assertTrue(listing, listing.contains("NAMEDARGS"));
    }

    @Test public void legacySystemPropertiesRemainValidWithExplicitOverride() {
        String old = System.getProperty("org.luaj.debug");
        String current = System.getProperty("io.ensox.exlua.debug");
        try {
            System.setProperty("org.luaj.debug", "true");
            System.clearProperty("io.ensox.exlua.debug");
            assertTrue(new LuajContext().globals.get("debug").istable());
            System.setProperty("io.ensox.exlua.debug", "false");
            assertTrue(new LuajContext().globals.get("debug").isnil());
        } finally {
            if (old == null) System.clearProperty("org.luaj.debug");
            else System.setProperty("org.luaj.debug", old);
            if (current == null) System.clearProperty("io.ensox.exlua.debug");
            else System.setProperty("io.ensox.exlua.debug", current);
        }
    }
}
