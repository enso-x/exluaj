package io.ensox.exlua.vm2.require;

import io.ensox.exlua.vm2.LuaValue;
import io.ensox.exlua.vm2.lib.ZeroArgFunction;

/**
 * This should fail while trying to load via 
 * "require()" because it throws a LuaError
 * 
 */
public class RequireSampleLoadLuaError extends ZeroArgFunction {
	
	public RequireSampleLoadLuaError() {		
	}
	
	public LuaValue call() {
		error("sample-load-lua-error");
		return LuaValue.valueOf("require-sample-load-lua-error");
	}	
}
