# ExLuaJ

[ExLua](https://github.com/enso-x/exlua) language extensions on the
[LuaJ 3.0.1](https://github.com/luaj/luaj/tree/v3.0.1) JVM interpreter.
The Java API is preserved under `io.ensox.exlua`:
`org.luaj.vm2.Globals` becomes `io.ensox.exlua.vm2.Globals`, for example.

```groovy
implementation 'io.ensox.exlua:exluaj-jse:3.0.1-exlua.1'
```

The artifact can be built through a Gradle composite or published to Maven
Local. It is not currently published to Maven Central.

## Build and embed

Use JDK 17 or later to run the Gradle wrapper. The library targets Java 8.

```sh
./gradlew build
./gradlew publishToMavenLocal
```

For a source dependency, add `includeBuild('/path/to/exluaj')` to the host
project's `settings.gradle`. Gradle substitutes the coordinate above with
the local project; no Maven publication or additional repository is needed.
For Maven Local, add `mavenLocal()` to the host's repositories instead.

```java
import io.ensox.exlua.vm2.Globals;
import io.ensox.exlua.vm2.lib.jse.JsePlatform;

Globals globals = JsePlatform.standardGlobals();
globals.load("local add = (a, b = 2) => a + b; print(add(a = 40))", "example.exl").call();
```

Existing LuaJ embedding code only needs the dependency and package imports
changed. `Globals`, `LuaValue`, `LuaClosure`, `Prototype`, `LuaC`, Java coercion,
coroutines, custom library environments, and JSR-223 remain available.
LuaJC remains an optional API and requires `org.apache.bcel:bcel:5.2` at runtime.

## Language

The compiler accepts ExLua's compound assignments, nil coalescing, optional
fields/calls, pipelines, arrow functions, named/default arguments, named
varargs, classes/inheritance, decorators, template strings, and local
destructuring. These compile directly to register bytecode, without a source
transpiler or a native library.

Files/chunks named `*.lua` use the original Lua 5.2 grammar. `.exl`, `.exlua`,
`.luex`, and anonymous chunks enable extensions. Pass the real filename when
loading source so the dialect stays explicit. `require` searches both dialects.

The VM, number model, standard library, and garbage collection retain the
LuaJ 3.0.1 baseline. This is not a Lua 5.5 VM port: use `bit32` instead of
Lua 5.3 bitwise operators; `<const>`/`<close>`, 64-bit Lua integers, and Lua 5.5
collector modes are not provided. See [compatibility and architecture](docs/exlua-runtime.md)
for the precise boundaries, binary format, and test provenance.

## Upstream and license

Forked from LuaJ tag `v3.0.1`, commit
`70cb74b4d65851ef69240d35b58d9f8d20a73ed1`. ExLua syntax follows commit
`010af99b8ae1b09f989f91ff3bf09b869010476a` of `enso-x/exlua`.

MIT licensed; original notices remain in the source. See [LICENSE](LICENSE).
The inherited Ant builds and `README.html` are retained as upstream history;
Gradle is the maintained build for this fork.
