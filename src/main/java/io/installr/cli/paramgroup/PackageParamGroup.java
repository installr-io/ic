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
package io.installr.cli.paramgroup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import io.installr.lib.pkg.InvalidPackageIdException;
import io.installr.lib.pkg.PackageId;
import lombok.Data;
import picocli.CommandLine.IParameterConsumer;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;

/**
 * Since positional parameters are not really supported within an ArgGroup, this group implements two converters that do
 * the job...
 */
@Data
public class PackageParamGroup {

    @Parameters(
        arity = "1..*",
        description = "List of package-ids to install",
        parameterConsumer = PackageIdConsumer.class,
        paramLabel = "<package-id>"
    )
    private List<PackageId> packageIds;

    @Parameters(
        arity = "1..*",
        description = "List of files to install",
        type = Path.class,
        parameterConsumer = PackageFileConsumer.class,
        paramLabel = "<file>"
    )
    private List<Path> packageFiles;

    private static abstract class AbstractBaseConsumer implements IParameterConsumer {
        @Override
        public void consumeParameters( Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec ) {
            // the first argument determines the type...
            final String firstArg = args.getLast();
            final boolean isFile = Files.isRegularFile( Path.of( firstArg ) );
            if ( !isFile && !isPackageId( firstArg ) ) {
                throw new ParameterException(
                    commandSpec.commandLine(),
                    String.format( "'%s' is neither a package-id nor a file.", firstArg ),
                    argSpec,
                    firstArg
                );
            }
            consumeParameters( args, argSpec, commandSpec, isFile );
        }

        protected abstract void consumeParameters(
            Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec, boolean isFile
        );

        private boolean isPackageId( String arg ) {
            try {
                PackageId.valueOf( arg );
                return true;
            }
            catch ( InvalidPackageIdException ex ) {
                return false;
            }
        }
    }

    private static class PackageIdConsumer extends AbstractBaseConsumer {
        @Override
        protected void consumeParameters(
            Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec, boolean isFile
        ) {
            if ( isFile ) {
                argSpec.setValue( null );
                return;
            }
            final List<PackageId> packageIds = new ArrayList<>();
            while ( !args.isEmpty() ) {
                final String currArg = args.pop();
                try {
                    packageIds.add( PackageId.valueOf( currArg ) );
                }
                catch ( InvalidPackageIdException ex ) {
                    throw new ParameterException(
                        commandSpec.commandLine(),
                        String.format( "'%s': %s.", currArg, ex.getMessage() ),
                        argSpec,
                        currArg
                    );
                }
            }
            argSpec.setValue( packageIds );
        }
    }

    private static class PackageFileConsumer extends AbstractBaseConsumer {
        @Override
        protected void consumeParameters(
            Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec, boolean isFile
        ) {
            if ( !isFile ) {
                argSpec.setValue( null );
                return;
            }
            final List<Path> files = new ArrayList<>();
            while ( !args.isEmpty() ) {
                final Path currArg = Path.of( args.pop() );
                if ( !Files.isRegularFile( currArg ) ) {
                    throw new ParameterException(
                        commandSpec.commandLine(),
                        String.format( "'%s' is not a file.", currArg ),
                        argSpec,
                        currArg.toString()
                    );
                }
                files.add( currArg );
            }
            argSpec.setValue( files );
        }
    }

}
