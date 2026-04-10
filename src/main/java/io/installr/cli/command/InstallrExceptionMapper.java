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

import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

public class InstallrExceptionMapper implements ResponseExceptionMapper<ClientErrorException> {

    @Override
    public ClientErrorException toThrowable( Response response ) {
        if ( response.getStatus() == 400 ) {
            return new BadRequestException( response );
        }
        if ( response.getStatus() == 401 ) {
            return new NotAuthorizedException( response );
        }
        if ( response.getStatus() == 403 ) {
            return new ForbiddenException( response );
        }
        if ( response.getStatus() == 404 ) {
            return new NotFoundException( response );
        }
        return null;
    }

}
