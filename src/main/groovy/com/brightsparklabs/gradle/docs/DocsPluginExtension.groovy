/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details.
 */

package com.brightsparklabs.gradle.docs

import java.nio.file.Path

/**
 * Configuration object for the Docs plugin.
 */
class DocsPluginExtension {
    /** YAML file containing context variables used when rendering Jinja2 templates. Default: `src/variables.yaml`. */
    String variablesFile = 'src/variables.yaml'

    /** Name of the directory (relative to project root) containing the documents to process. Default: `src/docs/`. */
    String docsDir = 'src/docs'

    /** Name of the directory (relative to project root) containing the source images. Default: `src/images`. */
    String sourceImagesDir = 'src/images/'

    /** Name of the directory (relative to project root) where the images are copies for processing. Default: `build/docs/images/`. */
    String buildImagesDir = 'build/docs/images/'

    /** Position to place the Table of Contents. Default `left`. */
    String tocPosition = 'left'

    /** Path to the logo file to use as the cover image. Defaults to the BSL logo. */
    Optional<Path> logoFile = Optional.empty()

    /**
     * The value to use at the Asciidoc `title-logo-image` (i.e. cover page logo) attribute in all files.
     * Default:  `image:${DocsPlugin.DEFAULT_LOGO_FILENAME}[pdfwidth=60%,align=right]\n`
     */
    String titleLogoImage = "image:${DocsPlugin.DEFAULT_LOGO_FILENAME}[pdfwidth=60%,align=right]\n"
}
