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

- AsciiDoc files should be stored under `src/docs/`.
- Images should be stored under `src/images`, which can then be referenced via [AsciiDoc image
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
  project_version: <version>

  # The time the build was run.
  build_timestamp: <timestamp>

  # Details of the last git commit on the repo.
  repo_last_commit:
    # The git commit hash (defaults to `unspecified` if project not under git control).
    hash: <hash>

    # The git commit timestamp (defaults to build timestamp if project not under git control).
    timestamp: <timestamp>

# User defined variables from global variables YAML file (default: `src/variables.yaml`).
vars:
  ...

# Details of the last git commit on the global variables YAML file.
vars_file_last_commit:
  # The git commit hash (defaults to `unspecified` if file not under git control).
  hash: <hash>

  # The git commit timestamp (defaults to build timestamp if file not under git control).
  timestamp: <timestamp>

# (Dynamic) Variables pertaining to the CURRENT template being rendered.
template_file:
  # The name of the source template file.
  name: <name>

  # The relative path (in docs directory) of the source template file.
  path: <path>

  # Details of the last git commit on the template file.
  last_commit:
    # The git commit hash (defaults to `unspecified` if file not under git control).
    hash: <hash>

    # The git commit timestamp (defaults to build timestamp if file not under git control).
    timestamp: <timestamp>

  # User defined variables from template specific variables YAML file (if present).
  #
  # A template specific variables file must be named the same as the Jinja2 template file with
  # `.yaml` appended.
  #
  # E.g.
  #
  #   src/docs/introduction.j2      -> Jinja2 template file.
  #   src/docs/introduction.j2.yaml -> Template specific variables YAML file.
  vars:
    ...

  # Details of the last git commit on the template specific variables YAML file (if present).
  vars_file_last_commit:
    # The git commit hash (defaults to `unspecified` if file not under git control).
    hash: <hash>

    # The git commit timestamp (defaults to build timestamp if file not under git control).
    timestamp: <timestamp>

# (Dynamic) User defined variables from the CURRENT instance variable file being processed (if
# present).
#
# Instance variable files must be stored under a directory with the same name as the Jinja2 template
# file with `.d` appended. Each `.yaml` file under this directory will be rendered against the
# corresponding Jinja2 template file.
#
# E.g.
#
#   src/docs/sops/sop-template.j2                        -> Jinja2 template file.
#   src/docs/sops/sop-template.j2.d/                     -> Instance variables directory.
#   src/docs/sops/sop-template.j2.d/restart-servers.yaml -> Instance variables file.
#   src/docs/sops/sop-template.j2.d/purge-logs.yaml      -> Instance variables file.
#
# Will result in the following output directory structure:
#
#   sops/restart-servers.pdf
#   sops/purge-logs.pdf
instance_file:
  # The name of the instance variable YAML file.
  name: <name>

  # The relative path (in docs directory) of the instance variables YAML file.
  path: <path>

  # Details of the last git commit on the instance variables YAML file.
  last_commit:
    # The git commit hash (defaults to `unspecified` if file not under git control).
    hash: <hash>

    # The git commit timestamp (defaults to build timestamp if file not under git control).
    timestamp: <timestamp>

  # Variables from the instance variables file.
  vars:
    ...

# (Dynamic) Details of the CURRENT file being rendered.
output_file:
  # The name of the output file.
  name: <name>

  # The relative path (in output directory) of the output file.
  path: <path>

  # Details of the last git commit identified which has had an impact on the content in the
  # generated output file. It is the most LATEST timestamp found amongst:
  #
  # - vars_file_last_commit
  # - template_file.last_commit
  # - instance_file.last_commit
  last_commit:
    # The git commit hash.
    hash: <hash>

    # The git commit timestamp.
    timestamp: <timestamp>
```

The values from the above context can be referenced using standard Jinja2 references. E.g.

    {{ sys.project_version }}

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

project.version = 'git describe --always --dirty'.execute().text.trim()

docsPluginConfig {
    // YAML file containing context variables used when rendering Jinja2 templates.
    // Default: `src/variables.yaml`.
    variablesFile = 'src/my-variables.yaml'

    // Name of the directory (relative to project root) containing the documents to process.
    // Default: `src/docs/`.
    docsDir = 'asciiDocs/'

    // Name of the directory (relative to project root) containing the images.
    // Default: `src/images`.
    imagesDir = 'images/'

    // Position for the Table of Contents. Refer to:
    //  - https://docs.asciidoctor.org/asciidoc/latest/toc/position
    // Default: `left`.
    tocPosition = 'macro'
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
