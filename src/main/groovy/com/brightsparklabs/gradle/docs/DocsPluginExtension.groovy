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

    /** Name of the directory (relative to project root) containing the images. Default: `src/images`. */
    String imagesDir = 'src/images/'
}
