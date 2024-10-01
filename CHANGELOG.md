# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

The changelog is applicable from version `2.7.0` onwards.

---

## [Unreleased]

### Added

### Fixed

### Changed

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
