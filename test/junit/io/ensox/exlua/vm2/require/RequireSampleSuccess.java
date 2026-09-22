package io.ensox.exlua.vm2.require;

import io.ensox.exlua.vm2.LuaValue;
import io.ensox.exlua.vm2.lib.TwoArgFunction;

/**
 * This should succeed as a library that can be loaded dynamically via "require()"
 */
public class RequireSampleSuccess extends TwoArgFunction {
	
	public RequireSampleSuccess() {		
	}
	
	public LuaValue call(LuaValue modname, LuaValue env) {
		env.checkglobals();
		return LuaValue.valueOf("require-sample-success-"+modname.tojstring());
	}	
}
