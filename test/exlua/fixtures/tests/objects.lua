return function(check, rejects)
check('class constructor defaults and named methods', [[
    local class Player
        constructor(name, hp = 100)
            self.name, self.hp = name, hp
            self.items = {}
        end
        function damage(amount = 1) self.hp -= amount; return self.hp end
        static function label(value) return 'player:' .. value end
        static kind = 'player'
    end
    local a, b = Player.new(name = 'Ada'), Player.new(hp = 20, name = 'Bob')
    a.items[1] = 42
    return a.name, a:damage(amount = 5), b.hp, #b.items,
           a.damage == b.damage, getmetatable(a) == Player,
           Player.label(value = 'x'), a.kind, type(Player)
]], 'Ada', 95, 20, 0, true, true, 'player:x', 'player', 'table')
check('class inheritance and parent constructor', [[
    local calls = 0
    local class Base
        constructor(x = 7) self.x = x end
        function value() return self.x end
        static tag = 'base'
    end
    local parent = () => do calls += 1; return Base end
    local class Child extends parent() do
        function value() return Child.super.value(self) * 2 end
    end
    local class Explicit extends Child
        constructor(x, y = 3)
            Explicit.super.__init(self, x = x)
            self.y = y
        end
    end
    local a = Child.new(x = 9)
    local b = Explicit.new(y = 4, x = 2)
    return a:value(), b:value(), b.y, calls, Child.tag,
           getmetatable(a) == Child, Child.new().x
]], 18, 4, 4, 1, 'base', true, 7)
check('class no constructor and constructor return values', [[
    local class Empty end
    local class Early
        constructor(x)
            self.x = x
            return 'ignored', 3
        end
    end
    return getmetatable(Empty.new()) == Empty, Early.new(x = 4).x
]], true, 4)
check('class globals and contextual words', [[
    local class, extends, static, constructor = 1, 2, 3, 4
    class GlobalExample
        static total = class + extends + static + constructor
    end
    local result = GlobalExample.total
    GlobalExample = nil
    return result
]], 10)
check('class default values evaluated at declaration', [[
    local times = 0
    local value = () => do times += 1; return {} end
    local class Example
        constructor(t = value()) self.t = t end
    end
    return times, Example.new().t == Example.new().t,
           Example.new(t = nil).t, Example.new(t = false).t
]], 1, true, nil, false)
check('class and methods capture enclosing locals', [[
    local function make(n)
        local class Example
            constructor(x = n) self.x = x end
            function get() return self.x + n, Example end
        end
        return Example
    end
    local A, B = make(2), make(3)
    collectgarbage('collect')
    local x, owner = A.new():get()
    return x, owner == A, B.new():get() == 6
]], 4, true, true)
check('class constructor yields', [[
    local class Example
        constructor(x = 1)
            self.x = coroutine.yield(x)
        end
    end
    local co = coroutine.create(() => Example.new(x = 4))
    local ok, x = coroutine.resume(co)
    local ok2, object = coroutine.resume(co, 9)
    return ok, x, ok2, object.x, getmetatable(object) == Example
]], true, 4, true, 9, true)
check('decorator order evaluation and application', [[
    local order = ''
    local function dec(name)
        order = order .. name
        return (fn) => do
            order = order .. name:upper()
            return wraps(fn, (...) => do return fn(...) end)
        end
    end
    @dec('a')
    @dec('b')
    local function sum(x, y = 2) return x + y, nil, 9 end
    return order, sum(x = 3)
]], 'abBA', 5, nil, 9)
check('decorators preserve recursion and outer decorator scope', [[
    local f = (fn) => fn
    @f
    local function f(n)
        if n == 0 then return 1 end
        return n * f(n - 1)
    end
    return f(n = 5)
]], 120)
check('class and method decorators', [[
    local logged = 0
    local log = fn => wraps(fn, (...) => do logged += 1; return fn(...) end)
    local mark = cls => do cls.marked = true; return cls end
    @mark
    local class Example
        @log
        constructor(x = 2) self.x = x end
        @log
        function add(n = 1) self.x += n; return self.x end
        @log
        static function double(n) return n * 2 end
    end
    local obj = Example.new(x = 5)
    local result = obj:add(n = 3)
    local doubled = Example.double(n = 4)
    return Example.marked, result, doubled, logged
]], true, 8, 8, 3)
check('decorated table functions and methods', [[
    local d = fn => wraps(fn, (...) => do return fn(...) end)
    local t = {x = 3}
    @d
    function t:get(n = 2) return self.x + n end
    @d
    function t.set(value) t.x = value end
    t.set(value = 7)
    return t:get(n = 4)
]], 11)
check('wraps lambdas through pipeline and optional call', [[
    local twice = (value, amount = 2) => value * amount
    local wrapped = wraps(twice, (...) => do return twice(...) end)
    local optional = wrapped
    return 4 |> wrapped(amount = 3), optional?.(amount = 4, value = 5)
]], 12, 20)
check('wrapping does not change a reused wrapper signature', [[
    local wrapper = (...) => do return ... end
    local one = wraps((x, y = 2) => x, wrapper)
    local two = wraps((a, b = 3) => a, wrapper)
    local x, y = one(x = 1)
    local a, b = two(a = 4)
    return x, y, a, b, (pcall(() => wrapper(x = 1)))
]], 1, 2, 4, 3, false)
check('wraps supports yields and preserves errors', [[
    local original = (value = 2) => value
    local wrapper = wraps(original, (value) => do
        local x = coroutine.yield(value)
        if x == 'fail' then error('wrapper failure') end
        return x, nil, value
    end)
    local co = coroutine.create(() => do return wrapper(value = 7) end)
    local ok, n = coroutine.resume(co)
    local done, x, y, z = coroutine.resume(co, 9)
    local co2 = coroutine.create(() => wrapper())
    coroutine.resume(co2)
    local ok2, err = coroutine.resume(co2, 'fail')
    return ok, n, done, x, y, z, ok2, err:find('wrapper failure', 1, true) ~= nil
]], true, 7, true, 9, nil, 7, false, true)
check('decorator adapters keep signatures and defaults alive', [[
    local function make()
        local fn = (value = {x = 42}) => value
        return wraps(fn, (...) => do return ... end)
    end
    local f = make()
    collectgarbage('collect')
    -- A named call uses the retained signature even if the wrapper does not
    -- capture the original function at all.
    return f(value = 4), (pcall(() => f(unknown = 1)))
]], 4, false)

