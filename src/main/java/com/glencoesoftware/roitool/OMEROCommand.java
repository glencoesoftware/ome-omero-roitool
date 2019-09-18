/*
 * Copyright (C) 2019 Glencoe Software, Inc. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.glencoesoftware.roitool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import Glacier2.CannotCreateSessionException;
import Glacier2.PermissionDeniedException;
import loci.common.services.DependencyException;
import omero.ServerError;
import picocli.CommandLine;

public abstract class OMEROCommand
{
    private static final Logger log =
            LoggerFactory.getLogger(OMEROCommand.class);

    @CommandLine.Option(
            names = "--port",
            description = "OMERO server port"
    )
    int port = 4064;

    @CommandLine.Option(
            names = "--server",
            description = "OMERO server address"
    )
    String server = "localhost";

    @CommandLine.Option(
            names = "--username",
            description = "OMERO user name"
    )
    String username = null;

    @CommandLine.Option(
            names = "--password",
            description = "OMERO password"
    )
    String password = null;

    @CommandLine.Option(
            names = "--key",
            description = "OMERO session key"
    )
    String sessionKey = null;

    /**
     * Creates an OME OMERO converter which will be initialized with the
     * server, port, and session key or username/password pair available to
     * this class
     * @param imageId OMERO Image ID to export ROIs from
     * @return Initialized OME OMERO converter
     * @throws ServerError If there is an error communicating with OMERO
     * during initialization
     * @throws DependencyException If there is a problem creating the OME-XML
     * metadata service.
     * @throws PermissionDeniedException
     * @throws CannotCreateSessionException
     */
    public OMEOMEROConverter createConverter(long imageId)
            throws ServerError, DependencyException,
                   CannotCreateSessionException, PermissionDeniedException
    {
        OMEOMEROConverter converter = new OMEOMEROConverter(imageId);
        if (username != null)
        {
            converter.initialize(username, password, server, port);
        }
        else if (sessionKey != null)
        {
            converter.initialize(server, port, sessionKey);
        }
        else
        {
            log.error("No OMERO username/password or session key, can't run!");
            return null;
        }
        return converter;
    }
}