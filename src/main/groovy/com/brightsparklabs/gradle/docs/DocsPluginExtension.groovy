/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details.
 */

package com.brightsparklabs.gradle.docs

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

    /** The name of the file to be used as the cover image.
     * Default: `bslDocumentationCoverPageLogoColour.svg`
     * Alternative: `bslDocumentationCoverPageLogoBlack.svg` */
    String logoFileName = "bslDocumentationCoverPageLogoColour.svg"

    /** The String to insert into adoc files which defines the configuration of the title-logo-image ( cover image) used with PDF generated documentation. */
    String pdfLogoConfig = 'image:' + logoFileName + '[pdfwidth=3.5in,align=center]\n'
}
