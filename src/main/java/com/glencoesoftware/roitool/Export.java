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

import java.io.File;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
        name = "export",
        description = "Export ROIs to an OME-XML file from an OMERO server"
)
public class Export extends OMEROCommand implements Callable<Integer>
{
    private static final Logger log =
            LoggerFactory.getLogger(Export.class);

    @CommandLine.Option(
            names = "--help",
            usageHelp = true,
            description = "Display this help and exit"
    )
    boolean help;

    @Parameters(
            index = "0",
            description = "OMERO Image ID to export ROIs from"
    )
    Long imageId = null;

    @Parameters(
            index = "1",
            description = "Path to write OME-XML file to"
    )
    File output;

    @Override
    public Integer call() throws Exception
    {
        OMEOMEROConverter converter = createConverter(imageId);
        if (converter == null)
        {
            return -1;
        }

        try
        {
            converter.exportRoisToFile(output);
        }
        finally
        {
            converter.close();
        }
        return 0;
    }

}