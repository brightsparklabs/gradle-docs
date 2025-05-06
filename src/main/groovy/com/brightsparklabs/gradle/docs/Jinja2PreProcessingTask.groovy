/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details.
 */

package com.brightsparklabs.gradle.docs

import com.hubspot.jinjava.loader.CascadingResourceLocator
import com.hubspot.jinjava.loader.ClasspathResourceLocator
import com.hubspot.jinjava.loader.FileLocator
import groovy.io.FileType
import groovy.transform.Immutable
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml

import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import static com.brightsparklabs.gradle.docs.DocsPlugin.jinjava

/**
 * The Jinja2 pre-processing task which renders all the .j2 template files.
 */
abstract class Jinja2PreProcessingTask extends DefaultTask {
    // -------------------------------------------------------------------------
    // CONSTANTS
    // -------------------------------------------------------------------------

    /**
     * Name of the default logo file in the resources directory. This is also used as the
     * destination file name when copying client supplied logos.
     */
    public static final String DEFAULT_LOGO_FILENAME = 'cover-page-logo.svg'

    /** Name of the Gradle property used to control max aliases allowed by Yaml loader. */
    public static final String YAML_MAX_ALIASES = 'com.brightsparklabs.gradle.docs.YAML_MAX_ALIASES'

    // -------------------------------------------------------------------------
    // INSTANCE VARIABLES
    // -------------------------------------------------------------------------

    /*
     * Using a closure to build the `Yaml` because we need to build up the `LoaderOptions` and
     * `DumperOptions` first. The closure allows us to run the logic, then execute (with the tailing
     * `()`) which then sets the value.
     *
     * NOTE: Needs to be non-static as we need to use the `project` variable (which is non-static)
     * to get Gradle properties.
     */
    /** Yaml parser. */
    private final Yaml yaml = { _ ->
        LoaderOptions loaderOptions = new LoaderOptions();
        /*
         * We only want to set the unsafe `setMaxAliasesForCollections` if explicitly requested by
         * developer using a gradle property.
         */
        String maxAliases = project.findProperty(YAML_MAX_ALIASES)
        if (maxAliases != null) {
            int max = Integer.parseInt(maxAliases)
            project.logger.warn(
                    "Gradle property `${YAML_MAX_ALIASES}` is set to `${max}`. Setting Yaml loader option `setMaxAliasesForCollections`. Be aware of security implications.")
            loaderOptions.setMaxAliasesForCollections(max);
        }

        def dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);

