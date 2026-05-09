# Schemas and Code Generation

> Most of the data model is **generated** from JSON schemas at build time. This document
> covers the schema mini-language, the regen workflow, and the rules for editing it.

## Layout

Authoring schemas live under
[`buildSrc/src/main/resources/schema/`](../buildSrc/src/main/resources/schema/). Each
sub-folder is a separate "domain" with its own output package:

| Folder         | Used for                                                                 | Java output                          | TypeScript output                       |
|----------------|--------------------------------------------------------------------------|--------------------------------------|-----------------------------------------|
| `data/`        | Persisted simulation state — stations, routes, vehicles, sidings, etc.   | `org.mtr.core.generated.data`        | —                                       |
| `legacy/`      | Backwards-compatible loaders for old save formats.                       | `org.mtr.legacy.generated.data`      | —                                       |
| `integration/` | `Request` / `Response` envelope and DTOs shared with embedded hosts.     | `org.mtr.core.generated.integration` | —                                       |
| `map/`         | DTOs returned by `/mtr/api/map/*` and consumed by the Angular dashboard. | `org.mtr.core.generated.map`         | `website/src/app/entity/generated/map/` |
| `oba/`         | DTOs returned by `/oba/api/where/*`.                                     | `org.mtr.core.generated.oba`         | —                                       |
| `operation/`   | DTOs for the in-process operation queue (`get_data`, `update_data`, …).  | `org.mtr.core.generated.operation`   | —                                       |

The mapping is wired in
[`build.gradle`](../build.gradle) under
`tasks.register("generateJavaSchemaClasses")` and
`tasks.register("generateTypeScriptSchemaClasses")`.

## Regenerating

```bash
./gradlew generateJavaSchemaClasses
./gradlew generateTypeScriptSchemaClasses
```

Run both whenever you touch a schema. The output folders are git-ignored, so the generated
sources are produced fresh per build environment.

`generateJavaSchemaClasses` additionally:

- emits a `SchemaTests` JUnit class per domain that round-trips a randomly populated
  instance through serialise → deserialise (so adding a property without updating the
  serializer breaks the build),
- runs `WebserverSetup`, which packages the built Angular app into the `WebserverResources`
  Java class served at `/`. This means the website must be built (`cd website && npm run
  build`) before this Gradle task if you want the dashboard bundled into the jar.

## Schema mini-language

Each schema file is a JSON object that the [`Generator`](../buildSrc/src/main/java/org/mtr/core/Generator.java)
translates into a Java class and (for the `map/` domain) a TypeScript class.

The top-level keys are:

| Key           | Type                           | Meaning                                                                                                                                              |
|---------------|--------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `extends`     | `{"$ref": "OtherSchema.json"}` | This schema extends another schema in the same folder. The generated class extends the other generated class.                                        |
| `javaExtends` | string                         | Manual Java superclass (used when the parent isn't itself a generated class).                                                                        |
| `properties`  | object                         | Map of property name → property descriptor. The descriptor is the JSON-schema-flavoured shape used by `SchemaParserJava` / `SchemaParserTypeScript`. |
| `description` | string                         | Optional doc comment emitted on the generated class.                                                                                                 |

A property descriptor's most common keys:

| Key                   | Meaning                                                                                      |
|-----------------------|----------------------------------------------------------------------------------------------|
| `type`                | `"string"`, `"integer"`, `"number"`, `"boolean"`, `"array"`, `"object"` (JSON-schema-style). |
| `items`               | For arrays: the descriptor for the element type.                                             |
| `$ref`                | Reference to another schema in the same folder (becomes the property's Java / TS type).      |
| `default`             | Default value baked into the generated reader/writer pair.                                   |
| `description`         | Doc comment on the generated getter / field.                                                 |
| `minimum` / `maximum` | Validation hints (currently informational on the Java side).                                 |

Look at any existing schema in `buildSrc/src/main/resources/schema/data/` for a worked
example.

## Rules

1. **Never edit generated sources directly.** Files under
   `src/main/java/org/mtr/core/generated/` and `website/src/app/entity/generated/` are
   regenerated on every build and your changes will silently disappear. Edit the JSON schema
   instead and re-run the relevant Gradle task.
2. **Mirror schema renames in both languages.** Renaming a property in `schema/map/` will
   change both the Java DTO consumed by the servlet and the TypeScript interface consumed by
   the dashboard. Run both regeneration tasks before testing.
3. **Use `lower_snake_case` for property names.** Generated Java getters and TypeScript
   fields convert this to `camelCase`; the wire format keeps `snake_case`.
4. **Keep generated code out of imports lists.** When the schema's class name changes, every
   hand-written file that imports the generated class needs updating. Re-run the build
   after a rename to surface the affected files.
5. **The schema is part of the public contract** between this jar and embedded hosts (the
   Minecraft Transit Railway mod). Removing or changing a field is a breaking change — bump
   the project version (currently date-based, see [BUILD.md](BUILD.md)) and call it out in
   release notes.

## Top-level `schema/` folder

There is **no** `schema/` folder at the repository root. The build references string paths
like `schema/data` from `Generator.generateJava(...)`, but the generator resolves them
against `buildSrc/src/main/resources/`. An older empty `schema/` shell at the project root
existed historically and has been removed to avoid confusion.
