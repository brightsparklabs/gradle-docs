/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details.
 */

package com.brightsparklabs.gradle.docs

import com.hubspot.jinjava.Jinjava
import com.hubspot.jinjava.JinjavaConfig
import groovy.io.FileType
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The brightSPARK Labs Docs Plugin.
 */
public class DocsPlugin implements Plugin<Project> {
    // -------------------------------------------------------------------------
    // CONSTANTS
    // -------------------------------------------------------------------------

    /** Name of the default logo file in the resources directory. This is also used as the
     * destination file name when copying client supplied logos. */
    public static final String DEFAULT_LOGO_FILENAME = 'cover-page-logo.svg'

    // -------------------------------------------------------------------------
    // INSTANCE VARIABLES
    // -------------------------------------------------------------------------

    // NOTE: Using closure because DumperOptions has no builder and we need to
    //       build instance variables in one statement.
    /** Yaml parser. */
    Yaml yaml = { _ ->
        def yamlOptions = new DumperOptions();
        yamlOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        yamlOptions.setPrettyFlow(true);
        return new Yaml(yamlOptions)
    }()

    /** Jinja2 processor. */
    Jinjava jinjava = { _ ->
        JinjavaConfig jinjavaConfig = JinjavaConfig.newBuilder()
                // Fail if templates reference non-existent variables
                .withFailOnUnknownTokens(true)
                .build();
        return new Jinjava(jinjavaConfig)
    }()

