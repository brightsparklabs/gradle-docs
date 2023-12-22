# gradle-docs

[![Build Status](https://github.com/brightsparklabs/gradle-docs/actions/workflows/unit_tests.yml/badge.svg)](https://github.com/brightsparklabs/gradle-docs/actions/workflows/unit_tests.yml)
[![Gradle Plugin](https://img.shields.io/gradle-plugin-portal/v/com.brightsparklabs.gradle.docs)](https://plugins.gradle.org/plugin/com.brightsparklabs.gradle.docs)

Applies brightSPARK Labs standardisation to project documentation.

## Compatibility

| Plugin Version | Gradle Version | Java Version
| -------------- | -------------- | ------------
| 3.x.y          | 8.x.y          | 17
| 2.x.y          | 7.x.y          | 17
| 1.x.y          | 6.x.y          | 11

## Build

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
    mavenCentral()
}
```

By default:

- AsciiDoc files should be stored under `src/docs/`.
- Images should be stored under `src/images`, which can then be referenced via [AsciiDoc image
  macros](https://docs.asciidoctor.org/asciidoc/latest/macros/images/).
- Any files ending with `.j2` will first be processed by
  [Jinjava](https://github.com/HubSpot/jinjava) (Java port of Python Jinja2).
- A number of Jinja2 macros are added by default. See
  [brightSPARK Labs Jinja2 Macros](#brightspark-labs-jinja2-macros).

Running `./gradlew build` will generate PDF and HTML of the documentation.

If `docker buildx` is present on the system, then in addition to the AsciiDoc HTML backend output,
it will also generate a Jekyll based static website.

### Tasks

The tasks added by this plugin appear under the `BrightSPARK Labs - Docs tasks` group. E.g.

    $ ./gradlew task | sed -n '/BrightSPARK/,/^$/p's

    BrightSPARK Labs - Docs tasks
    -----------------------------
    bslAsciidoctor - Alias for `asciidoctor` task.
    bslAsciidoctorPdf - Alias for `asciidoctorPdf` task.
    bslAsciidoctorPdfVersioned - Creates PDF files with version string in filename
    cleanJekyllWebsite - Cleans the Jekyll website out of the build directory
    cleanJinjaPreProcess - Cleans the Jinja2 processed documents out of the build directory
    generateJekyllWebsite - Generates a Jekyll based website from the documents
    jinjaPreProcess - Performs Jinja2 pre-processing on documents

NOTE: `generateJekyllWebsite` is only available if `docker buildx` is present on the system.

### Jinja2 Context Map

The context provided to the Jinja2 rendering engine has the following format:

```
# ------------------------------------------------------------------------------
# System variables.
# ------------------------------------------------------------------------------

sys:
  # The `project.name` set in Gradle.
  project_name: <name>

  # The `project.description` set in Gradle.
  project_description: <description>

  # The `project.version` set in Gradle.
  project_version: <version>

  # The `project.projectDir.toPath()` set in Gradle.
  project_path: <path>

  # The time the build was run as ZonedDateTime.
  build_timestamp: <timestamp>

  # The time the build was run in different string formats.
  build_timestamp_formatted:
    # Timestamp in UTC as an ISO8601 string.
    iso_utc: <timestamp>

    # Timestamp in UTC as an ISO8601 string with `T` replaced by a space.
    iso_utc_space: <timestamp>

    # Timestamp in UTC as an ISO8601 string with `:` removed (safe for Windows file systems).
    iso_utc_safe: <timestamp>

    # Timestamp with offset as an ISO8601 string.
    iso_offset: <timestamp>

    # Timestamp with offset as an ISO8601 string with `T` replaced by a space.
    iso_offset_space: <timestamp>

    # Timestamp with offset as an ISO8601 string with `:` removed (safe for Windows file systems).
    iso_offset_safe: <timestamp>

  # Details of the last git commit on the repo.
  repo_last_commit:
    # The git commit hash (defaults to `unspecified` if project not under git control).
    hash: <hash>

    # The git commit timestamp as ZonedDateTime (defaults to build timestamp if project not under git control).
    timestamp: <timestamp>

    # The git commit timestamp in different string formats.
    timestamp_formatted:
      # Timestamp in UTC as an ISO8601 string.
      iso_utc: <timestamp>

      # Timestamp in UTC as an ISO8601 string with `T` replaced by a space.
      iso_utc_space: <timestamp>

      # Timestamp in UTC as an ISO8601 string with `:` removed (safe for Windows file systems).
      iso_utc_safe: <timestamp>

      # Timestamp with offset as an ISO8601 string.
      iso_offset: <timestamp>

      # Timestamp with offset as an ISO8601 string with `T` replaced by a space.
      iso_offset_space: <timestamp>

      # Timestamp with offset as an ISO8601 string with `:` removed (safe for Windows file systems).
      iso_offset_safe: <timestamp>


# ------------------------------------------------------------------------------
# User defined variables from global variables YAML file (default: `src/variables.yaml`).
# ------------------------------------------------------------------------------

vars:
  ...


# ------------------------------------------------------------------------------
# Details of the last git commit on the global variables YAML file.
# ------------------------------------------------------------------------------

vars_file_last_commit:
  # The git commit hash (defaults to `unspecified` if file not under git control).
  hash: <hash>

  # The git commit timestamp as ZonedDateTime (defaults to build timestamp if file not under git control).
  timestamp: <timestamp>

  # The git commit timestamp in different string formats.
  timestamp_formatted:
    # Timestamp in UTC as an ISO8601 string.
    iso_utc: <timestamp>

    # Timestamp in UTC as an ISO8601 string with `T` replaced by a space.
    iso_utc_space: <timestamp>

    # Timestamp in UTC as an ISO8601 string with `:` removed (safe for Windows file systems).
    iso_utc_safe: <timestamp>

    # Timestamp with offset as an ISO8601 string.
    iso_offset: <timestamp>

    # Timestamp with offset as an ISO8601 string with `T` replaced by a space.
    iso_offset_space: <timestamp>

    # Timestamp with offset as an ISO8601 string with `:` removed (safe for Windows file systems).
    iso_offset_safe: <timestamp>


# ------------------------------------------------------------------------------
# (Dynamic) Variables pertaining to the CURRENT template being rendered.
# ------------------------------------------------------------------------------

template_file:
  # The name of the source template file.
  name: <name>

  # The relative path (in docs directory) of the source template file.
  path: <path>

  # Details of the last git commit on the template file.
  last_commit:
    # The git commit hash (defaults to `unspecified` if file not under git control).
    hash: <hash>

    # The git commit timestamp as ZonedDateTime (defaults to build timestamp if file not under git control).
    timestamp: <timestamp>

    # The git commit timestamp in different string formats.
    timestamp_formatted:
      # Timestamp in UTC as an ISO8601 string.
      iso_utc: <timestamp>

      # Timestamp in UTC as an ISO8601 string with `T` replaced by a space.
      iso_utc_space: <timestamp>

      # Timestamp in UTC as an ISO8601 string with `:` removed (safe for Windows file systems).
      iso_utc_safe: <timestamp>

      # Timestamp with offset as an ISO8601 string.
      iso_offset: <timestamp>

      # Timestamp with offset as an ISO8601 string with `T` replaced by a space.
      iso_offset_space: <timestamp>

      # Timestamp with offset as an ISO8601 string with `:` removed (safe for Windows file systems).
      iso_offset_safe: <timestamp>

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

    # The git commit timestamp as ZonedDateTime (defaults to build timestamp if file not under git control).
    timestamp: <timestamp>

    # The git commit timestamp in different string formats.
    timestamp_formatted:
      # Timestamp in UTC as an ISO8601 string.
      iso_utc: <timestamp>

      # Timestamp in UTC as an ISO8601 string with `T` replaced by a space.
      iso_utc_space: <timestamp>

      # Timestamp in UTC as an ISO8601 string with `:` removed (safe for Windows file systems).
      iso_utc_safe: <timestamp>

      # Timestamp with offset as an ISO8601 string.
      iso_offset: <timestamp>

      # Timestamp with offset as an ISO8601 string with `T` replaced by a space.
      iso_offset_space: <timestamp>

      # Timestamp with offset as an ISO8601 string with `:` removed (safe for Windows file systems).
      iso_offset_safe: <timestamp>


# ------------------------------------------------------------------------------
# (Dynamic) Variables pertaining to the CURRENT directory of the template being rendered.
# ------------------------------------------------------------------------------

template_dir:
  # The relative path (in docs directory) of the directory containing the source template file.
  path: <path>

  # User defined variables from directory variables YAML file (`./variables.yaml`).
  vars:
    ...


# ------------------------------------------------------------------------------
# (Dynamic) User defined variables from the CURRENT instance variable file being processed (if
# present).
# ------------------------------------------------------------------------------

# Instance variable files must be stored under a directory with the same name as the Jinja2 template
# file with `.d` appended. Each `.yaml` file under this directory will be rendered against the
# corresponding Jinja2 template file.
#
# E.g.
#
#   src/docs/sops/sop-template.j2                             -> Jinja2 template file.
#   src/docs/sops/sop-template.j2.d/                          -> Instance variables directory.
#   src/docs/sops/sop-template.j2.d/restart-servers.adoc.yaml -> Instance variables file.
#   src/docs/sops/sop-template.j2.d/purge-logs.adoc.yaml      -> Instance variables file.
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

    # The git commit timestamp as ZonedDateTime (defaults to build timestamp if file not under git control).
    timestamp: <timestamp>

    # The git commit timestamp in different string formats.
    timestamp_formatted:
      # Timestamp in UTC as an ISO8601 string.
      iso_utc: <timestamp>

      # Timestamp in UTC as an ISO8601 string with `T` replaced by a space.
      iso_utc_space: <timestamp>

      # Timestamp in UTC as an ISO8601 string with `:` removed (safe for Windows file systems).
      iso_utc_safe: <timestamp>

      # Timestamp with offset as an ISO8601 string.
      iso_offset: <timestamp>

      # Timestamp with offset as an ISO8601 string with `T` replaced by a space.
      iso_offset_space: <timestamp>

      # Timestamp with offset as an ISO8601 string with `:` removed (safe for Windows file systems).
      iso_offset_safe: <timestamp>

  # Variables from the instance variables file.
  vars:
    ...


# ------------------------------------------------------------------------------
# (Dynamic) Details of the CURRENT file being rendered.
# ------------------------------------------------------------------------------

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

    # The git commit timestamp as ZonedDateTime.
    timestamp: <timestamp>

    # The git commit timestamp in different string formats.
    timestamp_formatted:
      # Timestamp in UTC as an ISO8601 string.
      iso_utc: <timestamp>

      # Timestamp in UTC as an ISO8601 string with `T` replaced by a space.
      iso_utc_space: <timestamp>

      # Timestamp in UTC as an ISO8601 string with `:` removed (safe for Windows file systems).
      iso_utc_safe: <timestamp>

      # Timestamp with offset as an ISO8601 string.
      iso_offset: <timestamp>

      # Timestamp with offset as an ISO8601 string with `T` replaced by a space.
      iso_offset_space: <timestamp>

      # Timestamp with offset as an ISO8601 string with `:` removed (safe for Windows file systems).
      iso_offset_safe: <timestamp>


# ------------------------------------------------------------------------------
# Plugin configuration.
# ------------------------------------------------------------------------------

config:
  # The `docsPluginConfig` object as defined in the `Configuration` section below.
  ...
```

The values from the above context can be referenced using standard Jinja2 references. E.g.

    {{ sys.project_version }}

    {{ output_file.last_commit.timestamp | datetimeformat("%Y-%m-%d at %H:%M %Z", "Australia/Sydney") }}

    {{ output_file.last_commit.timestamp_formatted.iso_utc_space }}

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
    /**
     * Set to `true` to auto import brightSPARK Labs Jinja2 macros under `brightsparklabs`
     * namespace. Default: `true`.
     */
    autoImportMacros = false

    /**
     * Path to a header file (relative to project root) which contains a header to prepend to each
     * Jinja2 file prior to rendering. Default: `src/header.j2`.
     */
    templateHeaderFile = 'src/my-custom-header.j2'

    /**
     * Path to a footer file (relative to project root) which contains a footer to append to each
     * Jinja2 file prior to rendering. Default: `src/footer.j2`.
     */
    templateFooterFile = 'src/my-custom-footer.j2'

    // YAML file containing context variables used when rendering Jinja2 templates.
    // Default: `src/variables.yaml`.
    variablesFile = 'src/my-variables.yaml'

    // Name of the directory (relative to project root) containing the documents to process.
    // Default: `src/docs/`.
    docsDir = 'asciiDocs/'

    // Name of the directory (relative to project root) containing the source images.
    // Default: `src/images`.
    sourceImagesDir = 'images/'

    // Name of the directory (relative to project root) where the images are copied for processing.
    // Default: `build/docs/images/`.
    buildImagesDir = 'build/images/'

    // Position for the Table of Contents. Refer to:
    //  - https://docs.asciidoctor.org/asciidoc/latest/toc/position
    // Default: `left`.
    tocPosition = 'macro'

    // Path to the logo file to use as the cover image.
    // Default: `Optional.empty()`.
    logoFile = Optional.of(Path.get("src/custom-logo.svg"))

    // The value to use at the Asciidoc `title-logo-image` (i.e. cover page logo) attribute in all files.
    // Default: `image:${DocsPlugin.DEFAULT_LOGO_FILENAME}[pdfwidth=60%,align=left]\n`.
    titleLogoImage = "image:${DocsPlugin.DEFAULT_LOGO_FILENAME}[pdfwidth=30%,align=right]\n"

    // Modifications that will be made to the default asciidoctorj options for rendering the document.
    // Adding a non-existent key will add the option.
    // Adding an existing key will override the pre-existing option.
    // Adding an existing key with a value of `null` will remove the option.
    // Default: `["doctype" : 'book']`
    options = ["doctype" : 'article']


    // Modifications that will be made to the list of attributes that will be used by asciidoctor when rendering the documents.
    // Adding a non-existent key will add the attribute.
    // Adding an existing key will override the pre-existing attribute.
    // Adding an existing key with a value of `null` will remove the attribute.
    // Default: `[
    //           'chapter-label@'       : '',
    //           'icon-set@'            : 'fas',
    //           'icons@'               : 'font',
    //           'imagesdir@'           : buildImagesDir,
    //           'numbered@'            : '',
    //           'source-highlighter@'  : 'coderay',
    //           'title-logo-image@'    : titleLogoImage,
    //           'toc@'                 : tocPosition
    //           ]`.
    attributes = [
        'chapter-label@'    : 'Chapter',
        'toc@'              : null
    ]

   // Configuration for website generation.
   // NOTE: Website generation only available when `docker buildx` is present on system.

   // Title to display in the website. Default: `Documentation`.
   website.title = 'My Documentation'

   // Email to use in the website. Default: `enquire@brightsparklabs.com`.
   website.email = 'email@me.dev'

   // Description to display in the website. Default: The gradle project description.
   website.description = 'Documentation explaining how Project X operates.'

   // The subpath of the site if required. Default: ``.
   website.baseurl = '/projectX/documentation'

   // Domain portion of the site if required. DO NOT include trailing slashes. Default: ``.
   website.url = 'http://projectX.com'

   // Gem-based Jekyll theme to use when styling the website.
   // See https://jekyllrb.com/docs/themes/#understanding-gem-based-themes.
   // Default: `just-the-docs`.
   website.theme = 'minimal-mistakes-jekyll'
}
```

### brightSPARK Labs Jinja2 Macros

If the configuration field `autoImportMacros` is set to `true` (default) then the following
macros shall be be available under the `brightsparklabs` namespace:

- `add_default_attributes()` - Adds the standard set of AsciiDoc attributes to the document.

These can be used as follows:

    {{ brightsparklabs.add_default_attributes() }}

Macros are defined in:

    src/main/resources/brightsparklabs-macros.j2

### Asciidoctorj Diagram

The [`asciidoctorj-diagram`](https://github.com/asciidoctor/asciidoctorj-diagram) can be enabled
as [per standard
practice](https://asciidoctor.github.io/asciidoctor-gradle-plugin/development-3.x/user-guide/#diagram)
in `build.gradle`:

```groovy
// Support asciidoctor-diagram image generation.
asciidoctorj {
    modules {
        diagram.use()
    }
}
```

In order to make use of the various diagramming formats, the backing tool needs to be installed on
the system. E.g.

- `graphviz`/`plantuml` requires [graphviz](https://graphviz.org/) `dot` installed.
- `vega` requires [Vega](https://vega.github.io/vega/) installed.
- etc.

## Jekyll Website

By default the generated Jekyll website uses the [Just the Docs](https://just-the-docs
.com/docs/navigation-structure/) theme.

By default all pages will appear as top level pages in the main navigation. If you want to setup
nested navigation, you will need to set that up explicitly as detailed in the [Navigation
Structure](https://just-the-docs.com/docs/navigation-structure/) documentation.

A basic example is:

```
$ tree

src
└── docs
    ├── data-model
    │   ├── component-x-data-model.adoc.j2
    │   ├── component-x-data-model.adoc.j2.yaml
    │   └── index.adoc.j2
    └── index.adoc.j2

$ cat src/docs/index.adoc.j2
= Data Model
brightSPARK Labs <enquire@brightsparklabs.com>
{{ brightsparklabs.add_default_attributes() }}
:page-has_children: true

$ head src/docs/component-x-data-model.adoc.j2
= Component X Data Model
brightSPARK Labs <enquire@brightsparklabs.com>
{{ brightsparklabs.add_default_attributes() }}
:page-parent: Data Model
```

The attributes to note:

* `:page-has_children: true` on the parent page to indicate supports nested pages.
* `:page-parent: Data Model` on the child page to nest it under the parent page.

**NOTE: Just the Docs only supports a maximum of 3 levels of nesting.**

### Website Dockerfile

A Dockerfile for building the website can be generated via the `generateDockerfile` task.
This can be leveraged to work with the [gradle-docker](https://github.com/brightsparklabs/gradle-docker)
plugin by doing the following:

* Make the `gradle-docker` plugin's `buildDockerImages` task depend on this plugin's
  `generateDockerfile` task.
* Add the path of the generated Dockerfile to the `gradle-docker` plugin's configuration block in
  the `dockerFileDefinitions` list.

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