for _, source in ipairs({
    'local class A constructor() end constructor() end end',
    'local class A function new() end end',
    'local class A static __index = {} end',
    'local class A static x = 1 static x = 2 end',
    'local class A function x() end',
    '@print local x = 1', '@print return 1', '@',
    'local class A @print static value = 1 end',
}) do rejects('invalid class or decorator ' .. source, source) end
for _, source in ipairs({
    'local class A end', 'class A end', '@print local function f() end',
}) do rejects('ordinary Lua rejects objects ' .. source, source, '@objects.lua') end
check('class large constant indexes and member limits', (function()
    local parts = {'local t = {}'}
    for i = 1, 300 do parts[#parts + 1] = 't[' .. i .. '] = "key' .. i .. '"' end
    parts[#parts + 1] = 'local class Many'
    for i = 1, 200 do parts[#parts + 1] = 'static key' .. i .. ' = ' .. i end
    parts[#parts + 1] = 'end; return Many.key1, Many.key200'
    return table.concat(parts, '\n')
end)(), 1, 200)
check('combined features survive stripped chunk roundtrip', [==[
    local source = [=[
        local dec = fn => wraps(fn, (...) => do return fn(...) end)
        @((cls) => cls)
        local class Example
            constructor(x = 4) self.x = x end
            @dec
            function text(prefix = 'x')
                local {x} = self
                return f"{prefix}={x}"
            end
        end
        return Example
    ]=]
    local factory = assert(load(source, '@combined.exlua'))
    local Example = assert(load(string.dump(factory, true)))()
    return Example.new(x = 7):text(prefix = 'value')
]==], 'value=7')
for _, mode in ipairs({'collect'}) do
check('adapters survive ' .. mode .. ' GC', [[
    local old = 'collect'; collectgarbage(']] .. mode .. [[')
    local signature = (first = {answer = 42}, last = 7) => first
    local adapter = wraps(signature, (...) => do return ... end)
    signature = nil
    for i = 1, 100 do local t = {}; collectgarbage('step', 1) end
    collectgarbage('collect')
    local first, last = adapter(last = 9)
    collectgarbage(old)
    return first.answer, last
]], 42, 9)
end
check('wrapped constructor factory signature can be wrapped again', [[
    local class Example
        constructor(x = 4) self.x = x end
    end
    local factory = wraps(Example.new, (...) => Example.new(...))
    return factory(x = 7).x, factory().x
]], 7, 4)
check('adapter errors reject unknown duplicate and implicit self names', [[
    local class Example constructor(x = 1) self.x = x end end
    local id = wraps((x, y = 2) => x, (...) => (...))
    return (pcall(() => Example.new(self = {}))),
           (pcall(() => Example.new(1, x = 2))),
           (pcall(() => id(unknown = 1))),
           (pcall(() => wraps(print, print)))
]], false, false, false, false)
end
