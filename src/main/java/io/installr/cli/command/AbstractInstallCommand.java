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

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.net.ssl.SSLHandshakeException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.installr.cli.log.ConsoleLog;
import io.installr.cli.paramgroup.PackageParamGroup;
import io.installr.lib.installrd.client.ClientConfiguration;
import io.installr.lib.installrd.client.InstallrdClients;
import io.installr.lib.installrd.client.InstallrdConnection;
import io.installr.lib.installrd.controller.InstallController;
import io.installr.lib.installrd.event.Event;
import io.installr.lib.installrd.event.Events;
import io.installr.lib.installrd.event.Status;
import io.installr.lib.installrd.resource.InstallFileResource;
import io.installr.lib.installrd.resource.InstallPackageResource;
import io.installr.lib.installrd.resource.InstallResource;
import io.installr.lib.pkg.PackageId;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import picocli.CommandLine.ArgGroup;

abstract class AbstractInstallCommand extends AbstractCommand {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ErrorResponse {
        private String message;
    }

    @ArgGroup( exclusive = true, multiplicity = "1" )
    protected PackageParamGroup packages;

    @ConfigProperty( name = "ic.config-file" )
    String configFile;

    @Inject
    ConsoleLog log;

    @ConfigProperty( name = "quarkus.application.version" )
    String version;

    private final AtomicReference<Status> txStatus = new AtomicReference<>();
    private final AtomicInteger totalPackages = new AtomicInteger( 0 );

    protected final void run(
        BiFunction<InstallController, InstallResource, Multi<Event>> executor
    ) {
        final InstallrdConnection connection = ClientConfiguration.load( Path.of( configFile ) ).select( configName() );
        final InstallController installr = InstallrdClients.create(
            connection,
            "ic/" + version,
            InstallrExceptionMapper.class
        );
        try {
            // blocks until the event stream is completed; failures are thrown in this thread
            execute( installr, executor )
                .onItem()
                .invoke( this::writeLog )
                .collect()
                .last()
                .await()
                .indefinitely();
        }
        catch ( NotAuthorizedException ex ) {
            throw new RuntimeException(
                String.format( "installrd (config '%s') did not accept the client certificate.", connection.name() ),
                ex
            );
        }
        catch ( ClientErrorException ex ) {
            throw new RuntimeException( errorMessage( ex ), ex );
        }
        catch ( RuntimeException ex ) {
            final SSLHandshakeException sslEx = findCause( ex, SSLHandshakeException.class );
            if ( sslEx != null ) {
                throw new RuntimeException(
                    String.format( "TLS handshake with %s failed: %s", connection.url(), sslEx.getMessage() ),
                    ex
                );
            }
            throw ex;
        }
        final Status status = txStatus.get();
        if ( status == null ) {
            throw new RuntimeException( "installrd closed the connection before the transaction finished." );
        }
        if ( status.type() != Status.Type.Success && status.type() != Status.Type.Skipped ) {
            // details are already logged with the TransactionFinished event
            throw new RuntimeException( String.format( "Transaction failed (%s).", status.type() ) );
        }
    }

    private static String errorMessage( ClientErrorException ex ) {
        final Response response = ex.getResponse();
        try {
            final ErrorResponse er = response.readEntity( ErrorResponse.class );
            if ( er != null && er.getMessage() != null && !er.getMessage().isBlank() ) {
                return er.getMessage();
            }
        }
        catch ( RuntimeException ignored ) {
            // empty or non-JSON body
        }
        if ( response.getStatus() == 403 ) {
            return "Access denied.";
        }
        return String.format( "%d %s", response.getStatus(), response.getStatusInfo().getReasonPhrase() );
    }

    private static <T extends Throwable> T findCause( Throwable t, Class<T> type ) {
        for ( Throwable cause = t; cause != null; cause = cause.getCause() ) {
            if ( type.isInstance( cause ) ) {
                return type.cast( cause );
            }
        }
        return null;
    }

    protected Multi<Event> execute(
        InstallController installr,
        BiFunction<InstallController, InstallResource, Multi<Event>> executor
    ) {
        if ( packages.getPackageIds() != null ) {
            final InstallPackageResource resource = new InstallPackageResource();
            resource.setPackageIds( packages.getPackageIds() );
            return executor.apply( installr, resource );
        }
        else if ( packages.getPackageFiles() != null ) {
            final InstallFileResource resource = new InstallFileResource();
            resource.setFiles(
                packages.getPackageFiles().stream()
                    .map( Path::toAbsolutePath )
                    .map( Path::toString )
                    .toList()
            );
            return executor.apply( installr, resource );
        }
        throw new IllegalStateException( "packageIds && files is null" );
    }

