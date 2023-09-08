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
    /**
     * Set to `true` to auto import brightSPARK Labs Jinja2 macros under `brightsparklabs`
     * namespace. Default: `true`.
     */
    String autoImportMacros= true

    /**
     * Path to a header file (relative to project root) which contains a header to prepend to each
     * Jinja2 file prior to rendering. Default: `src/header.j2`.
     */
    String templateHeaderFile = 'src/header.j2'

    /**
     * Path to a footer file (relative to project root) which contains a footer to append to each
     * Jinja2 file prior to rendering. Default: `src/footer.j2`.
     */
    String templateFooterFile = 'src/footer.j2'

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
     * The value to use at the Asciidoc `title-logo-image` (i.e. cover page logo) attribute in all files.
     * <p>
     * Default:  `image:${DocsPlugin.DEFAULT_LOGO_FILENAME}[pdfwidth=60%,align=right]\n`
     */
    String titleLogoImage = "image:${DocsPlugin.DEFAULT_LOGO_FILENAME}[pdfwidth=60%,align=right]\n"

    /**
     * Modifications that will be made to the default asciidoctorj options for rendering the document.
     * <p>
     * Adding a non-existent key will add the option.<p>
     * Adding an existing key will override the pre-existing option.<p>
     * Adding an existing key with a value of `null` will remove the option.<p>
     * Default: `Map<String,Object> options = [:]`
     */
    Map<String,Object> options = [:]

    /**
     * Modifications that will be made to the list of attributes that will be used by asciidoctor when rendering the documents.
     * <p>
     * Adding a non-existent key will add the attribute.<p>
     * Adding an existing key will override the pre-existing attribute.<p>
     * Adding an existing key with a value of `null` will remove the attribute.<p>
     * Default: `Map<String,Object> attributes = [:]`
     */
    Map<String,Object> attributes = [:]

    /** Configuration options for the website. */
    WebsiteConfiguration website = new WebsiteConfiguration();

    /**
     * Configuration options for the website.
     */
    class WebsiteConfiguration {
        /** Title to display in the website. Default: `Documentation`. */
        String title = 'Documentation'

        /** Email to use in the website. Default: `enquire@brightsparklabs.com`. */
        String email = 'enquire@brightsparklabs.com'

        /** Description to display in the website. Default: The gradle project description. */
        /* NOTE:
         * Defaults the gradle project description in the template files as we do not have access to
         * the project description in this file
         */
        String description = null

        /** The subpath of the site if required (e.g. `/projectX/documentation`). Default: ``. */
        String baseurl = ''

        /** Domain portion of the site if required (e.g. http://projectX.com`. DO NOT include
         * trailing slashes. Default: ``.
         */
        String url = ''

        /**
         * Gem-based Jekyll theme to use when styling the website (e.g. `minimal-mistakes-jekyll`.
         * <p>
         * See https://jekyllrb.com/docs/themes/#understanding-gem-based-themes.
         * <p>Default: `just-the-docs`.
         */
        String theme = 'just-the-docs'
    }
}