        return new Yaml(
                new org.yaml.snakeyaml.constructor.Constructor(loaderOptions),
                new org.yaml.snakeyaml.representer.Representer(dumperOptions),
                dumperOptions,
                loaderOptions
                );
    }()

    /* NOTE:
     *
     * Not entirely sure why some of the fields need @Internal, and others do not. Have added
     * @Internal to any which gradle complains about.
     */
    /** The plugin configuration. */
    @Internal
    final DocsPluginExtension config = project.extensions.getByName('docsPluginConfig') as DocsPluginExtension

    /** The relative path of this project's directory (i.e. the directory containing this project's `build.gradle` file) relative to the repo root. */
    @Internal
    private final String projectRelativePath = getProjectRelativePath()

    /** The current time (used as a consistent default across files if timestamps are missing). */
    private final ZonedDateTime now = ZonedDateTime.now()

    /** Output asciidoc files mapped to the context which created them. */
    @Internal
    private Map<File, Map<String, Object>> outputFileToContextMap = [:]

    /** Header to add to each Jinja2 file prior to rendering. */
    private def templateHeader = ""

    /** Footer to add to each Jinja2 file prior to rendering. */
    private def templateFooter = ""

    // -------------------------------------------------------------------------
    // DYNAMIC INPUTS
    // -------------------------------------------------------------------------

    /** Directory containing the source templates to render. */
    @InputDirectory
    def templatesDirProperty = project.objects.directoryProperty()

    /** Directory containing any buildscript created variables. */
    @Input
    def buildscriptVariablesDirProperty = project.objects.property(String.class)

    /** Directory to store the rendered templates in. */
    @OutputDirectory
    def jinjaOutputDirProperty = project.objects.directoryProperty()

    // -------------------------------------------------------------------------
    // CONSTRUCTION
    // -------------------------------------------------------------------------

    /**
     * Default constructor.
     */
    Jinja2PreProcessingTask() {
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
    }

    // -------------------------------------------------------------------------
    // IMPLEMENTATION: Plugin<Project>
    // -------------------------------------------------------------------------

    /**
     * Adds the Jinja2 pre-processing task.
     *
     * @param project Gradle project to add the task to.
     * @param config Configuration for this plugin.
     * @param jinjaOutputDir Directory to output rendered Jinja2 templates.
     */
    @TaskAction
    void runTask() {
        final File jinjaOutputDir = jinjaOutputDirProperty.get().asFile

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

        final Map<String, Object> context = DocsPlugin.getGlobalContext(project, config, now)

        // ------------------------------------------------------------
        // ROOT JINJA2 CONTEXT
        // ------------------------------------------------------------

        final globalVariablesFile = project.file(config.variablesFile)
        final VariablesFileData variablesFileData = readVariablesFile(globalVariablesFile)
        context.putAll(variablesFileData.toMap())
        logger.info("Using `root` context:\n${yaml.dump(context)}")

        // All directory variable files which have been loaded.
        // Key is the full path to the dir, value is a map of the variables from the dir.
        final Map<String, Optional<Map<String, Object>>> loadedDirVariablesFiles = [:]

        // Process templates.
        jinjaOutputDir.traverse(type: FileType.FILES) { templateFile ->
            // Get any context variables in the directory containing the template file.
            final File templateDir = templateFile.getParentFile()
            final def templateDirVariables = getDirVariables(templateDir, loadedDirVariablesFiles)

            renderTemplateFile(templateFile, context, templateDirVariables)
        }

        project.logger.lifecycle("Template files rendered to `${jinjaOutputDir.getAbsolutePath()}`.")
    }

    // --------------------------------------------------------------------------
    // PUBLIC METHODS
    // -------------------------------------------------------------------------

    // NOTE: When the below methods were `private` they could not be called from within closures.
    //       So we have dropped the visibility modifier.

    /**
     *  Returns a map of output asciidoc files mapped to the context which created them.
     * @return a map of output asciidoc files mapped to the context which created them.
     */
    Map<File, Map<String, Object>> getOutputFileToContextMap(){
        return outputFileToContextMap;
    }

    /**
     * Loads the values from the specified variables file. The file must reside
     * within the `config.docsDir`.
     *
     * @param variablesFile File to read.
     * @return Results from reading the file.
     */
    VariablesFileData readVariablesFile(File variablesFile) {
        Map<String, Object> vars = [:]
        Map<String, Object> varsFileLastCommit = [:]
        Map<String, Object> buildscriptVars = [:]

        final String variablesFilePathRelativeToDocsProject = getPathRelativeToDocsProject(variablesFile)

        if (variablesFile.exists()) {
            project.logger.info("Adding variables to Jinja2 from [${variablesFile}]")

            String yamlText = variablesFile.text
            vars = yaml.load(yamlText)

            varsFileLastCommit = getLastCommit(projectRelativePath + variablesFilePathRelativeToDocsProject, now)
        }

        final File buildscriptVariablesDir = new File(buildscriptVariablesDirProperty.get())
        final File buildscriptVariablesFile = new File(buildscriptVariablesDir, variablesFilePathRelativeToDocsProject)
        if (buildscriptVariablesFile.exists()) {
            project.logger.info("Adding buildscript variables to Jinja2 from [${buildscriptVariablesFile}]")

            String yamlText = buildscriptVariablesFile.text
            buildscriptVars = yaml.load(yamlText)
        }

        return new VariablesFileData(vars, varsFileLastCommit, buildscriptVars)
    }

    /**
     * Finds the corresponding source file for a file currently in the Jinja2 output directory.
     * @param fileInJinja2OutputDir The file which currently resides in the Jinja2 output directory.
     * @return The corresponding source file which the file was copied from.
     */
    File jinja2OutputDirPathToSrcDirPath(File fileInJinja2OutputDir) {
        // NOTE: The `new File` call cleanly removes any trailing slashes on the `config.docsDir` value.
        final String srcDirPath = new File(config.docsDir).toString()

        final String jinja2OutputDirPath = jinjaOutputDirProperty.get().asFile.toString()
        /*
         * Replace the path to the jinja2Output directory with the path to the source directory
         *
         * E.g.
         *
         *   In:  /path/to/my-project/build/brightsparklabs/docs/jinjaProcessed/intro/overview.adoc.j2
         *   Out: src/docs/intro/overview.adoc.j2
         */
        final String srcFilePath = fileInJinja2OutputDir.getPath().replaceFirst(jinja2OutputDirPath, srcDirPath)

        final File srcFile = project.file(srcFilePath)
        return srcFile
    }

    /**
     * Returns the relative path for a file in relation to the current project (or subproject if part of a multi-build project).
     *
     * E.g.
     *
     *   In:  /path/to/my-project/subproject/docs/src/docs/intro/overview.adoc.j2
     *   Out: src/docs/intro/overview.adoc.j2
     *
     * @param file
     * @return
     */
    String getPathRelativeToDocsProject(File file) {
        // The absolute path to this docs project.
        final Path pathToDocsProject = Path.of(project.file('.').getAbsolutePath())

        // Calculate the path to the file from this docs project.
        final String relativePath = pathToDocsProject.relativize(file.toPath())
        return relativePath
    }

    /**
     * Calculate the relative path of this project's directory (i.e. the directory
     * containing this project's `build.gradle` file) relative to the repo root.
     *
     * Generally the relative path will simply by blank (i.e. `build.gradle` resides at the
     * root of the repo). However, if the project is part of a Gradle multi-project build,
     * then this will not be the case.
     *
     * This is variable is needed so that file path can be built for use in `git` commands
     * which reference files in relation to the repo's root directory.
     */
    String getProjectRelativePath() {
        String projectRelativePath = "";
        if (!(project.projectDir.toString() == project.rootDir.toString())) {
            // This is a subproject in a Gradle multi-project build. Calculate relative path.
            // The `/` subtract/add ensures the string ends with a slash.
            projectRelativePath = project.projectDir.toString() - project.rootDir.toString() - "/" + "/"
        }
        return projectRelativePath
    }

    /**
     * Renders the specified template file against the given context variables.
     *
     * @param templateFile The template file to render.
     * @param context The context variables for the template.
     * @param templateDirVariables Any context variables defined in the directory containing the template.
     */
    void renderTemplateFile(File templateFile, Map<String, Object> context, Map<String, Object> templateDirVariables) {
        // Ignore file if it is not a Jinja2 template
        if (!templateFile.name.endsWith(".j2")) {
            logger.info("Skipping non-Jinja2 template [${templateFile}]")
            return
        }

        final File jinjaOutputDir = jinjaOutputDirProperty.get().asFile

        // ------------------------------------------------------------
        // TEMPLATE DIR CONTEXT
        // ------------------------------------------------------------

        final File templateDir = templateFile.getParentFile()

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

        // ------------------------------------------------------------
        // TEMPLATE FILE VARIABLES
        // ------------------------------------------------------------

        final File templateFileVariables = new File(templateFile.getAbsolutePath() + ".yaml")

        // Find the corresponding file from the source directory so we can use git on it.
        final File templateFileVariablesSrc = jinja2OutputDirPathToSrcDirPath(templateFileVariables)
        final VariablesFileData variablesFileData = readVariablesFile(templateFileVariablesSrc)
        templateFileContext.putAll(variablesFileData.toMap())

        // If the template variables file exists, see if it has later modification timestamps.
        if (templateFileVariables.exists()) {
            final def varsFileLastCommit = variablesFileData.varsFileLastCommit
            if (varsFileLastCommit.timestamp.isAfter(templateFileContext.last_commit.timestamp) && varsFileLastCommit.hash != "unspecified") {
                outputFileContext.last_commit = varsFileLastCommit
            }

            // Delete the template variables file from output directory as we only want the rendered templates.
            templateFileVariables.delete()
        }

        logger.info("Using `template_file` context:\n${yaml.dump(templateFileContext)}")

        // ------------------------------------------------------------
        // RENDER TEMPLATES
        // ------------------------------------------------------------

        // Process instances if present.
        final File instancesDir = new File(templateFile.getAbsolutePath() + ".d")
        if (instancesDir.exists() && instancesDir.isDirectory()) {
            instancesDir.traverse(type: FileType.FILES) { instanceFile ->
                renderInstanceFile(instanceFile, templateFile, context)
                instanceFile.delete()
            }
            // Delete the instance file from output directory as we only want the rendered templates.
            instancesDir.delete()
        } else {
            // No instances, just process in place.
            logger.debug("Using context:\n${yaml.dump(context)}")
            writeTemplateToFile(templateFile, context, templateOutputFile)
        }

        // Clean out context.
        context.remove('template_dir')
        context.remove('template_file')
        context.remove('output_file')
        // Delete the template file from output directory as we only want the rendered templates.
        templateFile.delete()
    }

    /**
     * Renders the specified instance file against the given context variables.
     *
     * @param instanceFile The instance file to render.
     * @param templateFile The corresponding template file for the instance file.
     * @param context The context variables for the template.
     */
    def renderInstanceFile(File instanceFile, File templateFile, Map<String, Object> context) {
        final File jinjaOutputDir = jinjaOutputDirProperty.get().asFile

        // Create an copy of the context since it needs to be mutated.
        def mutatedContext = context.getClass().<Map<String, Object>> newInstance(context)

        // Ignore file if it is not a yaml file
        if (!instanceFile.name.endsWith(".yaml")) {
            logger.info("Skipping non-YAML instance file [${instanceFile}]")
            return
        }

        // Find the corresponding file from the source directory so we can use git on it.
        final File instanceFileSrc = jinja2OutputDirPathToSrcDirPath(instanceFile)
        final VariablesFileData variablesFileData = readVariablesFile(instanceFileSrc)
        final String instanceFileSrcRelativePath = project.file(config.docsDir).toPath().relativize(instanceFileSrc.toPath())

        // We want to output the file at the same level as the template file.
        final String instanceOutputFileName = instanceFile.getName().replaceFirst(/\.yaml$/, '')
        final File instanceOutputFile = new File(templateFile.getParent(), instanceOutputFileName)
        final Map<String, Object> instanceContext = [
            name       : instanceFile.getName(),
            // Relative to docs directory.
            path       : instanceFileSrcRelativePath,
            last_commit: variablesFileData.varsFileLastCommit,
        ]
        instanceContext.putAll(variablesFileData.toMap())
        mutatedContext.put('instance_file', instanceContext)

        final Map<String, Object> outputFileContext = mutatedContext.output_file as Map<String, Object>
        outputFileContext.name = instanceOutputFileName
        outputFileContext.path = instanceOutputFile.getPath().replaceFirst(jinjaOutputDir.toString() + '/?', '')

        logger.info("Using `instance_file` context :\n${yaml.dump(instanceContext)}")

        final Map<String, Object> instanceFileLastCommit = variablesFileData.varsFileLastCommit
        final def outputFileLastCommit = outputFileContext.last_commit
        if (instanceFileLastCommit.timestamp.isAfter(outputFileLastCommit.timestamp) && instanceFileLastCommit.hash != "unspecified") {
            outputFileContext.last_commit = instanceFileLastCommit
        }

        logger.debug("Using context:\n${yaml.dump(mutatedContext)}")
        writeTemplateToFile(templateFile, mutatedContext, instanceOutputFile)
    }

    /**
     * Renders the supplied template against the supplied context and writes
     * it to an output file.
     *
     * @param templateFile Template to render.
     * @param context Context to use to fill the template.
     * @param outputFile File to write the result to.
     */
    void writeTemplateToFile(File templateFile, Map<String, Object> context, File outputFile) {
        try {
            // Create an snapshot of the context since it is mutable.
            def snapshot = context.getClass().<Map<String, Object>> newInstance(context)
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
            logger.error("Could not process `${templateFile}`", ex)
            throw new Exception("Could not process `${templateFile}` - ${ex.message}")
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
    static Map<String, Object> getLastCommit(String relativeFilePath, ZonedDateTime defaultTimestamp) {
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
    static Map<String, String> getFormattedTimestamps(ZonedDateTime timestamp) {
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
    Map<String, Object> getDirVariables(File dir, Map<String, Optional<Map<String, Object>>> loadedDirVariablesFiles) {
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

    // -------------------------------------------------------------------------
    // INNER CLASSES
    // -------------------------------------------------------------------------

    /**
     * Holds the results from reading a variables file. Contains:
     *
     * <ul>
     *     <li>The values read from a variables file (empty map if file does not exist).</li>
     *     <li>The values read its corresponding buildscript file (empty map if file does not exists).</li>
     *     <li>The last commit details of the variables file (empty map if file does not exists).</li>
     * </ul>
     */
    @Immutable
    class VariablesFileData {
        Map<String, Object> vars
        Map<String, Object> varsFileLastCommit
        Map<String, Object> buildscriptVars

        Map<String, Object> toMap() {
            return [
                vars: vars,
                vars_file_last_commit: varsFileLastCommit,
                buildscript_vars: buildscriptVars,
            ]
        }
    }
}
