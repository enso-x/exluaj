return function(check, rejects)
check('destructuring fields aliases arrays and nesting', [[
    local source = {x = 2, y = false, position = {x = 3, y = 4}, items = {5, 6}}
    local {x, y: enabled, position: {x: px, y: py}, items: [a, b], absent} = source
    local [first, second, third] = {'one', 'two'}
    return x, enabled, px, py, a, b, absent, first, second, third
]], 2, false, 3, 4, 5, 6, nil, 'one', 'two', nil)
check('destructuring RHS and nested property evaluated once', [[
    local calls, log = 0, ''
    local inner = setmetatable({}, {__index = (t, key) => do
        log = log .. key
        return key
    end})
    local source = () => do
        calls += 1
        return setmetatable({}, {__index = (t, key) => do
            log = log .. key
            return inner
        end})
    end
    local {inside: {a, b}} = source()
    return calls, log, a, b
]], 1, 'insideab', 'a', 'b')
check('destructuring scope and captured locals', [[
    local x = {x = 3, y = 7}
    local old = () => x
    local {x, y} = x
    local get = () => x + y
    x += 1
    collectgarbage('collect')
    return get(), old().x
]], 11, 3)
check('destructuring preserves source when getter mutates it', [[
    local source
    source = setmetatable({}, {__index = (t, key) => do
        source = {a = 99, b = 99}
        return key
    end})
    local {a, b} = source
    return a, b, source.a
]], 'a', 'b', 99)
check('destructuring scopes within loops and functions', [[
    local out = {}
    for i = 1, 3 do
        local [x, {y}] = {i, {y = i * 2}}
        out[i] = () => x + y
    end
    return out[1](), out[2](), out[3]()
]], 3, 6, 9)
check('destructuring one RHS result and trailing commas', [[
    local get = () => do return {a = 42}, 'ignored' end
    local {a,} = get()
    local [b,] = {7}
    local {} = {}
    local [] = {}
    return a, b
]], 42, 7)
check('destructuring missing nested value errors normally', [[
    local ok, err = pcall(() => do local {child: {x}} = {} end)
    return ok, err:find('nil', 1, true) ~= nil
]], false, true)
check('destructuring bytecode roundtrip', [[
    local read = (t) => do local {a, b: [x, y]} = t; return a, x, y end
    return assert(load(string.dump(read, true)))({a = 1, b = {2, 3}})
]], 1, 2, 3)
for _, source in ipairs({
    'local {a, a} = {}', 'local {a, child: {a}} = {}',
    'local [a, a] = {}', 'local {a:} = {}', 'local {a}',
    'local [a] =', 'local {a = 2} = {}', 'local {...rest} = {}',
    'local [1] = {}', 'local {a: x, b: x} = {}',
}) do rejects('invalid destructuring ' .. source, source) end
rejects('Lua rejects record destructuring', 'local {x} = {}', '@plain.lua')
rejects('Lua rejects array destructuring', 'local [x] = {}', '@plain.lua')
local names = {}
for i = 1, 200 do names[i] = 'v' .. i end
check('200 destructuring bindings',
      'local {' .. table.concat(names, ',') .. '} = {v200 = 42}; return v200', 42)
names[201] = 'v201'
rejects('destructuring entry limit', 'local {' .. table.concat(names, ',') .. '} = {}',
        nil, 'limit is 200')
end