    /** Output asciidoc files mapped to the context which created them. */
    Map<File, Map<String, Object>> outputFileToContextMap = [:]

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
                project.delete jinjaOutputDir
                // Delete asciidoctor generated documents else renamed/deleted docs  may remain.
                def asciidoctorOutputDir = project.file("build/docs")
                project.delete asciidoctorOutputDir
            }
        }
        // Use `afterEvaluate` in case another task add the `clean` task.
        project.afterEvaluate {
            if (! project.tasks.findByName('clean')) {
                project.task('clean') {
                    group = "brightSPARK Labs - Docs"
                    description = "Cleans the documentation."
                }
            }
            project.clean.dependsOn project.cleanJinjaPreProcess
        }

        project.task('jinjaPreProcess') {
            group = "brightSPARK Labs - Docs"
            description = "Performs Jinja2 pre-processing on documents"

            /* Calculate the relative path of this project's directory (i.e. the directory
             * containing this project's `build.gradle` file) relative to the repo root.
             *
             * Generally the realtive path will simply by blank (i.e. `build.gradle` resides at the
             * root of the repo). However, if the project is part of a Gradle multi-project build,
             * then this will not be the case.
             *
             * This is variable is needed so that file path can be built for use in `git` commands
             * which reference files in relation to the repo's root directory.
             */
            String projectRelativePath = "";
            if (!(project.projectDir.toString() == project.rootDir.toString())) {
                // This is a subproject in a Gradle multi-project build. Calculate relative path.
                // The `/` subtract/add ensures the string ends with a slash.
                projectRelativePath = project.projectDir.toString() - project.rootDir.toString() - "/" + "/"
            }

            doLast {
                // Copy the entire directory and render files in-place to make
                // keeping output folder structures intact easier.
                jinjaOutputDir.delete()
                jinjaOutputDir.mkdirs()
                project.copy {
                    from project.file(config.docsDir)
                    into jinjaOutputDir
                }

                project.file(config.buildImagesDir).delete()
                project.file(config.buildImagesDir).mkdirs()
                project.copy{
                    from project.file(config.sourceImagesDir)
                    into project.file(config.buildImagesDir)
                }

                // Copy logo into build directory so it can be referenced in Asciidoc.
                final Path outputFile = Paths.get("${project.projectDir}/${config.buildImagesDir}/${DEFAULT_LOGO_FILENAME}")
                try {
                    final logoBytes = config.logoFile
                            .map {path -> path.toFile().readBytes() }
                            .orElse(getClass().getResourceAsStream("/${DEFAULT_LOGO_FILENAME}").readAllBytes());
                    outputFile.withOutputStream {stream -> stream.write(logoBytes)}
                } catch(Exception ex) {
                    logger.error("Could not copy logo file to build directory", ex)
                    throw ex
                }

                def now = ZonedDateTime.now()
                Map<String, Object> sysContext = [
                    project_name: project.name,
                    project_description: project.description,
                    project_version: project.version,
                    build_timestamp: now,
                    build_timestamp_formatted: getFormattedTimestamps(now),
                    repo_last_commit: getLastCommit('.', now),
                ]

                // Build Jinja2 context.
                Map<String, Object> context = [
                    sys: sysContext,
                ]

                File variablesFile = project.file(config.variablesFile)
                if (variablesFile.exists()) {
                    String yamlText = variablesFile.text
                    context.put('vars', yaml.load(yamlText))

                    Map<String, Object> variablesFileLastCommit = getLastCommit(projectRelativePath + config.variablesFile, now)
                    context.put('vars_file_last_commit', variablesFileLastCommit)
                }
                else {
                    logger.warn("Not adding global variables to Jinja2 context as no file found at [${variablesFile}]")
                }

                // Process templates.
                jinjaOutputDir.traverse(type: FileType.FILES) { templateFile ->
                    // Ignore file if it is not a Jinja2 template
                    if (! templateFile.name.endsWith(".j2")) {
                        logger.info("Skipping non-Jinja2 template [${templateFile}]")
                        return
                    }

                    String templateSrcFile = templateFile.getPath().replaceFirst(
                            jinjaOutputDir.toString(),
                            new File(config.docsDir).toString() // NOTE: this cleanly removes trailing slashes
                            )
                    Map<String, Object> templateFileLastCommit = getLastCommit(projectRelativePath + templateSrcFile, now)
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
                        Map<String, Object> fileVariablesFileLastCommit = getLastCommit(projectRelativePath + fileVariablesSrcFile, now)
                        templateFileContext.put('vars_file_last_commit', fileVariablesFileLastCommit)
                        // Make sure that if we want to replace the commit hash that it actually has a commit hash to replace it with.
                        if (fileVariablesFileLastCommit.timestamp.isAfter(templateFileContext.last_commit.timestamp) && fileVariablesFileLastCommit.hash != "unspecified") {
                            outputFileContext.last_commit = fileVariablesFileLastCommit
                        }
                        fileVariables.delete()
                    }
                    logger.info("Using `template_file` context:\n${yaml.dump(templateFileContext)}")

                    // Process instances if present.
                    File instancesDir = new File(templateFile.getAbsolutePath() + ".d")
                    if (instancesDir.exists() && instancesDir.isDirectory()) {
                        instancesDir.traverse(type: FileType.FILES) { instanceFile ->
                            // Ignore file if it is not a yaml file
                            if (! instanceFile.name.endsWith(".yaml")) {
                                logger.info("Skipping non-YAML instance file [${instanceFile}]")
                                return
                            }

                            String instanceSrcFile = instanceFile.getPath().replaceFirst(
                                    jinjaOutputDir.toString(),
                                    new File(config.docsDir).toString() // NOTE: This cleanly removes trailing slashes.
                                    )
                            Map<String, Object> instanceFileLastCommit = getLastCommit(projectRelativePath + instanceSrcFile, now)
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

                            logger.info("Using `instance_file` context :\n${yaml.dump(instanceContext)}")

                            // Cache current last commit so next instance can cleanly compare.
                            def cachedLastCommit = outputFileContext.last_commit
                            if (instanceFileLastCommit.timestamp.isAfter(cachedLastCommit.timestamp) && instanceFileLastCommit.hash != "unspecified"){
                                outputFileContext.last_commit = instanceFileLastCommit
                            }

                            logger.debug("Using context:\n${yaml.dump(context)}")
                            writeTemplateToFile(templateFile, context, instanceOutputFile)

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
                        logger.debug("Using context:\n${yaml.dump(context)}")
                        writeTemplateToFile(templateFile, context, templateOutputFile)
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
     * Renders the supplied template against the supplied context and writes
     * it to an output file.
     *
     * @param templateFile Template to render.
     * @param context Context to use to fill the template.
     * @param outputFile File to write the result to.
     */
    private void writeTemplateToFile(File templateFile, Map<String, Object> context, File outputFile) {
        try {
            // Create an snapshot of the context since it is mutable.
            def snapshot = context.getClass().newInstance(context)
            outputFileToContextMap[outputFile] = snapshot

            // Render template to file.
            outputFile.text = jinjava.render(templateFile.text, context)
        } catch(Exception ex) {
            throw new Exception("Could not process [${templateFile}] - ${ex.message}")
        }
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
        final Map<String, Object> result = [
            hash: 'unspecified',
            timestamp: defaultTimestamp,
            timestamp_formatted: getFormattedTimestamps(defaultTimestamp),
        ]

        // If file is dirty, then use current time.
        def checkFileDirtyCommand = 'git diff --shortstat --'.tokenize()
        checkFileDirtyCommand << relativeFilePath
        String checkFileDirty = checkFileDirtyCommand.execute().text.trim()
        if (! checkFileDirty.isEmpty()) {
            return result
        }

        // NOTE: Use array execution instead of string execution in case parameters contain spaces
        def lastCommitHashCommand = 'git log -n 1 --pretty=format:%h --'.tokenize()
        lastCommitHashCommand << relativeFilePath
        String lastCommitHash = lastCommitHashCommand.execute().text.trim()
        if (!lastCommitHash.isEmpty()) {
            result.put("hash", lastCommitHash)
        }

        // NOTE: Use array execution instead of string execution in case parameters contain spaces
        def lastCommitTimestampCommand = 'git log -n 1 --pretty=format:%aI --'.tokenize()
        lastCommitTimestampCommand << relativeFilePath
        String lastCommitTimestamp = lastCommitTimestampCommand.execute().text.trim()
        if (!lastCommitTimestamp.isEmpty()) {
            def zonedTimestamp = ZonedDateTime.parse(lastCommitTimestamp)
            result.put("timestamp", zonedTimestamp)
            result.put("timestamp_formatted", getFormattedTimestamps(zonedTimestamp))
        }
        return result
    }

    /**
     * Generates various formatted strings to represent the supplied timestamp.
     *
     * @param timestamp Timestamp to generate formatted strings for.
     *
     * Map of the various formatted strings.
     */
    private Map<String, String> getFormattedTimestamps(ZonedDateTime timestamp) {
        def isoUtcString = timestamp.format(DateTimeFormatter.ISO_INSTANT)
        def isoOffsetString = timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        return [
            iso_utc: isoUtcString,
            iso_utc_space: isoUtcString.replace('T', ' '),
            iso_utc_safe: isoUtcString.replace(':', ''),
            iso_offset: isoOffsetString,
            iso_offset_space: isoOffsetString.replace('T', ' '),
            iso_offset_safe: isoOffsetString.replace(':', ''),
        ]
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

        // creating aliases nested under our BSL group for clarity
        project.task('bslAsciidoctor') {
            group = "brightSPARK Labs - Docs"
            description = "Alias for `asciidoctor` task."
        }

        project.task('bslAsciidoctorPdf') {
            group = "brightSPARK Labs - Docs"
            description = "Alias for `asciidoctorPdf` task."
        }

        project.task('bslAsciidoctorPdfVersioned') {
            group = "brightSPARK Labs - Docs Versioned"
            description = "Creates PDF files with version string in filename"

            doLast {
                final def pdfOutputDir = project.file("${project.buildDir}/docs/asciidoc/pdf")
                final def versionedPdfOutputDir = project.file("${project.buildDir}/docs/asciidoc/pdfVersioned")
                project.copy {
                    from pdfOutputDir
                    into versionedPdfOutputDir
                }

                outputFileToContextMap.each { adocFile, context ->
                    final String hash = context.output_file.last_commit.hash
                    final String timestamp = context.output_file.last_commit.timestamp_formatted.iso_utc_safe
                    final String extantFilename = adocFile.getAbsolutePath().replace("jinjaProcessed", "docs/asciidoc/pdfVersioned").replace(".adoc", ".pdf")
                    final String renamedFilename = extantFilename.replace(".pdf", " ${timestamp} ${hash}.pdf")

                    final File extantFile = new File(extantFilename)
                    final File renamedFile = new File(renamedFilename)
                    extantFile.renameTo(renamedFile)
                }
            }
        }

        // Use `afterEvaluate` in case another task add the `build` task.
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
                     * 'title-logo-image'   -> defines the configuration of the image for pdf cover pages
                     *
                     * Appending `@` to lower precedence so that defaults can
                     * be overridden in Asciidoc documents. See:
                     * - https://docs.asciidoctor.org/asciidoc/latest/attributes/assignment-precedence/
                     */
                    attributes \
                            'chapter-label@'      : '',
                            'icons@'              : 'font',
                            'imagesdir@'          : project.file(config.buildImagesDir),
                            'numbered@'           : '',
                            'source-highlighter@' : 'coderay',
                            'toc@'                : config.tocPosition,
                            'title-logo-image@'   : config.titleLogoImage
                }
            }

            if (! project.tasks.findByName('build')) {
                project.task('build') {
                    group = "brightSPARK Labs - Docs"
                    description = "Builds the documentation."
                }
            }

            project.asciidoctor.dependsOn project.jinjaPreProcess
            project.asciidoctorPdf.dependsOn project.jinjaPreProcess
            project.bslAsciidoctor.dependsOn project.asciidoctor
            project.asciidoctorPdf.dependsOn project.asciidoctor
            project.bslAsciidoctorPdf.dependsOn project.asciidoctorPdf
            project.bslAsciidoctorPdfVersioned.dependsOn project.bslAsciidoctorPdf
            project.build.dependsOn project.bslAsciidoctorPdfVersioned
        }
    }
}
