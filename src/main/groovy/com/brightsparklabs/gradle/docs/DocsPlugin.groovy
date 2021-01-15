/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details.
 */

package com.brightsparklabs.gradle.docs

import java.time.ZonedDateTime

import org.gradle.api.Project
import org.gradle.api.Plugin

import com.hubspot.jinjava.Jinjava
import com.hubspot.jinjava.JinjavaConfig
import org.yaml.snakeyaml.Yaml

/**
 * The brightSPARK Labs Docs Plugin.
 */
public class DocsPlugin implements Plugin<Project> {

    // -------------------------------------------------------------------------
    // CONSTANTS
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // IMPLEMENTATION: Plugin<Project>
    // -------------------------------------------------------------------------

    @Override
    public void apply(Project project) {
        // Create plugin configuration object.
        def config = project.extensions.create('docsPluginConfig', DocsPluginExtension)

        File jinjaOutputDir = project.file('build/jinjaProcessed')

        setupJinjaPreProcessingTasks(project, config, jinjaOutputDir)
        setupAsciiDoctor(project, config, jinjaOutputDir)
    }

    // --------------------------------------------------------------------------
    // PRIVATE METHODS
    // -------------------------------------------------------------------------

    /**
     * Adds the Jinja2 pre-processing task.
     *
     * @param project Gradle project to add the task to.
     * @param config Configuration for this plugin.
     * @param jinjaOutputDir Directory to output rendered Jinja2 templates.
     */
    private void setupJinjaPreProcessingTasks(Project project, DocsPluginExtension config, File jinjaOutputDir) {
        project.task('jinjaPreProcess') {
            group = "brightSPARK Labs - Docs"
            description = "Performs Jinja2 pre-processing on documents"

            doLast {
                // Copy the entire directory and render files in-place to make
                // keeping output folder structures intact easier.
                jinjaOutputDir.delete()
                project.copy {
                    from project.file(config.docsDir)
                    into jinjaOutputDir
                }

                // Build Jinja2 context.
                Yaml yaml = new Yaml()
                String yamlText = project.file(config.variablesFile).text
                Map<String, Object> context = [
                    vars: yaml.load(yamlText),
                    sys: [
                        name: project.name,
                        description: project.description,
                        version: project.version,
                        build_timestamp: ZonedDateTime.now(),
                    ]
                ]

                // Process templates.
                JinjavaConfig jinjavaConfig = JinjavaConfig.newBuilder()
                        // Fail if templates reference non-existent variables
                        //.withFailOnUnknownTokens(true)
                        .build();
                Jinjava jinjava = new Jinjava(jinjavaConfig)
                jinjaOutputDir.traverse(type: groovy.io.FileType.FILES) { file ->
                    // Start with clean file variables each time.
                    context.remove('file_vars')

                    // Ignore file if it is not a Jinja2 template
                    if (file.name.endsWith(".j2")) {
                        // Include file vars if present.
                        File fileVariables = new File(file.getAbsolutePath() + ".yaml")
                        if (fileVariables.exists()) {
                            String fileVariablesYamlText = fileVariables.text
                            context.put('file_vars', yaml.load(fileVariablesYamlText))
                        }

                        // Render template and drop the '.j2' extension
                        file.text = jinjava.render(file.text, context)
                        file.renameTo(file.path.replace(".j2", ""))
                    }
                }
            }
        }
    }

    /**
     * Adds and configures the Asciidoctor Gradle plugins.
     *
     * @param project Gradle project to add the task to.
     * @param config Configuration for this plugin.
     * @param jinjaOutputDir Directory to output rendered Jinja2 templates.
     */
    private void setupAsciiDoctor(Project project, DocsPluginExtension config, File jinjaOutputDir) {
        project.plugins.apply 'org.asciidoctor.jvm.convert'
        project.plugins.apply 'org.asciidoctor.jvm.pdf'

        if (! project.tasks.findByName('build')) {
            project.task('build') {
                group = "brightSPARK Labs - Docs"
                description = "Builds the documentation."
            }
        }
        project.build.dependsOn project.asciidoctor
        project.asciidoctor.dependsOn project.jinjaPreProcess

        // creating aliases nested under our BSL group for clarity
        project.task('bslAsciidoctor') {
            group = "brightSPARK Labs - Docs"
            description = "Alias for `asciidoctor` task."
        }
        project.bslAsciidoctor.dependsOn project.asciidoctor

        project.task('bslAsciidoctorPdf') {
            group = "brightSPARK Labs - Docs"
            description = "Alias for `asciidoctorPdf` task."
        }
        project.bslAsciidoctorPdf.dependsOn project.asciidoctorPdf

        project.afterEvaluate {
            project.asciidoctor {
                sourceDir jinjaOutputDir
                outputOptions {
                    backends 'pdf', 'html'
                }

                // ensures includes are relative to each file
                baseDirFollowsSourceFile()

                asciidoctorj {
                    // 'book' adds a cover page to the PDF
                    options doctype: 'book'

                    /*
                     * 'chapter-label': ''  -> do not prefix headings with anything
                     * 'icons'': 'font'     -> use Font Awesome for admonitions
                     * 'imagesdir'':        -> directory to resolve images from
                     * 'numbered'           -> numbers all headings
                     * 'source-highlighter' -> add syntax highlighting to source blocks
                     * 'toc': 'left'        -> places TOC on left hand site in HTML pages
                     */
                    attributes \
                        'chapter-label'      : '',
                            'icons'              : 'font',
                            'imagesdir'          : project.file(config.imagesDir),
                            'numbered'           : '',
                            'source-highlighter' : 'coderay',
                            'toc'                : 'left'
                }
            }
        }
    }
}
