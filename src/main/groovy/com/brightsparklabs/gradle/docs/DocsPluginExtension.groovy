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

    /** Name of the directory (relative to project root) where the images are copied for processing. Default: `build/docs/images/`. */
    String buildImagesDir = 'build/docs/images/'

    /** Position to place the Table of Contents. Default `left`. */
    String tocPosition = 'left'

    /** Path to the logo file to use as the cover image. Defaults to the BSL logo. */
    Optional<Path> logoFile = Optional.empty()

    /**
     * The value to use at the Asciidoc `title-logo-image` (i.e. cover page logo) attribute in all files.<p>
     * Default:  `image:${DocsPlugin.DEFAULT_LOGO_FILENAME}[pdfwidth=60%,align=right]\n`
     */
    String titleLogoImage = "image:${DocsPlugin.DEFAULT_LOGO_FILENAME}[pdfwidth=60%,align=right]\n"

    /**
     * Modifications that will be made to the default asciidoctorj options for rendering the document.<p>
     * Adding a non-existent key will add the option.<p>
     * Adding an existing key will override the pre-existing option.<p>
     * Adding an existing key with a value of `null` will remove the option.<p>
     * Default: `Map<String,Object> options = [:]`
     */
    Map<String,Object> options = [:]

    /**
     * Modifications that will be made to the list of attributes that will be used by asciidoctor when rendering the documents.<p>
     * Adding a non-existent key will add the attribute.<p>
     * Adding an existing key will override the pre-existing attribute.<p>
     * Adding an existing key with a value of `null` will remove the attribute.<p>
     * Default: `Map<String,Object> attributes = [:]`
     */
    Map<String,Object> attributes = [:]
}
