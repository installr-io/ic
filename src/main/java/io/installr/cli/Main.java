/*
 * Installr.io
 *
 * Copyright (c) 2025-present Dorian Vallant
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.installr.cli;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.installr.cli.command.InstallCommand;
import io.installr.cli.command.ReInstallCommand;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.jbosslog.JBossLog;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

@QuarkusMain
@JBossLog
public class Main implements QuarkusApplication {

    @Inject
    CommandLine.IFactory factory;

    @Override
    public int run( String ... args ) {
        final CommandLine cl = new CommandLine( new MainCommand(), factory );
        // since picocli prints the entire stacktrace per default, we set out own execution exception handler
        cl.setExecutionExceptionHandler( ( ex, commandLine, fullParseResult ) -> {
            log.error( ex.getMessage() == null ? ex.toString() : ex.getMessage() );
            return 1;
        } );
        return cl.execute( args );
    }

    public static void main( String ... args ) {
        Quarkus.run( Main.class, args );
    }

    @Command(
        name = "ic",
        versionProvider = VersionProvider.class,
        header = "ic - Command line client for installrd%n",
        mixinStandardHelpOptions = true,
        subcommands = {
            InstallCommand.class,
            ReInstallCommand.class
        }
    )
    public static class MainCommand {
        @Option(
            names = { "-n", "--namespace" },
            defaultValue = "default",
            description = "Set the namespace to use.",
            showDefaultValue = Visibility.ALWAYS,
            scope = ScopeType.INHERIT,
            arity = "1"
        )
        public String namespace;

        @Option(
            names = { "-c", "--config" },
            description = "Set the config (installrd connection) of ~/.ic/config.yaml to use; " + "default: 'default-config' of ~/.ic/config.yaml.",
            scope = ScopeType.INHERIT,
            arity = "1"
        )
        public String config;
    }

    // https://github.com/quarkusio/quarkus/issues/29379#issuecomment-1879795931
    @Singleton
    @Unremovable
    static class VersionProvider implements IVersionProvider {
        @ConfigProperty( name = "quarkus.application.version" )
        String version;

        @Override
        public String[] getVersion() throws Exception {
            return new String[] { version };
        }
    }

}
