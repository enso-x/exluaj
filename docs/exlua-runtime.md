# ExLuaJ runtime contract

## Baseline and migration

- Artifact: `io.ensox.exlua:exluaj-jse:3.0.1-exlua.1`.
- Upstream: LuaJ `v3.0.1`, `70cb74b4d65851ef69240d35b58d9f8d20a73ed1`.
- Language reference: ExLua `010af99b8ae1b09f989f91ff3bf09b869010476a`.
- Java packages: replace `org.luaj` with `io.ensox.exlua`.
- Build: Gradle wrapper, JDK 17+; artifact bytecode targets Java 8.
- `_VERSION`: `ExLuaJ 3.0.1 (Lua 5.2)`.

Public/protected Java declarations are compared by reflection with the
published `org.luaj:luaj-jse:3.0.1` artifact. The original Ant build erased
selected JSE generic signatures before publication; those signatures are
preserved here as well. JME sources remain in the repository but are not part
of the JSE artifact.

ExLua's extensions run on LuaJ's Lua 5.2 VM and standard library. Lua 5.3–5.5
features such as native bitwise syntax, integer division, 64-bit Lua integers,
`<const>`, `<close>`, and Lua 5.5 GC controls are outside this runtime contract.
The Java garbage collector still owns runtime object lifetimes.

## Compilation and runtime

`LuaC` selects the original `LexState` for names ending in `.lua` and
`ExLuaLexState` otherwise. ExLua-specific tokens/template nesting live in
`ExLuaLexer`; expression lowering lives in `ExLuaLexState`; declarations and
patterns live in `ExLuaDeclarations`. All share the original scope, upvalue,
register allocation, line information, and bytecode machinery.

Extensions include:

- `+=`, `-=`, `*=`, `/=`: indexed assignment targets are evaluated once.
- `??` and `?.`: only nil short-circuits; skipped expressions are not evaluated.
- `|>`: the piped expression is evaluated before the callee/arguments. Regular
  pipelines return one value; method pipelines preserve method-call behavior.
- Arrow functions: expression/block bodies, ordinary and named varargs.
- Default parameters: values are evaluated once when the closure is created,
  in the enclosing scope; explicitly passed nil is not an omitted argument.
- Named calls: names are bound against the actual closure signature; unknown,
  duplicate, ambiguous, and already-supplied parameters are errors. Calls to
  ordinary Java functions have no Lua parameter signature.
- Classes: ordinary tables/metatables, inheritance, instance/static members,
  implicit self, constructor signature forwarding.
- Decorators: evaluate top to bottom and apply bottom to top. `wraps` preserves
  signature/default metadata while forwarding arguments and multiple results.
- Templates: immediate lexical `tostring` for each hole, once and in order;
  escapes and nested templates follow the reference language.
- Local record/array destructuring with reference scoping and validation rules.

`SETDEFAULTS` captures default values on individual closures. `NAMEDARGS`
builds a `Varargs` value so a large callee signature does not overflow a small
caller stack. Parameter names are stored on prototypes and remain available
when debug information is stripped. No global closure registry is used.

`BaseLib` installs the `exlua.class` and `wraps` helpers. Custom sandboxes can
keep using their existing library allowlist; ExLua adds no Java, IO, or OS
access. JSR-223 advertises both old `lua`/`luaj` names and `exlua`/`exluaj` names,
plus all four file extensions. The optional inherited `parser.LuaParser` AST
API remains the plain Lua 5.2 parser; execution uses `LuaC`.

Existing `org.luaj.debug` and `org.luaj.luajc` JVM flags remain aliases;
explicit `io.ensox.exlua.debug` and `io.ensox.exlua.luajc` flags take precedence.
The `luaj.package.path` property is retained.

`LuaJC` preserves loading/export APIs, including generated Java entry points.
Chunks with function/signature metadata or extended instructions use generated
Java adapters backed by the VM; eligible plain chunks retain the original BCEL
backend. This preserves semantics, not an additional JIT performance guarantee.
Gradle callers of LuaJC should add `runtimeOnly 'org.apache.bcel:bcel:5.2'`;
the published Maven POM declares BCEL optional.

## Binary chunks

The ExLuaJ binary format uses Lua 5.2 version `0x52` and format byte `1`.
Prototypes serialize parameter names and default counts. Existing LuaJ/Lua
format-0 chunks are rejected with a request to recompile. Native ExLua 5.5
bytecode is also incompatible. Exchange source files across these runtimes.

Dump/load round trips preserve signatures even with stripped debug metadata.
As in the reference language, dumping a standalone closure does not serialize
its captured defaults/upvalues: load the enclosing chunk to recreate them.
Calling omitted defaults without that state raises a clear Lua error.

## Verification and fixture provenance

Run `./gradlew test`. The suite covers the core/JSE unit tests that do not
depend on upstream's old external binary/script fixtures, the published Java
API, LuaJC compilation/export, disassembly, and ExLua's regression suite.
Fixture-dependent and JME aggregate tests are explicitly excluded in
`build.gradle`; this is not a claim that the entire historical Ant suite ran.

`test/exlua/fixtures` comes from the reference ExLua commit above, including
the feature examples and malformed-input mutation/prefix checks. The following
adaptations are needed for the LuaJ baseline:

- Expected `_VERSION` identifies ExLuaJ/Lua 5.2.
- One test of Lua 5.4 `<close>` unwinding is omitted; `<const>` is removed from
  a surrounding-expression test while retaining its default-argument assertions.
- Bitwise masks in named-argument permutations use equivalent `bit32` calls.
- Incremental/generational GC requests use `collect` because the JVM has no
  corresponding Lua collector modes. Collector restoration uses that mode too.

The ExLua-added feature assertions otherwise remain intact. Upstream's script
engine metadata expectation identifies ExLuaJ, and its stdout expectation
normalizes Windows CRLF to LF. Test failures propagate
through the public loading API to JUnit and fail the Gradle build.
