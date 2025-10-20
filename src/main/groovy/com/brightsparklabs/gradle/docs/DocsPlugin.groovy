/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details.
 */

package com.brightsparklabs.gradle.docs

import com.google.common.base.Charsets
import com.google.common.io.Resources
import com.hubspot.jinjava.Jinjava
import com.hubspot.jinjava.JinjavaConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import groovy.text.SimpleTemplateEngine

/**
 * The brightSPARK Labs Docs Plugin.
 */
class DocsPlugin implements Plugin<Project> {
    // -------------------------------------------------------------------------
    // CONSTANTS
    // -------------------------------------------------------------------------

    /**
     * The default set of options that will be provided to AsciiDoctor for rendering the document.
     */
    public static final Map<String, Object> DEFAULT_ASCIIDOCTOR_OPTIONS = [
        // `book` adds a cover page which is generally what is desired.
        "doctype@": 'book'
    ]

    /**
     * The name of the task which generates the Dockerfile for hosting the documentation as a static website.
     */
    private static final String GENERATE_DOCKERFILE_TASK_NAME = 'generateDockerfile'

    // -------------------------------------------------------------------------
    // INSTANCE VARIABLES
    // -------------------------------------------------------------------------

    /** Jinja2 processor. */
    public static Jinjava jinjava = { _ ->
        JinjavaConfig jinjavaConfig = JinjavaConfig.newBuilder()
                // Fail if templates reference non-existent variables
                .withFailOnUnknownTokens(true)
                .build();
        return new Jinjava(jinjavaConfig)
    }()

    // -------------------------------------------------------------------------
    // IMPLEMENTATION: Plugin<Project>
    // -------------------------------------------------------------------------

    @Override
    void apply(Project project) {
        // Create plugin configuration object.
        final def config = project.extensions.create('docsPluginConfig', DocsPluginExtension)
        //        project.extensions.docsPluginConfig.extensions.create('website', DocsPluginExtension.WebsiteExtension)

        final File baseOutputDirectory = project.file('build/brightsparklabs/docs')
        final File jinjaOutputDir = new File(baseOutputDirectory, 'jinjaProcessed')
        final File themesDir = new File(baseOutputDirectory, 'themes')
        final File buildscriptVarsDir = new File(baseOutputDirectory, 'buildscriptVariables')
        final File dockerfileOutputDir = new File(baseOutputDirectory, 'dockerfile')
        final File dockerPdfOutputDir = new File(baseOutputDirectory, 'pdf')
        final File websiteOutputDir = new File(baseOutputDirectory, 'website')

        project.ext.bslGradleDocs = [
            buildScriptVariablesDirs: buildscriptVarsDir,
            createBuildscriptVariablesFile: { filePath ->
                createBuildscriptVariablesFile(buildscriptVarsDir, filePath)
            }
        ]
        project.logger.info('The following has been exposed under `project.ext.bslGradleDocs:\n{}', project.ext.bslGradleDocs)

        def global_context = getGlobalContext(project, config)

        setupJinjaPreProcessingTasks(project, jinjaOutputDir, buildscriptVarsDir)
        setupAsciiDoctor(project, config, global_context, jinjaOutputDir, themesDir)
        setupDockerFileTask(project, config, global_context, dockerfileOutputDir)

        def dockerOrPodman = getDockerExecutableName(project)
        if (dockerOrPodman.isEmpty()) {
            project.logger.lifecycle("Docker `buildx` compatible command not available. Excluding tasks which rely on it.")
            return
        }

        dockerOrPodman = dockerOrPodman.get()
        setupBuildInDocker(project, config, dockerPdfOutputDir, dockerOrPodman)
        setupWebsiteTasks(project, config, websiteOutputDir, dockerOrPodman)
    }

    // --------------------------------------------------------------------------
    // EXPOSED METHODS
    // -------------------------------------------------------------------------

    /**
     * Creates a variables file within the buildscript variables directory.
     *
     * E.g.
     *
     *   In: src/devops/administrator-guide.adoc.j2.yaml
     *   Out: build/brightsparklabs/docs/buildscriptVariables/src/devops/administrator-guide.adoc.j2.yaml
     *
     * @param buildScriptVariablesDir The buildscript variables directory.
     * @param filePath The path to the new file within the directory.
     * @return The newly created file.
     */
    File createBuildscriptVariablesFile(File buildScriptVariablesDir, String filePath) {
        def buildscriptVariablesFile = new File(buildScriptVariablesDir, filePath)
        buildscriptVariablesFile.parentFile.mkdirs()
        return buildscriptVariablesFile
    }

