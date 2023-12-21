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
import com.hubspot.jinjava.loader.CascadingResourceLocator
import com.hubspot.jinjava.loader.ClasspathResourceLocator
import com.hubspot.jinjava.loader.FileLocator
import groovy.io.FileType
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The brightSPARK Labs Docs Plugin.
 */
class DocsPlugin implements Plugin<Project> {
    // -------------------------------------------------------------------------
    // CONSTANTS
    // -------------------------------------------------------------------------

    /**
     * Name of the default logo file in the resources directory. This is also used as the
     * destination file name when copying client supplied logos.
     */
    public static final String DEFAULT_LOGO_FILENAME = 'cover-page-logo.svg'

    /**
     * The default set of options that will be provided to AsciiDoctor for rendering the document.
     */
    public static final Map<String, Object> DEFAULT_ASCIIDOCTOR_OPTIONS = ["doctype": 'book']

    /**
     * The name of the task which generates the Dockerfile for hosting the documentation as a static website.
     */
    private static final String GENERATE_DOCKERFILE_TASK_NAME = 'generateDockerfile'

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

    /** Header to add to each Jinja2 file prior to rendering. */
    def templateHeader = ""

    /** Footer to add to each Jinja2 file prior to rendering. */
    def templateFooter = ""

    // -------------------------------------------------------------------------
    // IMPLEMENTATION: Plugin<Project>
    // -------------------------------------------------------------------------

