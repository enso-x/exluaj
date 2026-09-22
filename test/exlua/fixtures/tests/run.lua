-- Run with `make test`. This runner is ordinary Lua; test chunks opt in by name.
local count = 0
local function check(name, source, ...)
    local chunk, err = load(source, '@' .. name .. '.exlua')
    assert(chunk, err)
    local actual, expected = table.pack(chunk()), table.pack(...)
    assert(actual.n == expected.n, name .. ': wrong number of results: ' .. actual.n)
    for i = 1, expected.n do
        assert(actual[i] == expected[i], name .. ': result ' .. i .. ': expected '
            .. tostring(expected[i]) .. ', got ' .. tostring(actual[i]))
    end
    count = count + 1
    print('ok - ' .. name)
end

local function rejects(name, source, chunkname, message)
    local chunk, err = load(source, chunkname or '@rejected.exlua')
    assert(not chunk, name .. ': unexpectedly compiled')
    if message then
        assert(err:find(message, 1, true), name .. ': unexpected error: ' .. err)
    end
    count = count + 1
    print('ok - ' .. name)
end

check('coalesce preserves locals', [[
    local x = nil
    local y = x ?? 42
    return x, y
]], nil, 42)
check('coalesce preserves arguments', [[
    local function f() return 42 end
    return table.pack(nil ?? f(), 99).n, (nil ?? f()), 99
]], 2, 42, 99)
check('coalesce preserves assignments', [[
    local function f() return 42 end
    local x, y = nil ?? f(), 99
    return x, y
]], 42, 99)
check('coalesce preserves return lists', [[
    local function f() return 42 end
    return 1, nil ?? f(), 99
]], 1, 42, 99)
check('coalesce short circuits', [[
    local calls = 0
    local f = () => do calls += 1; return 99 end
    local a, b, c = 7 ?? f(), false ?? f(), nil ?? f()
    return a, b, c, calls
]], 7, false, 99, 1)
check('coalesce nested temporaries', [[
    local f = () => 42
    return nil ?? (nil ?? f()), (nil ?? f()) + (nil ?? 1), 99
]], 42, 43, 99)
check('coalesce with logical expressions', [[
    local a, b = false, true
    return (a or b) ?? 7, nil ?? (a and b), (nil ?? false) or 9,
           false ?? (a == b), nil ?? (a == b)
]], true, false, 9, false, false)
check('coalesce uses one RHS result', [[
    local f = () => do return 1, 2 end
    return nil ?? f(), 99
]], 1, 99)
check('coalesce does not change captured local', [[
    local x
    local get = () => x
    local y = x ?? 42
    return get(), y
]], nil, 42)