    // --------------------------------------------------------------------------
    // PRIVATE METHODS
    // -------------------------------------------------------------------------

    /**
     * Adds the Jinja2 pre-processing task.
     *
     * @param project Gradle project to add the task to.
     * @param jinjaOutputDir Directory to output rendered Jinja2 templates.
     * @param buildscriptVariablesDir Directory containing any buildscript created variables.
     */
    private static void setupJinjaPreProcessingTasks(Project project, File jinjaOutputDir, File buildscriptVariablesDir) {
        project.tasks.register('cleanJinjaPreProcess') {
            group = "brightSPARK Labs - Docs"
            description = "Cleans the Jinja2 processed documents out of the build directory."

            doLast {
                project.delete jinjaOutputDir
                // Delete asciidoctor generated documents else renamed/deleted docs  may remain.
                def asciidoctorOutputDir = project.file("build/docs")
                project.delete asciidoctorOutputDir
            }
        }
        // Use `afterEvaluate` in case another task add the `clean` task.
        project.afterEvaluate {
            try {
                project.tasks.named('clean')
            }
            catch (UnknownTaskException ignored) {
                project.tasks.register('clean') {
                    group = "brightSPARK Labs - Docs"
                    description = "Cleans the documentation."
                }
            }
            project.tasks.named('clean') { dependsOn 'cleanJinjaPreProcess' }
        }

        project.tasks.register('jinjaPreProcess', Jinja2PreProcessingTask) {
            group = "brightSPARK Labs - Docs"
            description = "Performs Jinja2 pre-processing on documents. Filter paths with `-P${Jinja2PreProcessingTask.GRADLE_PROPERTY_NAME_INCLUDE}=src/dir1,src/dir2/dir3`."

            templatesDirProperty.set(new File(config.docsDir))
            buildscriptVariablesDirProperty.set(buildscriptVariablesDir.getAbsolutePath())
            jinjaOutputDirProperty.set(jinjaOutputDir)
        }

        project.jinjaPreProcess.dependsOn project.cleanJinjaPreProcess
    }

