-- This file must remain valid in upstream Lua 5.5 as well as ExLua.
local function fn(x) return x + 1 end
local result = fn(2)
assert(result == 3)
local x, y = 1, 2
x, y = y, x
assert(x == 2 and y == 1)
assert((fn)(3) == 4)
local function first(...) return (...) end
assert(first(42, 99) == 42)
local object = {value = 10}
function object:add(amount) self.value = self.value + amount end
object:add(2)
assert(object.value == 12)
return true
