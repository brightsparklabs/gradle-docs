# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

The changelog is applicable from version `2.7.0` onwards.

---

## [Unreleased]

### Added

* RAD-226: Support internal references in variables file

### Fixed

### Changed

---

## [4.0.0] - 2025-05-15

### Added

* RAD-165: Add in-built BSL theme and apply by default.

### Changed

* TERA-1868: Enable `asciidoctorj-diagram` by default.

---

## [3.5.0] - 2025-05-01

### Added

* RAD-222: Add `bslDocsInclude` gradle property for filtering document generation.

---

## [3.4.2] - 2025-03-19

### Changed

* ACICGM-12: Allow overriding max aliases in a YAML file.

## [3.4.1] - 2025-03-19

### Changed

* RAD-154: Dependency patching.
---

## [3.4.0] - 2024-12-04

### Added

* RAD-1: Make it easy to see latest version.
* BD-116: Allow doctype to be overidden in asciidoc files.

---

## [3.3.0] - 2024-10-01

### Fixed

* INS-477: Remove `Chapter` prefix which got introduced by the `:sectnums:` attribute.

[Commits](https://github.com/brightsparklabs/gradle-docker/compare/3.2.0...HEAD)

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