    /**
     * Returns the global context to use for rendering templates.
     *
     * @param project Gradle project to add the task to.
     * @param config Configuration for this plugin.
     * @param now The current time (passed in for external consistency).
     * @return The global context to use for rendering templates.
     */
    static Map<String, Object> getGlobalContext(Project project, DocsPluginExtension config, ZonedDateTime now = ZonedDateTime.now()) {
        final Map<String, Object> sysContext = [
            project_name             : Optional.ofNullable(project.name).map { it.trim() }.orElse("unspecified"),
            project_description      : Optional.ofNullable(project.description).map { it.trim() }.orElse("unspecified"),
            project_version          : Optional.ofNullable(project.version).map { it.trim() }.orElse("unspecified"),
            project_path             : project.projectDir.toPath(),
            build_timestamp          : now,
            build_timestamp_formatted: getFormattedTimestamps(now),
            repo_last_commit         : getLastCommit('.', now),
        ]

        final Map<String, Object> context = [
            sys   : sysContext,
            config: config,
        ]

        return context
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
    private static Map<String, Object> getLastCommit(String relativeFilePath, ZonedDateTime defaultTimestamp) {
        final Map<String, Object> result = [
            hash               : 'unspecified',
            timestamp          : defaultTimestamp,
            timestamp_formatted: getFormattedTimestamps(defaultTimestamp),
        ]

        // If file is dirty, then use current time.
        def checkFileDirtyCommand = 'git diff --shortstat --'.tokenize()
        checkFileDirtyCommand << relativeFilePath
        String checkFileDirty = checkFileDirtyCommand.execute().text.trim()
        if (!checkFileDirty.isEmpty()) {
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
    private static Map<String, String> getFormattedTimestamps(ZonedDateTime timestamp) {
        def isoUtcString = timestamp.format(DateTimeFormatter.ISO_INSTANT)
        def isoOffsetString = timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        return [
            iso_utc         : isoUtcString,
            iso_utc_space   : isoUtcString.replace('T', ' '),
            iso_utc_safe    : isoUtcString.replace(':', ''),
            iso_offset      : isoOffsetString,
            iso_offset_space: isoOffsetString.replace('T', ' '),
            iso_offset_safe : isoOffsetString.replace(':', ''),
        ]
    }

    /**
     * Adds and configures the Asciidoctor Gradle plugins.
     *
     * @param project Gradle project to add the task to.
     * @param config Configuration for this plugin.
     * @param jinjaOutputDir Directory to output rendered Jinja2 templates.
     */
    private void setupAsciiDoctor(Project project, DocsPluginExtension config, Map<String, Object>
            global_context, File jinjaOutputDir,
            File themesDir) {
        project.plugins.apply 'org.asciidoctor.jvm.convert'
        project.plugins.apply 'org.asciidoctor.jvm.pdf'

        // Creating aliases nested under our BSL group for clarity.
        project.tasks.register('bslAsciidoctor') {
            group = "brightSPARK Labs - Docs"
            description = "Alias for `asciidoctor` task."
        }

        project.tasks.register('bslAsciidoctorPdf') {
            group = "brightSPARK Labs - Docs"
            description = "Alias for `asciidoctorPdf` task."
        }

        project.tasks.register('bslAsciidoctorPdfVersioned') {
            group = "brightSPARK Labs - Docs"
            description = "Creates PDF files with version string in filename."

            doLast {
                final def pdfOutputDir = project.file("${project.buildDir}/docs/asciidoc/pdf")
                final def timestampedPdfOutputDir = project.file("${project.buildDir}/docs/asciidoc/pdfTimestamped")
                project.copy {
                    from pdfOutputDir
                    into timestampedPdfOutputDir
                }
                final def versionedPdfOutputDir = project.file("${project.buildDir}/docs/asciidoc/pdfVersioned")
                project.copy {
                    from pdfOutputDir
                    into versionedPdfOutputDir
                }

                // Use the context variables for each output file to determine timestamps.
                def jinjaPreProcessTask = (Jinja2PreProcessingTask) project.tasks.named('jinjaPreProcess').get()
                def outputFileToContextMap = jinjaPreProcessTask.getOutputFileToContextMap()

                outputFileToContextMap.each { adocFile, context ->
                    // Find the PDF file which got generated from the asciidoc file.
                    final String extantTimestampedFilename = adocFile
                            .getAbsolutePath()
                            .replace("build/brightsparklabs/docs/jinjaProcessed", "build/docs/asciidoc/pdfTimestamped")
                            .replace(".adoc", ".pdf")
                    final Path extantTimestampedFile = Path.of(extantTimestampedFilename)
                    if (!extantTimestampedFile.toFile().exists()) {
                        // The asciidoc file did not result in a PDF file. This happens when the
                        // asciidoc files are in asciidoc hidden folder (i.e. folders prefixed with
                        // an underscore). These files can be ignored.
                        project.logger.info('Ignoring asciidoc file which has no PDF equivalent: {}', adocFile.getAbsolutePath())
                        return
                    }

                    // Copy to timestamped directory.
                    final String timestamp = context.output_file.last_commit.timestamp_formatted.iso_utc_safe
                    final String renamedTimestampedFilename = extantTimestampedFilename
                            .replace(".pdf", "__${timestamp}.pdf")
                    final Path renamedTimestampedFile = Path.of(renamedTimestampedFilename)
                    Files.move(extantTimestampedFile, renamedTimestampedFile)

                    // Copy to versioned directory.
                    final String version = context.sys.project_version
                    final String extantVersionedFilename = adocFile
                            .getAbsolutePath()
                            .replace("build/brightsparklabs/docs/jinjaProcessed", "build/docs/asciidoc/pdfVersioned")
                            .replace(".adoc", ".pdf")
                    final Path extantVersionedFile = Path.of(extantVersionedFilename)
                    final String renamedVersionedFilename = extantVersionedFilename
                            .replace(".pdf", "__${timestamp}__${version}.pdf")
                    final Path renamedVersionedFile = Path.of(renamedVersionedFilename)
                    Files.move(extantVersionedFile, renamedVersionedFile)
                }
            }
        }

        project.tasks.register('bslGradleDocsExtractResources') {
            group = "brightSPARK Labs - Docs"
            description = "Extracts resources from the Gradle Docs plugin JAR to the filesystem."

            def resourceToOutput = [
                "/themes/brightspark-labs-theme.yml": [
                    Paths.get("${themesDir}/brightspark-labs-theme.yml")
                ],
                "/cover-page-logo.svg": [
                    Paths.get("${themesDir}/cover-page-logo.svg"),
                    // Also copy logo to images directory so it can be used in Asciidoc files directly.
                    Paths.get("${project.projectDir}/${config.buildImagesDir}/cover-page-logo.svg"),
                ],
            ]

            outputs.files(resourceToOutput.values())

            doLast {
                resourceToOutput.each { resource, outputFiles ->
                    outputFiles.each { outputFile ->
                        extractResourceToFilesystem(project, resource,
                                outputFile)
                    }
                }
            }
        }

        // Use `afterEvaluate` in case another plugin has added the `build` task.
        project.afterEvaluate {
            project.asciidoctor {
                sourceDir jinjaOutputDir
                outputOptions {
                    backends 'pdf', 'html'
                }

                // ensures includes are relative to each file
                baseDirFollowsSourceFile()

                asciidoctorj {
                    modules {
                        // Support asciidoctor-diagram image generation.
                        diagram.use()
                    }

                    // ----------------------------------------
                    // SET ASCIIDOCTOR OPTIONS
                    // ----------------------------------------

                    Map<String, Object> pluginOptions = [:]
                    pluginOptions.putAll(DEFAULT_ASCIIDOCTOR_OPTIONS)
                    pluginOptions.putAll(config.options)
                    // Allows for the removal of any options for which the user defines a value of null
                    pluginOptions.values().removeIf { o -> !Objects.nonNull(o) }
                    options = pluginOptions

                    // ----------------------------------------
                    // SET ASCIIDOCTOR GLOBAL ATTRIBUTES
                    // ----------------------------------------

                    /*
                     * This is the list of default configurations that can be added to or modified via the attributes map
                     *
                     * 'doctype': 'book'        -> Use `Book` as default rather than `article`.
                     * '!chapter-signifier': '' -> Do not prefix chapter (Heading 1) with anything.
                     * 'icon-set':              -> Use Font Awesome icon set.
                     * 'icons'': 'font'         -> Use Font Awesome for admonitions.
                     * 'imagesdir'':            -> Directory to resolve images from.
                     * 'sectnums':              -> Numbers headings.
                     * 'sectnumlevels':         -> Numbers all headings levels (1-5).
                     * 'source-highlighter'     -> Add syntax highlighting to source blocks.
                     * 'toc': 'left'            -> Places TOC on left hand site in HTML pages.
                     * 'pdf-theme'              -> The theme to use for PDF generation.
                     *
                     * Appending `@` to lower precedence so that defaults can
                     * be overridden in Asciidoc documents. See:
                     * - https://docs.asciidoctor.org/asciidoc/latest/attributes/assignment-precedence/
                     */

                    Map<String, Object> pluginAttributes = [
                        // Asciidoctor attributes.
                        'doctype@'           : 'book',
                        '!chapter-signifier' : '',
                        'icon-set@'          : 'fas',
                        'icons@'             : 'font',
                        'imagesdir@'         : project.file(config.buildImagesDir),
                        'sectnums@'          : '',
                        'sectnumlevels@'     : '5',
                        'source-highlighter@': 'coderay',
                        'toc@'               : config.tocPosition,
                        'pdf-theme@'         : config.theme.orElse('brightspark-labs'),

                        // BSL attributes.
                        'bsl_classification@'       : config.classification,
                        'bsl_project_name@'         : global_context.sys.project_name,
                        'bsl_project_description@'  : global_context.sys.project_description,
                        'bsl_project_version@'      : global_context.sys.project_version,
                        'bsl_repo_last_commit_hash@': global_context.sys.repo_last_commit.hash,
                    ]

                    // If not using custom themes, must asciidoctor where to find the default theme.
                    if (config.theme.isEmpty()) {
                        pluginAttributes['pdf-themesdir@'] = themesDir
                    }

                    pluginAttributes.putAll(config.attributes)
                    // Allows for the removal of any attributes for which the user defines a value of null
                    pluginAttributes.values().removeIf { a -> !Objects.nonNull(a) }
                    attributes = pluginAttributes
                }
            }

            try {
                project.tasks.named('build')
            }
            catch (UnknownTaskException ignored) {
                project.tasks.register('build') {
                    group = "brightSPARK Labs - Docs"
                    description = "Builds the documentation."
                }
            }

            project.tasks.named('jinjaPreProcess') { dependsOn 'bslGradleDocsExtractResources' }
            project.tasks.named('asciidoctor') { dependsOn 'jinjaPreProcess' }
            project.tasks.named('asciidoctorPdf') { dependsOn 'jinjaPreProcess' }
            project.tasks.named('bslAsciidoctor') { dependsOn 'asciidoctor' }
            project.tasks.named('asciidoctorPdf') { dependsOn 'asciidoctor' }
            project.tasks.named('bslAsciidoctorPdf') { dependsOn 'asciidoctorPdf' }
            project.tasks.named('bslAsciidoctorPdfVersioned') { dependsOn 'bslAsciidoctorPdf' }
            project.tasks.named('build') { dependsOn 'bslAsciidoctorPdfVersioned' }
        }
    }

    /**
     * Extracts a resource from this plugin's JAR file onto the filesystem.
     *
     * @param project This Gradle project.
     * @param resourcePath Path of the resource within the JAR file (beginning with `/`).
     * @param outputFile File to write the extracted resource to.
     */
    private void extractResourceToFilesystem(Project project, String resourcePath, Path outputFile) {
        // Ensure all folder path for output file exists.
        outputFile.getParent().toFile().mkdirs()

        // Extract the resource out of this plugin's JAR file.
        try {
            final bytes = getClass().getResourceAsStream(resourcePath).readAllBytes();
            outputFile.withOutputStream { stream -> stream.write(bytes) }
        } catch (Exception ex) {
            project.logger.error("Could not copy resource file `{}` to `{}`", resourcePath, outputFile, ex)
            throw ex
        }
    }

    /**
     * Adds the Dockerfile generation tasks.
     *
     * @param project Gradle project to add the task to.
     * @param config Configuration for this plugin.
     * @param outputDir Directory to store the generated Dockerfile to.
     */
    private void setupDockerFileTask(Project project, DocsPluginExtension config, Map<String, Object> global_context, File outputDir) {
        project.tasks.register(GENERATE_DOCKERFILE_TASK_NAME) {
            group = "brightSPARK Labs - Docs"
            description = "Generates a Dockerfile for hosting the documentation as a static website."

            def dockerfile = new File(outputDir, 'Dockerfile')
            outputs.file(dockerfile)

            doLast {
                project.delete outputDir
                outputDir.mkdirs()

                // Render the jekyll configuration files.
                ["_config.yml", "Gemfile"].collect { filename ->
                    def templateUrl = getClass().getResource("/website/${filename}.j2")
                    def templateText = Resources.toString(templateUrl, Charsets.UTF_8)
                    def fileContent = jinjava.render(templateText, global_context)

                    def outputfile = new File(outputDir, filename)
                    outputfile.text = fileContent
                }

                // Read in the Dockerfile contents and then manually perform string extrapolation.
                def rawDockerFileContent = Resources.toString(getClass().getResource("/Dockerfile.j2"), Charsets.UTF_8)
                def dockerFileContent = jinjava.render(rawDockerFileContent, [config: config])

                def dockerFile = new File(outputDir, "Dockerfile")
                dockerFile.text = dockerFileContent

                project.logger.lifecycle("Dockerfile generated at `${dockerfile.getAbsolutePath()}`.")
            }
        }

        project.tasks.named('build') { dependsOn(GENERATE_DOCKERFILE_TASK_NAME) }
    }

    /**
     * Returns the name of the docker compatible executable (docker or podman) iff it has `buildx` support.
     *
     * @param project Gradle project to add the task to.
     * @return `docker` or `podman` iff one of them has `buildx` support. Empty otherwise.
     */
    private Optional<String> getDockerExecutableName(Project project) {
        if (checkCommandAvailable("docker buildx --help")) {
            project.logger.lifecycle("`docker buildx` available.")
            return Optional.of("docker")
        }

        if (checkCommandAvailable("podman buildx --help")) {
            project.logger.lifecycle("`podman buildx` available.")
            return Optional.of("podman")
        }

        project.logger.lifecycle("`docker buildx` / `podman buildx` not available.")
        return Optional.empty()
    }

    /**
     * Runs the `bslAsciidoctorPdf` task in a docker container. Useful as the CLI diagramming CLI
     * tools do not need to be installed on the local machine.
     *
     * @param project Gradle project to add the task to.
     * @param config Configuration for this plugin.
     * @param outputDir Directory to write the output to.
     * @param dockerOrPodman The command (docker or podman) to run for building the container.
     */
    private void setupBuildInDocker(Project project, DocsPluginExtension config, File outputDir, String dockerOrPodman) {
        project.tasks.register('bslAsciidoctorPdfInDocker') {
            group = "brightSPARK Labs - Docs"
            description = "Runs the Asciidoctor PDF tasks within a Docker container. Useful as the CLI diagramming CLI tools do not need to be installed on the local machine."
            dependsOn(GENERATE_DOCKERFILE_TASK_NAME)
            inputs.files(project.tasks.named(GENERATE_DOCKERFILE_TASK_NAME).get().outputs.files)
            outputs.dir(outputDir)

            doLast {
                outputDir.deleteDir()
                outputDir.mkdirs()

                /*
                 * The outputs from the `generateDockerFile` task only contain one file, the
                 * Dockerfile. It is the first item.
                 */
                def dockerFile = inputs.files[0]

                def command = [
                    dockerOrPodman,
                    "build",
                    "--file",
                    dockerFile,
                    // Specify the stage which contains only the built artifact.
                    "--target=pdf-export-stage",
                    // Write the files from that stage to the output directory.
                    "--output",
                    outputDir,
                    project.projectDir
                ]
                // Use `project.exec` (rather than "command".execute() as it live prints stderr/stdout.
                project.exec {
                    commandLine command
                }
                logger.lifecycle("PDF files exported to: `${outputDir}`")
            }
        }
    }


    /**
     * Adds the website generation tasks.
     *
     * @param project Gradle project to add the task to.
     * @param config Configuration for this plugin.
     * @param outputDir Directory to write the output to.
     * @param dockerOrPodman The command (docker or podman) to run for building the container.
     */
    private void setupWebsiteTasks(Project project, DocsPluginExtension config, File outputDir, String dockerOrPodman) {
        project.logger.lifecycle("Adding website generation tasks.")

        project.tasks.register('cleanJekyllWebsite') {
            group = "brightSPARK Labs - Docs"
            description = "Cleans the Jekyll website out of the build directory."

            doLast {
                project.delete outputDir
            }
        }

        // Use `afterEvaluate` in case another task add the `clean` task.
        project.afterEvaluate {
            try {
                project.tasks.named('clean')
            }
            catch (UnknownTaskException ignored) {
                project.tasks.register('clean') {
                    group = "brightSPARK Labs - Docs"
                    description = "Cleans the Jekyll website out of the build directory."
                }
            }

            project.tasks.named('clean') { dependsOn 'cleanJekyllWebsite' }
        }

        project.tasks.register('generateJekyllWebsite') {
            group = "brightSPARK Labs - Docs"
            description = "Generates a Jekyll based website from the documents using docker."
            dependsOn(GENERATE_DOCKERFILE_TASK_NAME)
            inputs.files(project.tasks.named(GENERATE_DOCKERFILE_TASK_NAME).get().outputs.files)
            outputs.dir(outputDir)

            doLast {
                outputDir.deleteDir()
                outputDir.mkdirs()

                /*
                 * The outputs from the `generateDockerFile` task only contain one file, the
                 * Dockerfile. It is the first item.
                 */
                def dockerFile = inputs.files[0]

                def command = [
                    dockerOrPodman,
                    "build",
                    "--file",
                    dockerFile,
                    // Specify the stage which contains only the built artifact.
                    "--target=export-stage",
                    // Write the files from that stage to the output directory.
                    "--output",
                    outputDir,
                    project.projectDir
                ]
                // Use `project.exec` (rather than "command".execute() as it live prints stderr/stdout.
                project.exec {
                    commandLine command
                }
                logger.lifecycle("Jekyll based static website created in: `${outputDir}`")
            }
        }
    }

    /**
     * Checks if the command can be executed successfully (returns exit code zero).
     * @param command Command to test.
     * @return `true` if running the command returns exit code 0. `false` otherwise.
     */
    private static boolean checkCommandAvailable(String command) {
        try {
            def process = command.execute()
            process.waitFor()
            if (process.exitValue() == 0) {
                return true
            }
        } catch (Exception ignored) {
            // If command not available it will generally throw an exception.
            // Do nothing as we return false by default.
        }

        return false
    }
}
