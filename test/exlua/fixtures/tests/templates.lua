return function(check, rejects)
check('template values expressions escapes and literal braces', [=[
    local name, hp = 'Ada', 42
    return f"Player {name}, HP: {hp + 1}", f'{nil}/{false}/{true}',
           f"{{{hp}}}", f"\x41\u{42}\067\n\t\000end", f"plain", f''
]=], 'Player Ada, HP: 43', 'nil/false/true', '{42}', 'ABC\n\t\0end', 'plain', '')
check('nested templates quoted braces and tables', [==[
    local x = 7
    return f"outer {f"inner {x}"}!", f"{({x = '}'}).x}",
           f"{[=[{text}]=]}", f'{({x = 1}).x + 2}'
]==], 'outer inner 7!', '}', '{text}', '3')
check('template expression scopes lambdas and named arguments', [=[
    local offset = 3
    local f = (x = 2) => f"{x + offset}"
    return f(x = 4), f"{((x) => x * 2)(x = 5)}", f"{4 |> f}"
]=], '7', '10', '7')
check('template evaluation and conversion order', [=[
    local log = ''
    local get = name => do
        log = log .. name
        return setmetatable({}, {__tostring = () => do
            log = log .. name:upper()
            return name
        end})
    end
    local text = f"{get('a')}-{get('b')}"
    return text, log
]=], 'a-b', 'aAbB')
check('template preserves registers and single results', [=[
    local x = 9
    local pair = () => do return 1, 2 end
    local a, b, c = x, f"{pair()}{x}", x + 1
    return a, b, c, f"{x}", 99
]=], 9, '19', 10, '9', 99)
check('template multiline expressions and comments', [=[
    return f"result: { -- } ignored
      (2 + -- } ignored too
       3) --[[ } ]]
    }", f"a\
b"
]=], 'result: 5', 'a\nb')
check('template lexical lookahead in table fields and parentheses', [=[
    local t = {f"{2}", named = f"{3}"}
    local name = 5
    return t[1], t.named, f"{(name)}", f"{(name) => name}" ~= nil
]=], '2', '3', '5', true)
check('template bytecode roundtrip', [=[
    local f = (x) => f"value={x}"
    local loaded = assert(load(string.dump(f, true)))
    return loaded(x = 42)
]=], 'value=42')
check('ordinary Lua retains f string-call syntax', [=[
    local source = 'local f = function(s) return s end; return f"{literal}"'
    return assert(load(source, '@plain.lua'))()
]=], '{literal}')
check('template conversion uses lexical tostring', [=[
    local tostring = x => 'converted'
    return f"{42}"
]=], 'converted')
for _, source in ipairs({
    'return f"{}"', 'return f"{1, 2}"', 'return f"}"',
    'return f"{1"', 'return f"{', 'return f"unclosed',
    'return f"{1} unfinished', 'return f"{local x}"',
    'return f"{1} newline\n"',
}) do rejects('invalid template ' .. source, source) end
local nested = '1'
for i = 1, 32 do nested = 'f"{' .. nested .. '}"' end
check('template nesting boundary', 'return ' .. nested, '1')
rejects('template nesting limit', 'return f"{' .. nested .. '}"', nil, 'limit is 32')
check('template call shorthand and method arguments', [=[
    local capture = (text) => text
    local t = {prefix = 'x'}
    function t:show(text) return self.prefix .. text end
    return capture f"{42}", t:show f"{3}"
]=], '42', 'x3')
check('large templates concatenate all holes', 'return f"' .. string.rep('{1}', 100) .. '"',
      string.rep('1', 100))
rejects('oversized template reports register limit',
        'return f"' .. string.rep('{1}', 200) .. '"', nil, 'too many registers')
end
