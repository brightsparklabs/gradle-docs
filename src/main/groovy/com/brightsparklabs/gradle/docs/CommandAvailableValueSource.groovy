/*
 * Maintained by brightSPARK Labs.
 * www.brightsparklabs.com
 *
 * Refer to LICENSE at repository root for license details.
 */

package com.brightsparklabs.gradle.docs

import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * A ValueSource implementation that checks if a given command is available on the system.
 *
 * <p>This class is designed to be used with Gradle's configuration cache. It executes the
 * provided command and returns `true` if the command exits with code 0, indicating it is
 * available and executable. A non-zero exit code or any exception results in `false`.</p>
 *
 * <p>Example usage:
 * <pre>
 * def isGitAvailable = providers.of(CommandAvailableValueSource) {
 *     it.parameters { params ->
 *         params.command.set("git --version")
 *     }
 * }.get()
 * </pre>
 *
 * @see ValueSource
 * @see ProviderFactory#of(Class, Action)
 */
abstract class CommandAvailableValueSource implements ValueSource<Boolean, CommandAvailableValueSource.Parameters> {
    /**
     * The parameters for configuring the {@link CommandAvailableValueSource}.
     */
    interface Parameters extends ValueSourceParameters {
        /**
         * The command line to be executed (e.g. `git --version`).
         *
         * @return The command as a {@link Property}.
         */
        Property<String> getCommand()
    }

    /**
     * Injects the {@link ExecOperations} service, which is used to execute the external command.
     *
     * @return The injected {@link ExecOperations} instance.
     */
    @Inject
    abstract ExecOperations getExecOperations()

    /**
     * Executes the command specified by the parameters and determines its availability.
     *
     * <p>This method is called by Gradle when the value is requested. It uses the injected
     * {@link ExecOperations} to run the command. If the command executes and returns an exit code of 0,
     * this method returns `true`. Any other exit code or an exception (e.g. command not found)
     * results in `false`.</p>
     *
     * <p><b>Note:</b> This method will be called on every build when using the configuration cache,
     * so avoid using slow commands.</p>
     *
     * @return `true` if the command is available and exits with code 0, `false` otherwise.
     */
    @Override
    Boolean obtain() {
        def command = parameters.command.get()
        try {
            def result = execOperations.exec {
                it.commandLine(command.split(' '))
                it.isIgnoreExitValue(true)
            }
            return result.exitValue == 0
        } catch (Exception ignored) {
            return false
        }
    }
}
