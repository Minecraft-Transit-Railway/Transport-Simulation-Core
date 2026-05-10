# Build

> How to build, test, and develop Transport Simulation Core. For runtime usage of the
> resulting jar see [RUNNING.md](RUNNING.md); for the schema-driven code generation flow
> see [SCHEMA.md](SCHEMA.md).

## Prerequisites

| Tool    | Version                                                                                                                                                            |
|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| JDK     | **21** — pinned via `java.toolchain.languageVersion = 21` in [`build.gradle`](../build.gradle). Gradle will provision a matching toolchain if one isn't installed. |
| Node.js | A current LTS that supports the Angular 21 toolchain.                                                                                                              |
| npm     | Bundled with Node.                                                                                                                                                 |
| Gradle  | Use the wrapper (`./gradlew` / `gradlew.bat`). Don't install Gradle globally.                                                                                      |

## Backend (Gradle)

All commands run from the repository root.

```bash
./gradlew build                            # compile, run tests, package the jar
./gradlew jar                              # just package
./gradlew test                             # JUnit 5 only
./gradlew clean build
./gradlew publishToMavenLocal              # publish a local Maven artifact (group org.mtr.core)
```

Outputs land in `build/libs/`:

- `Transport-Simulation-Core-<version>.jar` — the runnable fat jar
  (`Main-Class: org.mtr.core.Main`).
- `Transport-Simulation-Core-<version>-sources.jar`
- `Transport-Simulation-Core-<version>-javadoc.jar`

`<version>` is derived at task-configuration time from
`ZonedDateTime.now(Asia/Hong_Kong).format("yyyyMMdd-HHmmss")` — see the
`tasks.configureEach` block in `build.gradle`. The same block also rewrites
[`src/main/VersionTemplate.java`](../src/main/VersionTemplate.java) and
[`website/version-template.txt`](../website/version-template.txt) into `Version.java` and
`version.ts` respectively, so the running server and the bundled UI report the same build
identifier.

### Schema-driven code generation

```bash
./gradlew generateJavaSchemaClasses        # regenerate Java schema classes + WebserverResources
./gradlew generateTypeScriptSchemaClasses  # regenerate TypeScript schema classes
```

These tasks read JSON schemas from `buildSrc/src/main/resources/schema/<area>/` and write
generated Java / TypeScript into `src/main/java/org/mtr/core/generated/` and
`website/src/app/entity/generated/`. Both output directories are git-ignored. See
[SCHEMA.md](SCHEMA.md) for the schema mini-language.

`generateJavaSchemaClasses` also runs `WebserverSetup`, which packages the built Angular
app into the `WebserverResources` Java class served at `/`.

The full pre-release sequence is therefore:

```bash
cd website && npm install && npm run build && cd ..
./gradlew generateTypeScriptSchemaClasses generateJavaSchemaClasses
./gradlew clean build
```

## Frontend (Angular)

All commands run from `website/`.

```bash
npm install                # one-off / after dependency changes
npm start                  # ng serve --host 0.0.0.0 — dev server on http://localhost:4200
npm run watch              # ng lint && ng build --watch --configuration development
npm run build              # ng lint && ng build --base-href a — production build
npm run lint               # ng lint
npm test                   # ng test
```

The dev server hits the same `/mtr/api/map/*` endpoints exposed by the running Java jar; in
development you'll typically run the jar on its default port and let the Angular dev server
proxy XHRs to it.

`npm run update-all` upgrades all npm dependencies (and `baseline-browser-mapping`) to
their latest compatible versions — useful, but always re-run `npm run build` and the lint /
type-check pipeline after it.

## Project structure

```
Transport-Simulation-Core/
├── build.gradle             ← top-level Gradle script
├── settings.gradle
├── gradle.properties        ← project version etc.
├── buildSrc/                ← Gradle plugin: schema generator + webserver-resources packer
│   └── src/main/resources/schema/<area>/*.json   ← authoring schemas (see SCHEMA.md)
├── src/
│   ├── main/
│   │   ├── VersionTemplate.java                  ← rewritten to Version.java each build
│   │   ├── java/org/mtr/core/                    ← backend source
│   │   └── resources/log4j2.properties
│   └── test/java/org/mtr/core/                   ← JUnit 5 tests
├── website/                 ← Angular 21 dashboard
│   ├── package.json
│   └── src/app/{component,service,pipe,entity,...}/
└── docs/                    ← this folder
```

## Continuous integration

There is no CI configuration in the repo at the moment. When you add one, model it on the
"full pre-release sequence" above (frontend build → schema generation → Gradle build with
tests).
