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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import Glacier2.CannotCreateSessionException;
import Glacier2.PermissionDeniedException;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.MissingLibraryException;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;
import loci.formats.services.OMEXMLServiceImpl;
import ome.xml.meta.MetadataConverter;
import omero.ServerError;
import omero.model.IObject;

public class OMEOMEROConverter {

    long imageId;

    ROIMetadataStoreClient target;
 
    private static final Logger log =
            LoggerFactory.getLogger(OMEOMEROConverter.class);

    public OMEOMEROConverter(long imageId) {
        this.imageId = imageId;
        this.target = new ROIMetadataStoreClient();
    }

    public void initialize(String username, String password, String server, int port)
            throws CannotCreateSessionException, PermissionDeniedException,
                   ServerError
    {
        target.initialize(username, password, server, port);
    }

    public void initialize(String server, int port, String sessionKey)
            throws CannotCreateSessionException, PermissionDeniedException,
                   ServerError
    {
        target.initialize(server, port, sessionKey);
    }

    public List<IObject> importRoisFromFile(File input)
            throws IOException, MissingLibraryException
    {
        log.info("ROI import started");
        String xml = new String(
                Files.readAllBytes(input.toPath()), StandardCharsets.UTF_8);
            log.debug("Importing OME-XML: {}", xml);

        OMEXMLMetadata omexmlMeta;
        OMEXMLService service;
        try {
            ServiceFactory factory = new ServiceFactory();
            service = factory.getInstance(OMEXMLService.class);
            log.info("Creating omexmlMeta");
            omexmlMeta = service.createOMEXMLMetadata(xml);
            log.info("Converting to OMERO metadata");
            MetadataConverter.convertMetadata(omexmlMeta, target);
            log.info("ROI count: {}", omexmlMeta.getROICount());
            log.debug("Containers: {}",
                      target.countCachedContainers(null, null));
            log.debug("References: {}",
                      target.countCachedReferences(null, null));
            target.postProcess();
            try
            {
                List<IObject> rois = target.saveToDB(imageId);
                return rois;
            }
            catch (Exception e)
            {
                log.error("Exception saving to DB", e);
            }
        }
        catch (DependencyException de)
        {
            throw new MissingLibraryException(
                    OMEXMLServiceImpl.NO_OME_XML_MSG, de);
        }
        catch (ServiceException s)
        {
            log.error("Exception creating OME-XML metadata", s);
        }
        return null;
    }

    public void close()
    {
        if (this.target != null)
        {
            this.target.logout();
        }
    }

}
