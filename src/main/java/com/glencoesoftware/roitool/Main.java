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

import ch.qos.logback.classic.Level;

import java.util.concurrent.Callable;

import net.sourceforge.argparse4j.annotation.Arg;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * @author Emil Rozbicki <emil@glencoesoftware.com>
 */
@Command(
    subcommands = {
        Import.class,
        Export.class
    }
)
public class Main implements Callable<Integer>
{
    @Option(
        names = "--help",
        usageHelp = true,
        description = "Display this help and exit"
    )
    boolean help;

    private static final Logger log =
            LoggerFactory.getLogger(Main.class);

    @Arg
    private String source;

    @Arg
    private String output;

    @Option(names = "--debug", description = "Set logging level to DEBUG")
    boolean debug;

    Main()
    {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        if (debug)
        {
            root.setLevel(Level.DEBUG);
        }
        else
        {
            root.setLevel(Level.INFO);
        }
    }

    public static void main(String[] args)
    {
        String version = Main.class.getPackage().getImplementationVersion();
        if (version == null)
        {
            version = "DEV";
        }
        log.info("ROI tool {} started", version);
        Integer returnCode = CommandLine.call(new Main(), args);
        if (returnCode != null)
        {
            System.exit(returnCode);
        }
    }

    @Override
    public Integer call() {
        return 0;
    }

}
