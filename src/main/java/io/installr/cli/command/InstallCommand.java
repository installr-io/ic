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
package io.installr.cli.command;

import picocli.CommandLine.Command;

@Command(
    name = "install",
    description = "Install one or more packages."
)
public class InstallCommand extends AbstractInstallCommand {

    @Override
    public void run() {
        super.run(
            ( installr, pkgResource, fileResource ) -> {
                if ( pkgResource != null ) {
                    return installr.install( namespace(), pkgResource );
                }
                if ( fileResource != null ) {
                    return installr.install( namespace(), fileResource );
                }
                throw new IllegalStateException();
            }
        );
    }

}