check('pipeline library functions', [[
    return -5 |> math.abs, 'hello' |> string.upper,
           {'a', 'b'} |> table.concat(',')
]], 5, 'HELLO', 'a,b')
check('pipeline callee temporaries', [[
    local funcs = {math.abs}
    local index = () => 1
    local get = () => math.abs
    local object = () => {f = math.abs}
    return -5 |> funcs[index()], -6 |> (get()), -7 |> (object()).f
]], 5, 6, 7)
check('pipeline evaluates left before callee', [[
    local order = ''
    local value = () => do order = order .. 'v'; return -5 end
    local callee = () => do order = order .. 'f'; return math.abs end
    local result = value() |> (callee())
    return result, order
]], 5, 'vf')
check('pipeline lists and nesting', [[
    local add = (a, b) => a + b
    local f = () => 42
    local a, b = (nil ?? f()) |> add(1), 99
    return a, b, 10 |> add(5) |> add(2), -5 |> math.abs |> add(2)
]], 43, 99, 17, 7)
check('pipeline vararg arguments', [[
    local rest = () => do return 2, 3 end
    local capture = (...) => table.concat({...}, ',')
    return 1 |> capture(rest())
]], '1,2,3')
check('pipeline statements accept expressions', [[
    local values = {}
    local save = x => do values[#values + 1] = x end
    local x = 1
    x + 2 |> save;
    -1 |> save;
    #values |> save;
    not false |> save;
    (x + 3) |> save;
    return values[1], values[2], values[3], values[4], values[5]
]], 3, -1, 2, true, 4)
check('method pipeline', [[
    local box = {value = 2}
    function box:add(n) self.value += n; return self end
    box |> :add(3) |> :add(4)
    return box.value, ({value = 5, add = box.add} |> :add(2)).value
]], 9, 7)
rejects('nested pipeline does not allow arbitrary statements', '{1 |> tostring}')
rejects('lambda body pipeline does not allow arbitrary statements',
    '{() => 1 |> tostring}')
rejects('arithmetic after a call is not a statement', 'print() + 1')

check('optional calls as statements', [[
    local calls = 0
    local f = () => do calls += 1 end
    f?.()
    f = nil
    f?.(error('arguments must not be evaluated'))
    return calls
]], 1)
check('optional access and call values', [[
    local user = {profile = {name = 'Ada', get = () => 42}}
    local missing
    return user?.profile?.name, missing?.profile?.name,
           user?.profile?.get?.(), user?.missing?.(), 99
]], 'Ada', nil, 42, nil, 99)
check('optional receivers evaluated once', [[
    local calls = 0
    local get = () => do calls += 1; return {value = false} end
    local result = get()?.value ?? 9
    return result, calls
]], false, 1)
check('optional checks only nil', [[
    local x = false
    local ok = pcall(() => x?.field)
    return ok
]], false)
rejects('optional field is not a statement', 'local x; x?.field')
rejects('optional call cannot be assigned', 'local x; x?.() = 1')

check('arrow forms', [[
    local a = () => 42
    local b = x => x * 2
    local c = (x) => x + 1
    local d = (x, y) => x + y
    local e = (x) => do local y = x * 2; return y + 1 end
    return a(), b(21), c(41), d(20, 22), e(20)
]], 42, 42, 42, 42, 41)
check('arrow captures and shadowing', [[
    local x = 7
    local add = x => y => x + y
    local f = (x, y) => x + y
    return add(20)(22), f(1, 2), x
]], 42, 3, 7)
check('arrow varargs', [[
    local a = (...) => do return ... end
    local b = (first, ...) => do return first, ... end
    local c = (...args) => args[2]
    local d = (first, ...args) => first + args[1]
    local x, y = a(1, 2)
    local p, q = b(3, 4)
    return x, y, p, q, c(10, 11), d(20, 22)
]], 1, 2, 3, 4, 11, 42)
check('arrow expression returns one value', [[
    local pair = () => do return 1, 2 end
    local f = () => pair()
    return table.pack(f()).n, f()
]], 1, 1)
check('parentheses remain ordinary expressions', [[
    local x = 42
    local pair = function(...) return (...) end
    local funcs = {() => 7}
    return (x), ((x) + 1), (math.abs)(-2), (funcs)[1](), pair(8, 9)
]], 42, 43, 2, 7, 8)
check('fn remains an ordinary identifier', [[
    local function fn(x) return x + 1 end
    fn(1)
    local a = fn(2)
    return a, (fn)(3), 4 |> fn
]], 3, 4, 5)
check('arrow multiline and comments', [[
    local f = (
        first, -- name
        second
    ) => do
        return first + second
    end
    local g = (value -- ambiguous parenthesized name
    ) => value
    return f(20, 22), g(42)
]], 42, 42)
check('arrows survive bytecode roundtrip', [[
    local f = (x, y) => x + y
    return assert(load(string.dump(f)))(20, 22)
]], 42)
for _, source in ipairs({
    'local f = fn(x) => x', 'local f = (x,) => x',
    'local f = (1) => 1', 'local f = (x + 1) => x',
    'local f = (..., x) => x', 'local f = (x, y)',
    'local f = (x, y) =>', 'local f = () => do return 1',
}) do
    rejects('invalid arrow ' .. source, source)
end
local params = {}
for i = 1, 200 do params[i] = 'p' .. i end
check('200 arrow parameters', 'return type((' .. table.concat(params, ',') .. ') => 1)', 'function')
params[201] = 'p201'
rejects('201 arrow parameters', 'return (' .. table.concat(params, ',') .. ') => 1',
    nil, 'limit is 200')
rejects('old fn overflow input is rejected', 'return fn(' .. table.concat(params, ',') .. ') => 1')

check('compound targets evaluated once', [[
    local t = {value = 10}
    local reads, keys = 0, 0
    local object = () => do reads += 1; return t end
    local key = () => do keys += 1; return 'value' end
    object()[key()] += 5
    t.value -= 3
    t.value *= 2
    t.value /= 4
    return t.value, reads, keys
]], 6, 1, 1)
rejects('compound assignment respects const', 'local x <const> = 1; x += 1')

-- Force the first nil constant past EQK's 8-bit constant index.
local prefix = {'local t = {}'}
for i = 1, 300 do prefix[#prefix + 1] = 't[' .. i .. '] = "constant-' .. i .. '"' end
prefix = table.concat(prefix, '\n') .. '\n'
check('large constant table coalescing', prefix .. [[
    local x
    return x ?? 42, x, false ?? 99, 7 ?? 99
]], 42, nil, false, 7)
check('large constant table optional field', prefix .. [[
    local x
    local y = {value = 42}
    return x?.value, y?.value
]], nil, 42)
check('large constant table optional call', prefix .. [[
    local x
    local y = () => 42
    x?.(error('must not run'))
    return x?.(), y?.()
]], nil, 42)

local compat = [[
    local function fn(x) return x + 1 end
    local x, y = 1, 2
    x, y = y, x
    return fn(2), (fn)(3), x, y
]]
local ordinary = assert(load(compat, '@compat.lua'))
local a, b, c, d = ordinary()
assert(a == 3 and b == 4 and c == 2 and d == 1)
count = count + 1
for _, source in ipairs({
    'local x=1; x+=1', 'local x=1; x-=1', 'local x=1; x*=2',
    'local x=1; x/=2', 'return nil ?? 1', 'local x; return x?.a',
    'return 1 |> tostring', 'return x => x', 'return (x, y) => x + y',
}) do
    rejects('ordinary Lua rejects ' .. source, source, '@compat.lua')
end
rejects('named Lua chunk rejects extensions', 'return nil ?? 1', 'compat.lua', '.exlua')
assert(assert(load('return (() => 42)()'))() == 42)
count = count + 1

local testdir = arg[0]:match('^(.*[/\\])') or './'
dofile(testdir .. 'named.lua')(check, rejects)
dofile(testdir .. 'objects.lua')(check, rejects)
dofile(testdir .. 'templates.lua')(check, rejects)
dofile(testdir .. 'destructuring.lua')(check, rejects)
assert(dofile(testdir .. 'syntax.lua'))
count = count + 1
assert(_VERSION == 'ExLuaJ 3.0.1 (Lua 5.2)')
for _, extension in ipairs({'.exlua', '.exl', '.luex'}) do
    local chunk = assert(load('return ((x = 42) => x)()', '@example' .. extension))
    assert(chunk() == 42)
    count = count + 1
end
local oldpath = package.path
package.path = oldpath:gsub('%.[/\\]', function() return testdir .. 'modules/' end)
for _, name in ipairs({'exlua_module', 'exl_module', 'legacy_module',
                      'exlua_package', 'exl_package'}) do
    assert(require(name) == 42)
    count = count + 1
end
package.path = oldpath
assert(dofile(testdir .. 'compat.lua'))
count = count + 1
dofile(testdir .. '../test.exlua')
print(('Passed %d regression checks and the feature examples.'):format(count))