    @Override
    void apply(Project project) {
        // Create plugin configuration object.
        final def config = project.extensions.create('docsPluginConfig', DocsPluginExtension)
        //        project.extensions.docsPluginConfig.extensions.create('website', DocsPluginExtension.WebsiteExtension)

        if (config.autoImportMacros) {
            templateHeader += '{% import "brightsparklabs-macros.j2" as brightsparklabs %}\n'
        }

        final File templateHeaderFile = project.file(config.templateHeaderFile)
        if (templateHeaderFile.exists()) {
            project.logger.info("Templates will be prepended with header from [${templateHeaderFile}]")
            templateHeader += templateHeaderFile.text
        }

        final File templateFooterFile = project.file(config.templateFooterFile)
        if (templateFooterFile.exists()) {
            project.logger.info("Templates will be appended with footer from [${templateFooterFile}]")
            templateFooter += templateFooterFile.text
        }

        final File baseOutputDirectory = project.file('build/brightsparklabs/docs')
        final File jinjaOutputDir = new File(baseOutputDirectory, 'jinjaProcessed')
        final File dockerfileOutputDir = new File(baseOutputDirectory, 'dockerfile')
        final File websiteOutputDir = new File(baseOutputDirectory, 'website')

        setupJinjaPreProcessingTasks(project, config, jinjaOutputDir)
        setupAsciiDoctor(project, config, jinjaOutputDir)
        setupDockerFileTask(project, config, dockerfileOutputDir)
        setupWebsiteTasks(project, config, websiteOutputDir)
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
        project.tasks.register('cleanJinjaPreProcess') {
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
            try {
                project.tasks.named('clean')
            }
            catch (UnknownTaskException ignored) {
                project.tasks.register('clean') {
                    group = "brightSPARK Labs - Docs"
                    description = "Cleans the documentation."
                }
            }
            project.tasks.named('clean'){ dependsOn 'cleanJinjaPreProcess'}
        }

        project.tasks.register('jinjaPreProcess') {
            group = "brightSPARK Labs - Docs"
            description = "Performs Jinja2 pre-processing on documents"

            /* Calculate the relative path of this project's directory (i.e. the directory
             * containing this project's `build.gradle` file) relative to the repo root.
             *
             * Generally the relative path will simply by blank (i.e. `build.gradle` resides at the
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
                project.delete jinjaOutputDir
                jinjaOutputDir.mkdirs()
                project.copy {
                    from project.file(config.docsDir)
                    into jinjaOutputDir
                }

                def buildImagesDir = project.file(config.buildImagesDir)
                project.delete buildImagesDir
                buildImagesDir.mkdirs()
                project.copy {
                    from project.file(config.sourceImagesDir)
                    into buildImagesDir
                }

                // Copy logo into build directory so it can be referenced in Asciidoc.
                final Path outputFile = Paths.get("${project.projectDir}/${config.buildImagesDir}/${DEFAULT_LOGO_FILENAME}")
                try {
                    final logoBytes = config.logoFile
                            .map { path -> path.toFile().readBytes() }
                            .orElse(getClass().getResourceAsStream("/${DEFAULT_LOGO_FILENAME}").readAllBytes());
                    outputFile.withOutputStream { stream -> stream.write(logoBytes) }
                } catch (Exception ex) {
                    logger.error("Could not copy logo file to build directory", ex)
                    throw ex
                }

                def now = ZonedDateTime.now()
                final Map<String, Object> context = getGlobalContext(project, config, now)

                // ------------------------------------------------------------
                // ROOT JINJA2 CONTEXT
                // ------------------------------------------------------------

                final File variablesFile = project.file(config.variablesFile)
                if (variablesFile.exists()) {
                    project.logger.info("Adding global variables to Jinja2 from [${variablesFile}]")

                    String yamlText = variablesFile.text
                    context.put('vars', yaml.load(yamlText))

                    Map<String, Object> variablesFileLastCommit = getLastCommit(projectRelativePath + config.variablesFile, now)
                    context.put('vars_file_last_commit', variablesFileLastCommit)
                }
                logger.info("Using `root` context:\n${yaml.dump(context)}")

                // All directory variable files which have been loaded.
                // Key is the full path to the dir, value is a map of the variables from the dir.
                final Map<String, Optional<Map<String, Object>>> loadedDirVariablesFiles = [:]

                // Process templates.
                jinjaOutputDir.traverse(type: FileType.FILES) { templateFile ->
                    // Ignore file if it is not a Jinja2 template
                    if (!templateFile.name.endsWith(".j2")) {
                        logger.info("Skipping non-Jinja2 template [${templateFile}]")
                        return
                    }

                    // ------------------------------------------------------------
                    // TEMPLATE DIR CONTEXT
                    // ------------------------------------------------------------
                    final def templateDir = templateFile.getParentFile()
                    final def templateDirVariables = getDirVariables(templateDir, loadedDirVariablesFiles)
                    final def templateDirRelativePath = templateDir.getPath().replaceFirst(
                            jinjaOutputDir.toString(),
                            new File(config.docsDir).toString() // NOTE: this cleanly removes trailing slashes
                            ).replaceFirst(config.docsDir + '/?', '')
                    final def templateDirContext = [
                        path: templateDirRelativePath,
                        vars: templateDirVariables]
                    context.put('template_dir', templateDirContext)

                    // ------------------------------------------------------------
                    // TEMPLATE FILE CONTEXT
                    // ------------------------------------------------------------
                    String templateSrcFile = templateFile.getPath().replaceFirst(
                            jinjaOutputDir.toString(),
                            new File(config.docsDir).toString() // NOTE: this cleanly removes trailing slashes
                            )
                    Map<String, Object> templateFileLastCommit = getLastCommit(projectRelativePath + templateSrcFile, now)
                    String templateOutputFileName = templateFile.getName().replaceFirst(/\.j2$/, '')
                    File templateOutputFile = new File(templateFile.getParent(), templateOutputFileName)
                    Map<String, Object> templateFileContext = [
                        name       : templateFile.getName(),
                        // Relative to docs directory (`toString` to prevent GString in map).
                        path       : "${templateDirRelativePath}/${templateFile.getName()}".toString(),
                        last_commit: templateFileLastCommit,
                    ]
                    context.put('template_file', templateFileContext)

                    // ------------------------------------------------------------
                    // OUTPUT FILE CONTEXT
                    // ------------------------------------------------------------
                    Map<String, Object> outputFileContext = [
                        name       : templateOutputFileName,
                        // Relative to output directory.
                        path       : templateOutputFile.getPath().replaceFirst(jinjaOutputDir.toString() + '/?', ''),
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
                            if (!instanceFile.name.endsWith(".yaml")) {
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
                                name       : instanceFile.getName(),
                                // Relative to docs directory.
                                path       : instanceSrcFile.replaceFirst(config.docsDir + '/?', ''),
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
                            if (instanceFileLastCommit.timestamp.isAfter(cachedLastCommit.timestamp) && instanceFileLastCommit.hash != "unspecified") {
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
                    } else {
                        // No instances, just process in place.
                        logger.debug("Using context:\n${yaml.dump(context)}")
                        writeTemplateToFile(templateFile, context, templateOutputFile)
                    }

                    // Clean out context and template.
                    context.remove('template_dir')
                    context.remove('template_file')
                    context.remove('output_file')
                    templateFile.delete()
                }

                project.logger.lifecycle("Template files rendered to `${jinjaOutputDir.getAbsolutePath()}`.")
            }
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
    private static Map<String, Object> getGlobalContext(Project project, DocsPluginExtension config, ZonedDateTime now = ZonedDateTime.now()) {
        final Map<String, Object> sysContext = [
            project_name: project.name,
            project_description: project.description,
            project_version: project.version,
            project_path: project.projectDir.toPath(),
            build_timestamp: now,
            build_timestamp_formatted: getFormattedTimestamps(now),
            repo_last_commit: getLastCommit('.', now),
        ]

        final Map<String, Object> context = [
            sys   : sysContext,
            config: config,
        ]

        return context
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
            def snapshot = context.getClass().<Map<String, Object>>newInstance(context)
            outputFileToContextMap[outputFile] = snapshot

            // Support Jinja2 imports from classpath and relative to template file's directory.
            Path absoluteDocsDir = context.sys.project_path.resolve(context.config.docsDir)
            File absoluteTemplateDir = absoluteDocsDir.resolve(context.template_dir.path).toFile()
            def resourceLocator = new CascadingResourceLocator(new ClasspathResourceLocator(), new FileLocator(absoluteTemplateDir))

            // Render template to file.
            def originalResourceLocator = jinjava.getResourceLocator()
            jinjava.setResourceLocator(resourceLocator)
            final def inputText = [
                templateHeader,
                templateFile.text,
                templateFooter
            ].join('\n')
            outputFile.text = jinjava.render(inputText, context)
            jinjava.setResourceLocator(originalResourceLocator)
        } catch (Exception ex) {
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
     * Returns the variables from the `variables.yaml` file in the template file's directory. Or empty if no variables are present.
     * This method will cache the result in the `loadedDirVariablesFiles` (i.e. will modify it).
     *
     * @param dir The directory the template is from.
     * @param loadedDirVariablesFiles Cache of all the directory variables files previously loaded.
     * @return The dir variables pertaining to the template.
     */
    private Map<String, Object> getDirVariables(File dir, Map<String, Optional<Map<String, Object>>> loadedDirVariablesFiles) {
        final def dirName = dir.getAbsolutePath()
        final def extantDirVariables = loadedDirVariablesFiles.get(dirName)
        if (extantDirVariables != null) {
            return extantDirVariables.orElse([:])
        }

        final File dirVariablesFile = new File(dir, "variables.yaml")
        if (!dirVariablesFile.exists()) {
            loadedDirVariablesFiles.put(dirName, Optional.empty())
            return [:]
        }

        final def yamlText = dirVariablesFile.text
        final def dirVariables = yaml.<Map<String, Object>> load(yamlText)
        final def result = Optional.of(dirVariables)
        loadedDirVariablesFiles.put(dirName, result)
        // Delete the variables file since we do not want it in the final output dir.
        dirVariablesFile.delete()
        return dirVariables
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
                    Map<String, Object> pluginOptions = [:]
                    pluginOptions.putAll(DEFAULT_ASCIIDOCTOR_OPTIONS)
                    pluginOptions.putAll(config.options)
                    // Allows for the removal of any options for which the user defines a value of null
                    pluginOptions.values().removeIf { o -> !Objects.nonNull(o) }
                    options = pluginOptions

                    /*
                     * This is the list of default configurations that can be added to or modified via the attributes map
                     *
                     * 'chapter-label': ''  -> do not prefix headings with anything
                     * 'icon-set':          -> use Font Awesome icon set
                     * 'icons'': 'font'     -> use Font Awesome for admonitions
                     * 'imagesdir'':        -> directory to resolve images from
                     * 'sectnums':          -> numbers headings
                     * 'sectnumlevels':     -> numbers all headings levels (1-5)
                     * 'source-highlighter' -> add syntax highlighting to source blocks
                     * 'title-logo-image'   -> defines the configuration of the image for pdf cover pages
                     * 'toc': 'left'        -> places TOC on left hand site in HTML pages
                     *
                     * Appending `@` to lower precedence so that defaults can
                     * be overridden in Asciidoc documents. See:
                     * - https://docs.asciidoctor.org/asciidoc/latest/attributes/assignment-precedence/
                     */

                    Map<String, Object> pluginAttributes = [
                        'chapter-label@'     : '',
                        'icon-set@'          : 'fas',
                        'icons@'             : 'font',
                        'imagesdir@'         : project.file(config.buildImagesDir),
                        'sectnums@'          : '',
                        'sectnumlevels@'     : '5',
                        'source-highlighter@': 'coderay',
                        'title-logo-image@'  : config.titleLogoImage,
                        'toc@'               : config.tocPosition
                    ]
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

            project.tasks.named('asciidoctor') {dependsOn 'jinjaPreProcess' }
            project.tasks.named('asciidoctorPdf') {dependsOn 'jinjaPreProcess' }
            project.tasks.named('bslAsciidoctor') {dependsOn 'asciidoctor' }
            project.tasks.named('asciidoctorPdf') {dependsOn 'asciidoctor' }
            project.tasks.named('bslAsciidoctorPdf') {dependsOn 'asciidoctorPdf' }
            project.tasks.named('bslAsciidoctorPdfVersioned') {dependsOn 'bslAsciidoctorPdf' }
            project.tasks.named('build') {dependsOn 'bslAsciidoctorPdfVersioned' }
        }
    }

    /**
     * Adds the Dockerfile generation tasks.
     *
     * @param project Gradle project to add the task to.
     * @param config Configuration for this plugin.
     * @param outputDir Directory to store the generated Dockerfile to.
     */
    private void setupDockerFileTask(Project project, DocsPluginExtension config, File outputDir) {
        project.tasks.register(GENERATE_DOCKERFILE_TASK_NAME) {
            group = "brightSPARK Labs - Docs"
            description = "Generates a Dockerfile for hosting the documentation as a static website."

            def dockerfile = new File(outputDir, 'Dockerfile')
            outputs.file(dockerfile)

            doLast {
                project.delete outputDir
                outputDir.mkdirs()

                // Render the jekyll configuration files.
                def context = getGlobalContext(project, config)
                ["_config.yml", "Gemfile"].collect { filename ->
                    def templateUrl = getClass().getResource("/website/${filename}.j2")
                    def templateText = Resources.toString(templateUrl, Charsets.UTF_8)
                    def fileContent = jinjava.render(templateText, context)

                    def outputfile = new File(outputDir, filename)
                    outputfile.text = fileContent
                }

                def dockerFileContent = """
                # -----------------------------------------
                # BUILD STAGE: GENERATE ASCIIDOC/PDF FILES
                # -----------------------------------------

                FROM eclipse-temurin:17.0.9_9-jdk-alpine as builder-java

                RUN apk add \\
                  # Allow gradle-docs plugin to read git details on files.
                  git \\
                  # Needed to allow asciidoctor-diagram to render plantuml (dot) diagrams.
                  graphviz

                # Get gradle distribution (done separately so Docker caches layer)
                WORKDIR /src
                COPY build.gradle settings.gradle gradlew ./
                COPY gradle ./gradle
                RUN ./gradlew build || return 0 # force sucess as build is expected to fail due to no sources

                COPY .git ./.git
                COPY src ./src

                # Build the asciidoc files and Dockerfile resources.
                RUN ./gradlew jinjaPreProcess generateDockerfile --no-daemon

                # Always ensure the images directory exists since it is copied in next stage.
                RUN mkdir -p "${config.buildImagesDir}"

                # -----------------------------------------
                # BUILD STAGE: GENERATE WEBSITE
                # -----------------------------------------

                FROM jekyll/jekyll:4.2.2 as builder-jekyll

                # Needed to allow asciidoctor-diagram to render plantuml (dot) diagrams.
                RUN apk add graphviz

                COPY --from=builder-java /src/build/brightsparklabs/docs/dockerfile/_config.yml .
                COPY --from=builder-java /src/build/brightsparklabs/docs/dockerfile/Gemfile .

                # Run a build to cache gems.
                RUN jekyll build --verbose

                COPY --from=builder-java /src/build/brightsparklabs/docs/jinjaProcessed .
                COPY --from=builder-java "/src/${config.buildImagesDir}" images

                # NOTE:
                #
                # If you do not specify `-d /tmp/site` it should default to `/srv/jekyll/_site`.
                # However, this directory does not seem to get created for some reason. I.e. adding
                # the following to the Dockerfile shows that the directory is not present:
                #
                #   RUN ls -al /srv/jekyll
                #
                # Explicitly building to `/tmp/site` fixes it.
                RUN jekyll build --verbose -d /tmp/site

                # NOTE:
                #
                # Need to chown it to root otherwise `jenkins` owns it, and the CI server fails next
                # stage. See https://circleci.com/docs/2.0/high-uid-error/
                RUN chown -R root:root /tmp/site

                # -----------------------------------------
                # EXPORT STAGE
                # -----------------------------------------

                # The image produced by this stage can be used if the files are to be exported to
                # the file system via: `docker build ... --target export-stage --output output/`

                # Base off scratch so output only contains the website files.
                FROM scratch AS export-stage
                COPY --from=builder-jekyll /tmp/site .

                # -----------------------------------------
                # FINAL STAGE
                # -----------------------------------------

                # The image produced by this stage hosts the website.

                FROM kyma/docker-nginx:latest
                MAINTAINER brightSPARK Labs <enquire@brightsparklabs.com>
                ARG BUILD_DATE=UNKNOWN
                ARG VCS_REF=UNKNOWN
                LABEL org.label-schema.name="nswcc-documentation" \
                      org.label-schema.description="Image used by NSWCC to host documentation as a static website" \
                      org.label-schema.vendor="brightSPARK Labs" \
                      org.label-schema.schema-version="1.0.0-rc1" \
                      org.label-schema.vcs-url="https://bitbucket.org/brightsparklabs/nswcc-documentation" \
                      org.label-schema.vcs-ref=\${VCS_REF} \
                      org.label-schema.build-date=\${BUILD_DATE}
                ENV META_BUILD_DATE=\${BUILD_DATE} \
                    META_VCS_REF=\${VCS_REF}

                COPY --from=builder-jekyll /tmp/site /var/www
                """.stripIndent().trim()

                def dockerFile = new File(outputDir, "Dockerfile")
                dockerFile.text = dockerFileContent

                project.logger.lifecycle("Dockerfile generated at `${dockerfile.getAbsolutePath()}`.")
            }
        }

        project.tasks.named('build') { dependsOn(GENERATE_DOCKERFILE_TASK_NAME) }
    }

    /**
     * Adds the website generation tasks.
     *
     * @param project Gradle project to add the task to.
     * @param config Configuration for this plugin.
     */
    private void setupWebsiteTasks(Project project, DocsPluginExtension config, File outputDir) {
        // Only enable task if `docker buildx`/`podman buildx` is present since it is used for the generation.
        def dockerOrPodman
        if (checkCommandAvailable("docker buildx --help")) {
            project.logger.lifecycle("`docker buildx` available. Adding website generation tasks.")
            dockerOrPodman = "docker"
        } else if (checkCommandAvailable("podman buildx --help")) {
            project.logger.lifecycle("`podman buildx` available. Adding website generation tasks.")
            dockerOrPodman = "podman"
        } else {
            project.logger.lifecycle("`docker buildx` / `podman buildx` not available. Not adding website generation tasks (which require it)")
            return
        }

        project.tasks.register('cleanJekyllWebsite') {
            group = "brightSPARK Labs - Docs"
            description = "Cleans the Jekyll website out of the build directory"

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
                    description = "Cleans the Jekyll website out of the build directory"
                }
            }

            project.tasks.named('clean'){ dependsOn 'cleanJekyllWebsite'}
        }

        project.tasks.register('generateJekyllWebsite') {
            group = "brightSPARK Labs - Docs"
            description = "Generates a Jekyll based website from the documents using docker"
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
