-- Exercise recovery from malformed new syntax without executing the input.
-- Run under the same assertions/sanitizers as the semantic regression suite.
local seeds = {
    'local class C extends ({}) constructor(x=1) self.x=x end function get(y) return self.x+y end end',
    '@((f)=>f) local function f(x=1) return x end',
    'local {a, child: {b}, list: [x,y]} = {a=1,child={b=2},list={3,4}}',
    'return f"a{({x=f\'b{1}\'}).x}c{{end}}"',
}
local replacements = {'{', '}', '[', ']', '(', ')', '"', "'", '@', '=', ',', '\n', ''}
local total = 0
for _, source in ipairs(seeds) do
    assert(load(source, '@syntax-seed.exlua'))
    for i = 1, #source do
        for _, replacement in ipairs(replacements) do
            local mutated = source:sub(1, i - 1) .. replacement .. source:sub(i + 1)
            local fn, err = load(mutated, '@syntax-mutation.exlua')
            assert(type(fn) == 'function' or type(err) == 'string')
            total = total + 1
        end
        local fn, err = load(source:sub(1, i), '@syntax-prefix.exlua')
        assert(type(fn) == 'function' or type(err) == 'string')
        total = total + 1
    end
end
print(('ok - %d syntax mutations and prefixes recover safely'):format(total))
return true
