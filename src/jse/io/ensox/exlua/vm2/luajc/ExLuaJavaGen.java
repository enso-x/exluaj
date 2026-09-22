package io.ensox.exlua.vm2.luajc;

import io.ensox.exlua.vm2.Lua;
import io.ensox.exlua.vm2.LuaError;
import io.ensox.exlua.vm2.Prototype;
import io.ensox.exlua.vm2.compiler.DumpState;
import org.apache.bcel.Constants;
import org.apache.bcel.generic.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/** Generates LuaJC classes backed by serialized ExLuaJ prototypes. */
final class ExLuaJavaGen {
    private ExLuaJavaGen() {}

    static boolean requiresVm(Prototype prototype) {
        if (prototype.numparams != 0 || prototype.p.length != 0 || prototype.linedefined != 0) return true;
        for (int instruction : prototype.code) if (Lua.GET_OPCODE(instruction) > Lua.OP_EXTRAARG) return true;
        return false;
    }

    static byte[] generate(Prototype prototype, String name, String filename, boolean main) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try { DumpState.dump(prototype, output, false); }
        catch (IOException error) { throw new LuaError(error); }
        String data = Base64.getEncoder().encodeToString(output.toByteArray());
        String parent = CompiledExLuaFunction.class.getName();
        ClassGen generated = new ClassGen(name, parent, filename, Constants.ACC_PUBLIC | Constants.ACC_SUPER, null);
        ConstantPoolGen pool = generated.getConstantPool();
        InstructionFactory factory = new InstructionFactory(generated);
        InstructionList code = new InstructionList();
        code.append(InstructionFactory.createLoad(Type.OBJECT, 0));
        int count = (data.length() + 29999) / 30000;
        code.append(new PUSH(pool, count));
        code.append(factory.createNewArray(Type.STRING, (short) 1));
        for (int i = 0; i < count; i++) {
            code.append(InstructionConstants.DUP); code.append(new PUSH(pool, i));
            code.append(new PUSH(pool, data.substring(i * 30000, Math.min(data.length(), (i + 1) * 30000))));
            code.append(InstructionConstants.AASTORE);
        }
        ArrayType strings = new ArrayType(Type.STRING, 1);
        code.append(factory.createInvoke(parent, "<init>", Type.VOID, new Type[]{strings}, Constants.INVOKESPECIAL));
        code.append(InstructionConstants.RETURN);
        method(generated, code, "<init>", Constants.ACC_PUBLIC, Type.NO_ARGS);
        if (main) {
            code = new InstructionList();
            code.append(factory.createNew(name)); code.append(InstructionConstants.DUP);
            code.append(factory.createInvoke(name, "<init>", Type.VOID, Type.NO_ARGS, Constants.INVOKESPECIAL));
            code.append(InstructionFactory.createLoad(Type.OBJECT, 0));
            code.append(factory.createInvoke("io.ensox.exlua.vm2.lib.jse.JsePlatform", "luaMain", Type.VOID,
                new Type[]{new ObjectType("io.ensox.exlua.vm2.LuaValue"), strings}, Constants.INVOKESTATIC));
            code.append(InstructionConstants.RETURN);
            method(generated, code, "main", Constants.ACC_PUBLIC | Constants.ACC_STATIC, new Type[]{strings});
        }
        return generated.getJavaClass().getBytes();
    }

    private static void method(ClassGen type, InstructionList code, String name, int access, Type[] arguments) {
        MethodGen method = new MethodGen(access, Type.VOID, arguments, null, name, type.getClassName(), code, type.getConstantPool());
        method.setMaxLocals(); method.setMaxStack(); type.addMethod(method.getMethod()); code.dispose();
    }
}
