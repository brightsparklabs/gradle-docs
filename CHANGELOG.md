# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

The changelog is applicable from version `2.7.0` onwards.

---

## [Unreleased]

### Added

### Fixed

### Changed

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/7.0.0...HEAD)

---

## [7.0.0] - 2026-05-06

### Added

* BD-131: Added a new `allowUriRead` configuration option (default: `false`).

### Fixed

* QGRC-6: Restored compatibility with Gradle 9 by:
    * Refactoring all custom tasks (`cleanJinjaPreProcess`, `bslGradleDocsExtractResources`,
      `bslAsciidoctorPdfVersioned`, `generateDockerfile`, `bslAsciidoctorPdfInDocker`,
      `cleanJekyllWebsite`, `generateJekyllWebsite`) to use injected `FileSystemOperations` and
      `ExecOperations` services instead of referencing `project` (and friends) at execution time.
    * Refactoring `Jinja2PreProcessingTask` to capture project state via lazy `Property` instances
      at configuration time (project name/description/version/dirs and Gradle properties),
      removing all references to `project` from the task action.
    * Marking `bslAsciidoctorPdfVersioned` and all Asciidoctor JVM plugin tasks (`asciidoctor`,
      `asciidoctorPdf`, etc.) as `notCompatibleWithConfigurationCache(...)`.
      The Asciidoctor Gradle JVM plugin (4.x) is not yet compatible with the Gradle configuration
      cache.
    * Replacing the deprecated `project.buildDir` with `project.layout.buildDirectory`.
    * Streaming JAR resources directly to the destination file in `bslGradleDocsExtractResources`
      instead of using `Project.copy { from <InputStream> }` (no longer supported in Gradle 9).
    * Marking `Jinja2PreProcessingTask.templatesDirProperty` as `@Optional` and skipping the task
      when the docs source directory does not exist, so the plugin can be applied to projects
      that do not (yet) contain documents.
    * Annotating `Jinja2PreProcessingTask` with `@DisableCachingByDefault` and
      `templatesDirProperty` with `@PathSensitive(PathSensitivity.RELATIVE)` so the task passes
      Gradle 9.5's stricter input validation.
    * Bumping the Gradle wrapper to `9.5.0` (required because the `com.brightsparklabs.gradle.baseline:7.0.0`
      plugin is built with Java 25 bytecode, which Gradle 9.0.0 cannot read).

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/6.1.0...7.0.0)

---

## [6.1.0] - 2026-03-06

### Added

* ACICGM-41: Include host environment variables in `context` under `env` key.

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/6.0.0...6.1.0)

---

## [6.0.0] - 2025-12-18

### Changed

* NSWCC-718: Bump to Java 21

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/5.1.0...6.0.0)

---

## [5.1.0] - 2025-11-20

### Added

* BD-138: Add `format_currency` macro
* BD-125: Include company name in footer

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/5.0.0...5.1.0)

---

## [5.0.0] - 2025-10-07

### Changed

* RAD-237: Update to Gradle 9
* RAD-237: Disable `shadowJar` from BSL gradle-baseline which is conflicting with the gradle-publish plugin

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/4.2.0...5.0.0)

---

## [4.2.0] - 2025-09-24

### Added

* NSWCCCLOUD-16: Add support for extended HTML colours in PDF documents
* NSWCCCLOUD-4: Add macros for standard date formatting

### Changed

* NSWCCCLOUD-16: Make repo commit lighter

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/4.1.0...4.2.0)

---

## [4.1.0] - 2025-08-28

### Added

* RAD-226: Support internal references in variables file.

### Fixed

* ACICGM-68: Return bslGradleDocs task before performing docker check.
* RAD-241: Shift off `jekyll` base to `node` base to fix dependency issues.

### Changed

* BD-135: Use table header color which works for link-color.

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/4.0.0...4.1.0)

---

## [4.0.0] - 2025-05-15

### Added

* RAD-165: Add in-built BSL theme and apply by default.

### Changed

* TERA-1868: Enable `asciidoctorj-diagram` by default.

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/3.5.0...4.0.0)

---

## [3.5.0] - 2025-05-01

### Added

* RAD-222: Add `bslDocsInclude` gradle property for filtering document generation.

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/3.4.2...3.5.0)

---

## [3.4.2] - 2025-03-19

### Changed

* ACICGM-12: Allow overriding max aliases in a YAML file.

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/3.4.1...3.4.2)

---

## [3.4.1] - 2025-03-19

### Changed

* RAD-154: Dependency patching.

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/3.4.0...3.4.1)

---

## [3.4.0] - 2024-12-04

### Added

* RAD-1: Make it easy to see latest version.
* BD-116: Allow doctype to be overidden in asciidoc files.

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/3.3.0...3.4.0)

---

## [3.3.0] - 2024-10-01

### Fixed

* INS-477: Remove `Chapter` prefix which got introduced by the `:sectnums:` attribute.

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/3.2.0...3.3.0)

---

## [3.2.0] - 2024-08-12

### Added

* INS-477: Add helper methods under `project.ext.bslGradleDocs` for creating buildscript variables
  files.

### Changed

* INS-477: Upgrade Asciidoctor gradle plugin to v4.

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/3.1.0...3.2.0)

---

## [3.1.0] - 2024-08-07

### Added

* RAD-191: Support Vega diagrams in build containers.
* RAD-192: Add `pdfTimestamped` variant for generated documents'
* INS-477: Add ability to generate 'buildscript variables' from `build.gradle`

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/3.0.0...3.y.z)

---

## [3.0.0] - 2023-12-22

### Changed

* APED-102: Upgrade to Gradle 8

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/2.7.1...3.0.0)

---

## [2.7.1] - 2024-12-22

### Fixed

* APED-102: Ignore asciidoc files which do not produce PDF files

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/2.7.0...2.7.1)

---

## [2.7.0] - 2024-12-22

### Changed

* APED-102:
    * Fixed PDF versioned file generation. Format updated:
        From: `<name> <timestamp> <hash>`
        To:   `<name>__<timestamp>__<project_version>`
    * Removed heredoc from Dockerfile which is not supported on Bitbucket CI

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/2.6.4...2.7.0)

---

## [2.6.4] - 2023-11-17

_No changelog for this release._

[Commits](https://github.com/brightsparklabs/appcli/compare/2.6.3...2.6.4)

---

# TEMPLATE

## [Unreleased]

### Added

### Fixed

### Changed

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/x.y.z...HEAD)

---

## [x.y.z] - 2024-01-01
