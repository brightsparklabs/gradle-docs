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
        project.task('cleanJinjaPreProcess') {
            group = "brightSPARK Labs - Docs"
            description = "Cleans the Jinja2 processed documents out of the build directory"

            doLast {
                jinjaOutputDir.delete()
            }
        }
        // Use `afterEvaluate` in case another task add the `clean` task.
        project.afterEvaluate {
            if (! project.tasks.findByName('cleand')) {
                project.task('cleadn') {
                    group = "brightSPARK Labs - Docs"
                    description = "Cleans the documentation."
                }
            }
            project.clean.dependsOn project.cleanJinjaPreProcess
        }

        project.task('jinjaPreProcess') {
            group = "brightSPARK Labs - Docs"
            description = "Performs Jinja2 pre-processing on documents"

            doLast {
                // Copy the entire directory and render files in-place to make
                // keeping output folder structures intact easier.
                jinjaOutputDir.delete()
                jinjaOutputDir.mkdirs()
                project.copy {
                    from project.file(config.docsDir)
                    into jinjaOutputDir
                }

                Map<String, Object> sysContext = [
                    project_name: project.name,
                    project_description: project.description,
                    project_version: project.version,
                    build_timestamp: ZonedDateTime.now(),
                ]
                String repoLastCommitHash = "git log -n 1 --pretty=format:%h".execute().text.trim()
                if (! repoLastCommitHash.isEmpty()) {
                    sysContext.put("last_commit_hash", repoLastCommitHash)
                }
                String repoLastCommitTimestamp = "git log -n 1 --pretty=format:%cI".execute().text.trim()
                if (! repoLastCommitHash.isEmpty()) {
                    sysContext.put("last_commit_timestamp", ZonedDateTime.parse(repoLastCommitTimestamp))
                }

                // Build Jinja2 context.
                Yaml yaml = new Yaml()
                Map<String, Object> context = [
                    sys: sysContext,
                ]

                File variablesFile = project.file(config.variablesFile)
                if (variablesFile.exists()) {
                    String yamlText = variablesFile.text
                    context.put('vars', yaml.load(yamlText))
                }
                else {
                    logger.warn("Not adding global variables to Jinja2 context as no file found at [${variablesFile}]")
                }

                // Process templates.
                JinjavaConfig jinjavaConfig = JinjavaConfig.newBuilder()
                        // Fail if templates reference non-existent variables
                        .withFailOnUnknownTokens(true)
                        .build();
                Jinjava jinjava = new Jinjava(jinjavaConfig)
                jinjaOutputDir.traverse(type: groovy.io.FileType.FILES) { file ->
                    // Start with clean file context each time.
                    context.remove('file')

                    // Ignore file if it is not a Jinja2 template
                    if (! file.name.endsWith(".j2")) {
                        logger.info("Skipping non-Jinja2 template [${file}]")
                        return
                    }

                    String srcFile = file.getPath().replaceFirst(
                            jinjaOutputDir.toString(),
                            new File(config.docsDir).toString() // NOTE: this cleanly removes trailing slashes
                            )
                    Map<String, Object> fileContext = [
                        name: file.getName().replaceFirst(/\.j2$/, ""),
                        path: srcFile.toString().replaceFirst(/\.j2$/, ""),
                        src_name: file.getName(),
                        src_path: srcFile.toString()
                    ]

                    String fileLastCommitHash = "git log -n 1 --pretty=format:%h -- ${srcFile}".execute().text.trim()
                    if (! fileLastCommitHash.isEmpty()) {
                        fileContext.put("last_commit_hash", fileLastCommitHash)
                    }
                    String fileLastCommitTimestamp = "git log -n 1 --pretty=format:%cI -- ${srcFile}".execute().text.trim()
                    if (! fileLastCommitHash.isEmpty()) {
                        fileContext.put("last_commit_timestamp", ZonedDateTime.parse(fileLastCommitTimestamp))
                    }

                    // Include file vars if present.
                    File fileVariables = new File(file.getAbsolutePath() + ".yaml")
                    if (fileVariables.exists()) {
                        String fileVariablesYamlText = fileVariables.text
                        fileContext.put('vars', yaml.load(fileVariablesYamlText))
                    }

                    context.put('file', fileContext)

                    logger.info("Using file context: ${fileContext}")
                    logger.debug("Using context: ${context}")

                    // Render template and drop the '.j2' extension
                    file.text = jinjava.render(file.text, context)
                    file.renameTo(file.path.replaceFirst(/\.j2$/, ""))
                }
            }
        }
        project.jinjaPreProcess.dependsOn project.cleanJinjaPreProcess
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

        // Use `afterEvaluate` in case another task add the `build` task.
        project.afterEvaluate {
            if (! project.tasks.findByName('build')) {
                project.task('build') {
                    group = "brightSPARK Labs - Docs"
                    description = "Builds the documentation."
                }
            }
            project.build.dependsOn project.asciidoctor
            project.asciidoctor.dependsOn project.jinjaPreProcess
        }

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
