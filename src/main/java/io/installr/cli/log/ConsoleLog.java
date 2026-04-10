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
package io.installr.cli.log;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConsoleLog {

    private static final int COLOR_RED = 31;
    private static final int COLOR_GREEN = 32;
    private static final int COLOR_YELLOW = 33;
    private static final int COLOR_MAGENTA = 35;
    private static final int COLOR_WHITE = 37;

    private static enum Label {
        Info( "===>", COLOR_WHITE ),
        Warn( "WARN", COLOR_YELLOW ),
        Error( "ERROR", COLOR_RED ),
        Skip( "SKIP", COLOR_MAGENTA ),
        Done( "DONE", COLOR_GREEN );

        final String labelString;
        final int colorCode;

        Label( String labelString, int colorCode ) {
            this.labelString = labelString;
            this.colorCode = colorCode;
        }

        String formatted() {
            return String.format( "\033[1m\033[%dm%-5s\033[0m", colorCode, labelString );
        }
    }

    private static enum Format {
        Plain( 0 ),
        Bold( 1 );

        final int code;

        Format( int code ) {
            this.code = code;
        }

        String formatted( String str ) {
            return String.format( "\033[%dm%s\033[0m", code, str );
        }
    }

    public void info( String message, Object ... args ) {
        print( Label.Info, message, args );
    }

    public void warn( String message, Object ... args ) {
        print( Label.Warn, message, args );
    }

    public void error( String message, Object ... args ) {
        print( Label.Error, message, args );
    }

    public void error( Throwable t ) {
        error( formatException( t ) );
    }

    public void skip( String message, Object ... args ) {
        print( Label.Skip, message, args );
    }

    public void done( String message, Object ... args ) {
        print( Label.Done, message, args );
    }

    public void bold( String message, Object ... args ) {
        print( Format.Bold, message + "\n", args );
    }

    public void out( String message, Object ... args ) {
        print( Format.Plain, message, args );
    }

    public void outln( String message, Object ... args ) {
        out( message + "\n", args );
    }

    private void print( Label label, String message, Object ... args ) {
        final String line = args == null ? message : String.format( message, args );
        System.out.printf( "%s %s\n", label.formatted(), line );
    }

    private void print( Format format, String message, Object ... args ) {
        final String line = args == null ? message : String.format( message, args );
        System.out.print( format.formatted( line ) );
    }

    private String formatException( Throwable t ) {
        if ( t == null ) {
            return null;
        }
        if ( t.getCause() != null ) {
            return t.getCause().getMessage();
        }
        if ( t.getMessage() != null ) {
            return t.getMessage();
        }
        return t.toString();
    }

}
