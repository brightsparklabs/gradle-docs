# gradle-docs

[![Build Status
master](https://api.travis-ci.org/brightsparklabs/gradle-docs.svg?branch=master)](https://travis-ci.org/brightsparklabs/gradle-docs)
[![Gradle Plugin](https://img.shields.io/badge/gradle-latest-blue)](https://plugins.gradle.org/plugin/com.brightsparklabs.gradle.docs)

Applies brightSPARK Labs standardisation to project documentation.

## Build

Development Status: [![Build Status develop](https://api.travis-ci.org/brightsparklabs/gradle-docs.svg?branch=develop)](https://travis-ci.org/brightsparklabs/gradle-docs)

```shell
./gradlew build

# publish
./gradlew publishPlugins
```

## Usage

```groovy
// file: build.gradle

plugins {
    // Applies brightSPARK Labs standardisation to project documentation.
    id 'com.brightsparklabs.gradle.docs' version '<version>'
}

// Define repositories to ensure plugin dependencies libraries can be resolved.
repositories {
    jcenter()
}
```

By default:

- AsciiDoc files should be stored under `docs/`.
- Images should be stored under `docs/images`, which can then be referenced via [AsciiDoc image
  macros](https://docs.asciidoctor.org/asciidoc/latest/macros/images/).
- Any files ending with `.j2` will first be processed by
  [Jinjava](https://github.com/HubSpot/jinjava) (Java port of Python Jinja2).

Running `./gradlew build` will generate PDF and HTML of the documentation.

### Jinja2 Context Map

The context provided to the Jinja2 rendering engine has the following format:

```
# System variables.
sys:
  # The `project.name` set in Gradle.
  project_name: <name>

  # The `project.description` set in Gradle.
  project_description: <description>

  # The `project.version` set in Gradle.
  version: <version>

  # The time the build was run.
  build_timestamp: <timestamp>

  # Last git commit hash of the repo (only present if repo is under git control).
  last_commit_hash: <hash>

  # Last git commit timestamp of the repo (only present if repo is under git control).
  last_commit_timestamp: <timestamp>

# User defined variables from global variables YAML file (default: `docs/variables.yaml`).
vars:
  ...

# File variables.
file:
  # The name of the output file.
  name: <name>

  # The relative path of of the output file.
  path: <path>

  # The name of the source template file.
  src_name: <name>

  # The relative path of of the source template file.
  src_path: <path>

  # Last git commit hash of the file (only present if file is under git control).
  last_commit_hash: <hash>

  # Last git commit timestamp of the file (only present if file is under git control).
  last_commit_timestamp: <timestamp>

  # User defined variables from template specific variables YAML file (if present).
  #
  # A template specific variables file must be named the same as the Jinja2 template file with
  # `.yaml` appended.
  #
  # E.g.
  #
  #   docs/introduction.j2      -> Jinja2 template file.
  #   docs/introduction.j2.yaml -> Template specific variables YAML file.
  vars:
    ...
```

The values from the above context can be referenced using standard Jinja2 references. E.g.

    {{ sys.version }}

    {{ file.last_commit_timestamp | datetimeformat("%Y-%m-%d at %H:%M %p %Z", "Australia/Sydney") }}

## Tasks

The plugin adds the following gradle tasks:

### jinjaPreProcess

Performs Jinja2 pre-processing on documents.

### asciidoctor

Generic task to convert AsciiDoc files and copy related resources.

This will automatically be added as a dependency to the `build` task.

Alias `bslAsciidoctor`.

### asciidoctorPdf

Convert AsciiDoc files to PDF format.

Alias `bslAsciidoctorPdf`.

## Configuration

Use the following configuration block to configure the plugin:

```groovy
// file: build.gradle

project.version = 'v1.2.0-RC'

docsPluginConfig {
    // YAML file containing context variables used when rendering Jinja2 templates.
    // Default: `docs/variables.yaml`.
    variablesFile: src/my-variables.yaml

    // Name of the directory (relative to project root) containing the documents to process.
    // Default: `docs/`.
    docsDir: src/

    // Name of the directory (relative to project root) containing the images.
    // Default: `docs/images`.
    imagesDir: images/
}
```

## Testing during development

To test plugin changes during development:

```bash
# bash

# create a test application
mkdir gradle-docs-test
cd gradle-docs-test
gradle init --type java-application --dsl groovy
# add the plugin (NOTE: do not specify a version)
sed -i "/plugins/ a id 'com.brightsparklabs.gradle.docs'" build.gradle

# setup git (plugin requires repo to be under git control)
git init
git add .
git commit "Initial commit"
git tag -a -m "Tag v0.0.0" 0.0.0

# run using the development version of the plugin
gradlew --include-build /path/to/gradle-docs <task>
```

## Licenses

Refer to the `LICENSE` file for details.
