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

                def now = ZonedDateTime.now()
                Map<String, Object> sysContext = [
                    project_name: project.name,
                    project_description: project.description,
                    project_version: project.version,
                    build_timestamp: now,
                    repo_last_commit: getLastCommit('.', now),
                ]

                // Build Jinja2 context.
                Yaml yaml = new Yaml()
                Map<String, Object> context = [
                    sys: sysContext,
                ]

                File variablesFile = project.file(config.variablesFile)
                if (variablesFile.exists()) {
                    String yamlText = variablesFile.text
                    context.put('vars', yaml.load(yamlText))

                    Map<String, Object> variablesFileLastCommit = getLastCommit(config.variablesFile, now)
                    context.put('vars_file_last_commit', variablesFileLastCommit)
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
                jinjaOutputDir.traverse(type: groovy.io.FileType.FILES) { templateFile ->
                    // Ignore file if it is not a Jinja2 template
                    if (! templateFile.name.endsWith(".j2")) {
                        logger.info("Skipping non-Jinja2 template [${templateFile}]")
                        return
                    }

                    String templateSrcFile = templateFile.getPath().replaceFirst(
                            jinjaOutputDir.toString(),
                            new File(config.docsDir).toString() // NOTE: this cleanly removes trailing slashes
                            )
                    Map<String, Object> templateFileLastCommit = getLastCommit(templateSrcFile, now)
                    String templateOutputFileName = templateFile.getName().replaceFirst(/\.j2$/, '')
                    File templateOutputFile = new File(templateFile.getParent(), templateOutputFileName)
                    Map<String, Object> templateFileContext = [
                        name: templateFile.getName(),
                        // Relative to docs directory.
                        path: templateSrcFile.replaceFirst(config.docsDir + '/?', ''),
                        last_commit: templateFileLastCommit,
                    ]
                    context.put('template_file', templateFileContext)

                    Map<String, Object> outputFileContext = [
                        name: templateOutputFileName,
                        // Relative to output directory.
                        path: templateOutputFile.getPath().replaceFirst(jinjaOutputDir.toString() + '/?', ''),
                        last_commit: templateFileLastCommit,
                    ]
                    context.put('output_file', outputFileContext)

                    // Include file vars if present.
                    File fileVariables = new File(templateFile.getAbsolutePath() + ".yaml")
                    if (fileVariables.exists()) {
                        String fileVariablesYamlText = fileVariables.text
                        templateFileContext.put('vars', yaml.load(fileVariablesYamlText))

                        String fileVariablesSrcFile = templateFile.getPath().replaceFirst(
                                jinjaOutputDir.toString(),
                                new File(config.docsDir).toString() // NOTE: This cleanly removes trailing slashes.
                                ) + '.yaml'
                        Map<String, Object> fileVariablesFileLastCommit = getLastCommit(fileVariablesSrcFile, now)
                        templateFileContext.put('vars_file_last_commit', fileVariablesFileLastCommit)
                        if (fileVariablesFileLastCommit.timestamp.isAfter(templateFileContext.last_commit.timestamp)) {
                            templateFileContext.last_commit = fileVariablesFileLastCommit
                        }
                        fileVariables.delete()
                    }
                    logger.info("Using file context: ${templateFileContext}")

                    // Process instances if present.
                    File instancesDir = new File(templateFile.getAbsolutePath() + ".d")
                    if (instancesDir.exists() && instancesDir.isDirectory()) {
                        instancesDir.traverse(type: groovy.io.FileType.FILES) { instanceFile ->
                            // Ignore file if it is not a yaml file
                            if (! instanceFile.name.endsWith(".yaml")) {
                                logger.info("Skipping non-YAML instance file [${instanceFile}]")
                                return
                            }

                            String instanceSrcFile = instanceFile.getPath().replaceFirst(
                                    jinjaOutputDir.toString(),
                                    new File(config.docsDir).toString() // NOTE: This cleanly removes trailing slashes.
                                    )
                            Map<String, Object> instanceFileLastCommit = getLastCommit(instanceSrcFile, now)
                            // We want to output the file at the same level as the template file.
                            String instanceOutputFileName = instanceFile.getName().replaceFirst(/\.yaml$/, '')
                            File instanceOutputFile = new File(templateFile.getParent(), instanceOutputFileName)
                            Map<String, Object> instanceContext = [
                                name: instanceFile.getName(),
                                // Relative to docs directory.
                                path: instanceSrcFile.replaceFirst(config.docsDir + '/?', ''),
                                last_commit: instanceFileLastCommit,
                            ]
                            context.put('instance_file', instanceContext)

                            outputFileContext.name = instanceOutputFileName
                            outputFileContext.path = instanceOutputFile.getPath().replaceFirst(jinjaOutputDir.toString() + '/?', '')

                            String instanceFileVariablesYamlText = instanceFile.text
                            instanceContext.put('vars', yaml.load(instanceFileVariablesYamlText))

                            logger.info("Using instance context: ${instanceContext}")

                            // Cache current last commit so next instance can cleanly compare.
                            def cachedLastCommit = outputFileContext.last_commit
                            if (instanceFileLastCommit.timestamp.isAfter(cachedLastCommit.timestamp)) {
                                outputFileContext.last_commit = instanceFileLastCommit
                            }

                            logger.debug("Using context: ${context}")
                            instanceOutputFile.text = jinjava.render(templateFile.text, context)

                            // Restore cached last commit so next instance can cleanly compare.
                            templateFileContext.last_commit = cachedLastCommit

                            // Clean out context and instance file.
                            context.remove('instance_file')
                            instanceFile.delete()
                        }
                        instancesDir.delete()
                    }
                    else {
                        // No instances, just process in place.
                        logger.debug("Using context: ${context}")
                        templateOutputFile.text = jinjava.render(templateFile.text, context)
                    }

                    // clean out context and template
                    context.remove('template_file')
                    context.remove('output_file')
                    templateFile.delete()
                }
            }
        }
        project.jinjaPreProcess.dependsOn project.cleanJinjaPreProcess
    }

    /**
     * Gets the last commit for the specified path.
     *
     * @param defaultTimestamp Timestamp to return if the specified file is not under git control.
     *
     * @return Map containing details of the commit as follows:
     *         <pre>
     *         [
     *           hash: <commmit_hash> # default to `unspecified` if path is not under git control.
     *           timestamp: <commmit_timestamp> # default to supplied timestamp if path is not under git control.
     *         ]
     *         </pre>
     */
    private Map<String, Object> getLastCommit(String relativeFilePath, ZonedDateTime defaultTimestamp) {
        final Map<String, Object> result = [:]
        String lastCommitHash = "git log -n 1 --pretty=format:%h -- ${relativeFilePath}".execute().text.trim()
        result.put("hash", lastCommitHash.isEmpty() ? 'unspecified' : lastCommitHash)
        String lastCommitTimestamp = "git log -n 1 --pretty=format:%cI -- ${relativeFilePath}".execute().text.trim()
        result.put("timestamp", lastCommitTimestamp.isEmpty() ? defaultTimestamp : ZonedDateTime.parse(lastCommitTimestamp))
        return result
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
