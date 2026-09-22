-- Ordinary Lua test driver: each source passed to check opts in to ExLua.
return function(check, rejects)
    local function fails(name, source, message)
        check(name, 'local ok, err = pcall(function()\n' .. source
            .. '\nend); return ok, type(err) == "string" and '
            .. 'err:find(' .. string.format('%q', message) .. ', 1, true) ~= nil',
            false, true)
    end

    check('named ordinary function and mixed arguments', [[
        local function f(a, b, c) return a, b, c end
        local x = table.pack(f(c = 3, a = 1, b = 2))
        return x.n, x[1], x[2], x[3], f(4, c = 6, b = 5)
    ]], 3, 1, 2, 3, 4, 5, 6)
    check('named expression and block arrows', [[
        local one = x => x * 2
        local two = (a, b) => a - b
        local block = (a, b) => do return a, b end
        return one(x = 21), two(b = 3, a = 10), block(b = 2, a = 1)
    ]], 42, 7, 1, 2)
    check('named calls resolve dynamic closures', [[
        local first = (x, y) => x - y
        local second = (y, x) => x - y
        local t = {first, second}
        local get = i => t[i]
        local alias = t[2]
        return t[1](y = 3, x = 10), t[2](y = 3, x = 10),
               get(1)(y = 3, x = 10), alias(y = 3, x = 10)
    ]], 7, 7, 7, 7)
    check('named nil false and omitted arguments', [[
        local f = (a, b, c) => do return a, b, c end
        return f(c = false, a = nil)
    ]], nil, nil, false)
    check('named values evaluate once in source order', [[
        local order = ''
        local f = (a, b, c) => a .. b .. c
        local get = () => do order = order .. 'f'; return f end
        local value = x => do order = order .. x; return x end
        local result = get()(value('a'), c = value('c'), b = value('b'))
        return result, order
    ]], 'abc', 'facb')
    check('named values and preceding positional expressions are single', [[
        local many = () => do return 10, 20 end
        local f = (a, b, ...) => do return a, b, select('#', ...) end
        return f(many(), b = many())
    ]], 10, 10, 0)
    check('ordinary final positional expression still expands', [[
        local many = () => do return 10, 20 end
        local f = (a, b, ...) => do return a, b, select('#', ...) end
        return f(many())
    ]], 10, 20, 0)
    check('named calls with varargs and named vararg table', [[
        local f = (a, b, ...rest) => do return a, b, #rest end
        return f(b = 2, a = 1)
    ]], 1, 2, 0)
    check('named methods and explicit self', [[
        local t = {base = 10}
        function t:add(a, b = 2) return self.base + a + b end
        return t:add(b = 3, a = 4), t.add(a = 5, self = t),
               t |> :add(a = 6)
    ]], 17, 17, 18)
    check('named pipeline and optional calls', [[
        local f = (a, b = 2) => a + b
        local absent
        absent?.(b = error('must be skipped'))
        return 10 |> f(b = 3), f?.(b = 5, a = 7), absent?.(a = 1)
    ]], 13, 12, nil)
    check('named calls as statements', [[
        local result = 0
        local f = (a, b) => do result += a + b end
        f(b = 2, a = 1)
        f?.(a = 3, b = 4)
        return result
    ]], 10)
    check('nested named calls preserve surrounding registers', [[
        local f = (a, b) => a + b
        local r = {99, f(b = f(a = 2, b = 3), a = f(b = 4, a = 5)), 100}
        return r[1], r[2], r[3], 1 + f(b = 2, a = 3)
    ]], 99, 14, 100, 6)
    check('named tail recursion', [[
        local function sum(n, total = 0)
            if n == 0 then return total end
            return sum(total = total + n, n = n - 1)
        end
        return sum(n = 10000)
    ]], 50005000)
    check('named calls survive stripped bytecode', [[
        local original = (first, second) => do return first, second end
        local f = assert(load(string.dump(original, true)))
        return f(second = 2, first = 1)
    ]], 1, 2)
    check('named calls can target ordinary Lua functions', [[
        local f = assert(load('return function(a, b) return a, b end', '@plain.lua'))()
        return f(b = 2, a = 1)
    ]], 1, 2)

    rejects('repeated keyword', 'local f; f(a = 1, a = 2)', nil, 'duplicate named argument')
    rejects('positional after keyword', 'local f; f(a = 1, 2)', nil, 'positional argument follows')
    rejects('vararg after keyword', 'local f; f(a = 1, ...)', nil, 'positional argument follows')
    fails('unknown keyword', 'local f = a => a; f(other = 1)', "unknown named argument 'other'")
    fails('positional keyword collision', 'local f = a => a; f(1, a = 2)', "multiple values for argument 'a'")
    fails('implicit self collision', 'local t = {}; function t:f() end; t:f(self = t)',
        "multiple values for argument 'self'")
    fails('ambiguous duplicate parameter', 'local f = (a, a) => a; f(a = 1)',
        "ambiguous parameter name 'a'")
    fails('native function named arguments', 'print(value = 1)', 'named arguments require a Lua function')
    fails('callable table named arguments',
        'local t = setmetatable({}, {__call = (self, a) => a}); t(a = 1)',
        'named arguments require a Lua function')
    fails('varargs are not named parameters', 'local f = (...rest) => rest; f(rest = {})',
        "unknown named argument 'rest'")

    check('defaults in ordinary functions and both arrow forms', [[
        local function f(a, b = 10) return a + b end
        local expression = (x = 3) => x * 2
        local block = (x = 7) => do return x end
        return f(2), f(b = 4, a = 3), expression(), block()
    ]], 12, 7, 6, 7)
    check('defaults do not replace explicit nil or false', [[
        local f = (a = 1, b = 2, c = 3) => do return a, b, c end
        local x = table.pack(f(nil, false))
        return x.n, x[1], x[2], x[3], f(c = nil, a = false)
    ]], 3, nil, false, 3, false, 2, nil)
    check('nil and false defaults', [[
        local f = (a = nil, b = false) => do return a, b end
        return f()
    ]], nil, false)
    check('defaults evaluate at definition in enclosing scope', [[
        local calls, order, x = 0, '', 10
        local make = n => do calls += 1; order = order .. n; return x + n end
        local function f(x = make(1), y = make(2)) return x, y end
        x = 100
        local a, b = f()
        f(0, 0)
        return calls, order, a, b, f()
    ]], 2, '12', 11, 12, 11, 12)
    check('default parameter names do not shadow enclosing scope', [[
        local a = 10
        local f = (a = 1, b = a + 2) => do return a, b end
        return f()
    ]], 1, 12)
    check('mutable defaults are shared per closure', [[
        local factory = () => function(t = {}) t.n = (t.n or 0) + 1; return t.n end
        local first, second = factory(), factory()
        return first(), first(), second()
    ]], 1, 2, 1)
    check('nested default closures use the right prototype and scope', [[
        local x = 20
        local f = (a = (y = 2) => x + y, b = function(z = 3) return x + z end) => a() + b()
        local unrelated = () => 99
        x = 30
        return f(), unrelated()
    ]], 65, 99)
    check('local recursive binding survives defaults', [[
        local function f(n = 5)
            if n == 0 then return 0 end
            return 1 + f(n - 1)
        end
        return f()
    ]], 5)
    check('local default can capture recursive binding', [[
        local function f(n = 1, get = () => f)
            return n, get() == f
        end
        return f()
    ]], 1, true)
    check('defaults use only one result', [[
        local many = () => do return 1, 2, 3 end
        local f = (a = many(), b = 9, ...) => do return a, b, select('#', ...) end
        return f()
    ]], 1, 9, 0)
    check('defaults can read enclosing varargs', [[
        local function factory(...)
            return (a = ...) => a
        end
        return factory(42, 99)()
    ]], 42)
    check('defaults with extra positional varargs', [[
        local f = (a = 1, ...) => do return a, ... end
        return f(3, 4, 5)
    ]], 3, 4, 5)
    check('defaults work through C API calls', [[
        local f = (a = 42) => a
        local ok, result = pcall(f)
        return ok, result, coroutine.wrap(f)()
    ]], true, 42, 42)
    check('defaults survive ordinary and vararg tail calls', [[
        local f = (a = 42) => a
        local function forward(...) return f(...) end
        local function tail() return f() end
        return forward(), tail(), forward(nil)
    ]], 42, 42, nil)
    check('yield while evaluating named arguments', [[
        local f = (a, b = 10) => a + b
        local co = coroutine.wrap(() => f(a = coroutine.yield('argument')))
        return co(), co(32)
    ]], 'argument', 42)
    check('yield during default definition', [[
        local co = coroutine.wrap(() => do
            local f = (a = coroutine.yield('default')) => a
            return f()
        end)
        return co(), co(42)
    ]], 'default', 42)
    check('named calls under count hooks', [[
        local f = (a = 1, b = 2) => a + b
        local count = 0
        debug.sethook(function() count = count + 1 end, '', 1)
        local value = f(b = 10, a = 20)
        debug.sethook()
        return value, count > 0
    ]], 30, true)
    -- Lua 5.4 <close> and its VM unwinding are outside the LuaJ 5.2 baseline.
    check('defaults preserve surrounding expressions and locals', [[
        local a, b, c = 3, 4, 5
        local t = {99, (x = a + b) => x, (x = c) => x, 100}
        local function f(x = (a == 3 and b or c), y = nil ?? 10)
            return x + y
        end
        return a, b, c, t[1], t[2](), t[3](), t[4], f()
    ]], 3, 4, 5, 99, 7, 5, 100, 14)
    check('named binding across permutations and omitted slots', [=[
        local values = {'nil', 'false', '30', '40', '50', '60'}
        local expectedValues = {false, false, 30, 40, 50, 60}
        local count = 0
        for positional = 0, 6 do
            for mask = 0, 2 ^ (6 - positional) - 1 do
                for reverse = 0, 1 do
                    local args, expected = {}, {1, 2, 3, 4, 5, 6}
                    for i = 1, positional do
                        args[#args + 1] = values[i]
                        expected[i] = expectedValues[i]
                    end
                    local names = {}
                    for i = positional + 1, 6 do
                        if bit32.band(mask, bit32.lshift(1, i - positional - 1)) ~= 0 then
                            names[#names + 1] = i
                            expected[i] = expectedValues[i]
                        end
                    end
                    for j = 1, #names do
                        local i = names[reverse == 0 and j or #names - j + 1]
                        args[#args + 1] = 'p' .. i .. '=' .. values[i]
                    end
                    local source = 'local f=(p1=1,p2=2,p3=3,p4=4,p5=5,p6=6)=>do '
                        .. 'return p1,p2,p3,p4,p5,p6 end;return f(' .. table.concat(args, ',') .. ')'
                    local result = table.pack(assert(load(source))())
                    assert(result.n == 6)
                    for i = 1, 6 do
                        local supplied = i <= positional or
                            (i > positional and bit32.band(mask, bit32.lshift(1, i - positional - 1)) ~= 0)
                        if i == 1 and supplied then
                            assert(result[i] == nil)
                        else
                            assert(result[i] == expected[i])
                        end
                    end
                    count += 1
                end
            end
        end
        return count
    ]=], 254)
    check('stripped enclosing chunk recreates defaults and signatures', [[
        local source = 'local x = 20; local f = (a = {n = x}, b = 2) => a.n + b; return f'
        local chunk = assert(load(source))
        local f = assert(load(string.dump(chunk, true)))()
        return f(), f(b = 3), f(a = {n = 10}, b = 4)
    ]], 22, 23, 14)
    check('standalone dumped closure accepts explicit arguments', [[
        local f = assert(load(string.dump((a = 42) => a, true)))
        return f(a = nil), f(7)
    ]], nil, 7)
    fails('standalone dumped closure reports missing captured defaults',
        'local f = assert(load(string.dump((a = 42) => a))); f()',
        'default argument values unavailable')
    fails('standalone default error through named call',
        'local f = assert(load(string.dump((a = 1, b = 2) => a))); f(b = 3)',
        'default argument values unavailable')
    fails('standalone default error through tail call',
        'local f = assert(load(string.dump((a = 1) => a))); local g = function() return f() end; g()',
        'default argument values unavailable')
    rejects('required parameter after default', 'local f = (a = 1, b) => a', nil,
        'parameter without default follows')
    rejects('ordinary required parameter after default', 'local function f(a = 1, b) end', nil,
        'parameter without default follows')
    rejects('default missing expression', 'local f = (a =) => a')
    rejects('default on varargs', 'local f = (... = 1) => 1')
    rejects('ordinary Lua rejects named calls', 'local f; f(a = 1)', '@plain.lua')
    rejects('ordinary Lua rejects defaults', 'local function f(a = 1) end', '@plain.lua')

    local params, defaults, keywords = {}, {}, {}
    for i = 1, 200 do
        params[i] = 'p' .. i
        defaults[i] = params[i] .. ' = ' .. i
        keywords[i] = 'p' .. (201 - i) .. ' = ' .. (201 - i)
    end
    check('200 named arguments reorder without overflow',
        'local f = (' .. table.concat(params, ',') .. ') => do return p1, p200 end; '
        .. 'return f(' .. table.concat(keywords, ',') .. ')', 1, 200)
    check('200 defaults and expanded caller stack',
        'local f = (' .. table.concat(defaults, ',') .. ') => do return p1, p199, p200 end; '
        .. 'return f(p199 = 999)', 1, 999, 200)
    check('large defaults after hidden-vararg tail call',
        'local f = (' .. table.concat(defaults, ',') .. ') => do return p1, p200 end; '
        .. 'local function forward(...) return f() end; '
        .. 'return forward(table.unpack({}, 1, 2000))', 1, 200)
    defaults[201] = 'p201 = 201'
    rejects('201 default parameters rejected',
        'return (' .. table.concat(defaults, ',') .. ') => 1', nil, 'limit is 200')
    for i = 201, 300 do keywords[i] = 'p' .. i .. ' = ' .. i end
    rejects('oversized named call rejected', 'local f; f(' .. table.concat(keywords, ',') .. ')',
        nil, 'registers')

    for _, mode in ipairs({'collect'}) do
        check('defaults survive ' .. mode .. ' GC', [[
            collectgarbage(']] .. mode .. [[')
            local keep = {}
            for i = 1, 100 do
                local n = i
                keep[i] = (a = {n = n}, b = () => n) => a.n + b()
                collectgarbage('collect')
            end
            local result = 0
            for i, f in ipairs(keep) do result += f() + f(a = {n = i}) end
            return result
        ]], 20200)
    end
    check('old bytecode format rejected', [[
        local data = string.dump(function() end)
        local old = data:sub(1, 5) .. string.char(0) .. data:sub(7)
        local f, err = load(old)
        return f == nil, err:find('format mismatch', 1, true) ~= nil
    ]], true, true)
end
