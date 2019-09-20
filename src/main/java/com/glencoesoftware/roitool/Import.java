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

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "import",
    description = "Import ROIs from OME-XML file into an OMERO server"
)

public class Import extends OMEROCommand implements Callable<Integer>
{
    private static final Logger log =
            LoggerFactory.getLogger(Import.class);

    @Option(
        names = "--help",
        usageHelp = true,
        description = "Display this help and exit"
    )
    boolean help;

    @Parameters(
        index = "0",
        description = "OMERO Image ID to link the ROIs"
    )
    Long imageId = null;

    @Parameters(
        index = "1",
        description = "Input OME-XML file"
    )
    File input;

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
            converter.importRoisFromFile(input);
        }
        finally
        {
            converter.close();
        }
        return 0;
    }

}
