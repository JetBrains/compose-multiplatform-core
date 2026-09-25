# Repository guidance

This is a fork of androidx monorepo dedicated for Compose Multiplatform (CMP) work.
The purpose of the fork is to provide missing Kotlin targets for Compose modules. 
We add support for iOS, Web and Desktop. We do not validate, build and test Android in this fork.

The primary focus of Compose Multiplatform is the `./compose` directory.
Most often, we work in `./compose/ui/ui` and `./compose/foundation/foundation`.
Also, we publish klibs for some other libraries: `./navigation`, `./navigation3`.

In this fork, we apply fork-specific gradle files: `./gradle/libs-fork.versions.toml`, and `build-fork.gradle` in the modules, which co-exist with the upstream gradle files.

## Instruction files

A session may not automatically load instructions in descendant directories.
Read these files when working in the corresponding project parts:
- `./compose/AGENTS.md`
- `./compose/material3/material3/AGENTS.md`

When delegating, refer the subagent to those files too.
Note: Since this a fork, new instruction files might get merged from the upstream. Ignore android-specific instructions / gradle tasks / checks / verifications.

## General
- **Git:** Use `git mv` when moving files to preserve history. 
- **Git:** Do not create git commits unless explicitly requested.
- In this fork we avoid introducing code changes in the `commonMain` and `commonTest` source sets. Before changing any code in them, notify and request an approval when such a change is necessary.
- Running Gradle tasks: 
  - The output is usually very large and most of it is irrelevant. Unless it's necessary, avoid reading a full output by using `grep`, `tail`, etc.
  - Also, use `--console=plain`
  - When investigating a build failure, save the build output to a temporary file and then use `grep`.
- API: Unless a feature is intended for GA, avoid introduction of public API changes. 