    private void writeLog( Event event ) {
        switch ( event ) {
            case Events.TransactionStarted( UUID id, Instant timestamp, Status status )                                                       -> {
                log.info( "Starting new transaction: %s", id );
                totalPackages.set( 0 );
            }
            case Events.PackageCacheOutdated( Instant lastUpdated, Instant timestamp, Status status )                                         -> {
                log.info( "Package cache is outdated (last updated: %s)", lastUpdated );
            }
            case Events.PackageIndexUpdateStarted( Instant timestamp, Status status )                                                         -> {
                log.info( "Updating package index..." );
            }
            case Events.PackageIndexDownloadStarted( String repository, Instant timestamp, Status status )                                    -> {
                log.info( "Downloading repository index: %s", repository );
            }
            case Events.PackageIndexDownloadFinished( String repository, Instant timestamp, Status status )                                   -> {
                logStatus( status, "=> Downloading repository index: %s", repository );
            }
            case Events.PackageIndexUpdateFinished( Instant timestamp, Status status )                                                        -> {
                logStatus( status, "=> Updating package index." );
            }
            case Events.PackageResolvingStarted( Instant timestamp, Status status )                                                           -> {
                log.info( "Resolving packages and dependencies..." );
            }
            case Events.PackageResolvingFinished( int total, List<PackageId> notFoundPackages, Instant timestamp, Status status )             -> {
                logStatus( status, "=> Resolving packages and dependencies" );
                log.info( "    Total packages: %d", total );
                log.info( "    Not found packages: %d", notFoundPackages.size() );
                if ( notFoundPackages != null && !notFoundPackages.isEmpty() ) {
                    log.error(
                        "The following packages could not be found:\n%s",
                        notFoundPackages.stream()
                            .map( PackageId::toString ).collect( Collectors.joining( "\n" ) )
                    );
                }
            }
            case Events.DownloadProcessStarted( int total, Instant timestamp, Status status )                                                 -> {
                log.info( "%d Packages need to be downloaded", total );
            }
            case Events.PackageDownloadStarted( PackageId packageId, Instant timestamp, Status status )                                       -> {
                log.info( "Downloading package: %s: ...", packageId );
            }
            case Events.PackageDownloadFinished( PackageId packageId, Instant timestamp, Status status )                                      -> {
                logStatus( status, "=> Downloading package: %s:", packageId );
            }
            case Events.DownloadProcessFinished( Instant timestamp, Status status )                                                           -> {
            }
            case Events.InstallationProcessStarted( int total, Instant timestamp, Status status )                                             -> {
                totalPackages.set( total );
                log.info( "Starting installation of %d packages:", total );
            }
            case Events.PackageInstallationStarted( PackageId packageId, int packageIndex, int totalItems, Instant timestamp, Status status ) -> {
                log.bold( "[%d/%d] %s:", packageIndex, totalPackages.get(), packageId );
            }
            case Events.PackageContentDeploymentStarted( String itemPath, int itemIndex, Instant timestamp, Status status )                   -> {
                log.info( "Processing '%s'...", itemPath );
            }
            case Events.PackageContentDeploymentFinished( String itemPath, int itemIndex, Instant timestamp, Status status )                  -> {
                logStatus( status, "=> Processing '%s'.", itemPath );
            }
            case Events.PackageInstallationFinished( PackageId packageId, int packageIndex, Instant timestamp, Status status )                -> {
            }
            case Events.InstallationProcessFinished( Instant timestamp, Status status )                                                       -> {
                log.info( "Installation finished with status '%s'.", status.type() );
            }
            case Events.TransactionFinished( UUID id, Instant timestamp, Status status )                                                      -> {
                if ( status.message() != null && !status.message().isBlank() ) {
                    log.error( status.message() );
                }
                log.info( "Transaction '%s' finished with status '%s'", id, status.type() );
                txStatus.set( status );
            }
        }
    }

    private void logStatus( Status status, String message, Object ... args ) {
        int spaces = 6;
        switch ( status.type() ) {
            case Success:
                log.done( message, args );
                break;
            case Skipped:
                log.skip( message, args );
                break;
            case Canceled:
                log.warn( message, args );
                break;
            case Failed:
            case Fatal:
                log.error( message, args );
                break;
        }
        if ( status.message() != null && !status.message().isBlank() ) {
            final String lpad = String.format( "%" + spaces + "s", " " );
            log.outln( "%s%s", lpad, status.message() );
        }
    }

}
