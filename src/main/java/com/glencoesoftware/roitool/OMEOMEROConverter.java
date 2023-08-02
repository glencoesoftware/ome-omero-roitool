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
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import Glacier2.CannotCreateSessionException;
import Glacier2.PermissionDeniedException;
import com.google.common.collect.ImmutableMap;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.MissingLibraryException;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;
import ome.specification.XMLWriter;
import ome.system.Login;
import ome.xml.meta.MetadataConverter;
import ome.xml.model.OME;
import omero.ServerError;
import omero.api.IConfigPrx;
import omero.model.Annotation;
import omero.model.Image;
import omero.model.IObject;
import omero.model.Mask;
import omero.model.Roi;
import omero.model.Shape;
import omero.model.XmlAnnotation;
import omero.sys.ParametersI;

public class OMEOMEROConverter {

    /**
     * Namespace for the PathViewer annotation that controls ROI display order.
     */
    private static final String PATHVIEWER_NS = "glencoesoftware.com/pathviewer/roidisplayorder";

    private static final Logger log =
            LoggerFactory.getLogger(OMEOMEROConverter.class);

    /**
     * Default context which looks for the requested Image/ROI in all groups
     * that the user is a member of. This means that the user can specify an
     * Image ID only for import/export, without providing the corresponding group ID.
     * Omitting this context would query in the user's default group only.
     */
    public static final ImmutableMap<String, String> ALL_GROUPS_CONTEXT =
            ImmutableMap.of(Login.OMERO_GROUP, "-1");

    /**
     * OMERO Image ID for import/export.
     */
    private final long imageId;

    /**
     * Provides communication with OMERO.
     */
    private final ROIMetadataStoreClient target;

    /**
     * Helper service for translating between OMERO and OME objects.
     */
    private final OMEXMLService omeXmlService;

    /**
     * LSID format associated with the current OMERO database.
     * See https://docs.openmicroscopy.org/omero/5.6.3/sysadmins/config.html#omero-db-authority
     * and https://downloads.openmicroscopy.org/omero/5.4.0/api/ome/services/db/DatabaseIdentity.html
     */
    private String lsidFormat;

    public OMEOMEROConverter(long imageId)
            throws ServerError, DependencyException {
        this.imageId = imageId;
        this.target = new ROIMetadataStoreClient();
        ServiceFactory factory = new ServiceFactory();
        this.omeXmlService = factory.getInstance(OMEXMLService.class);
    }

    /**
     * Log in to OMERO with the specified credentials.
     *
     * @param username OMERO username or session key
     * @param password OMERO password or session key
     * @param server OMERO server URL
     * @param port OMERO server port
     * @param detachOnDestroy detach the session so that the key can be reused,
     *                        this should be true if a session key is passed in
     *                        instead of username and password
     */
    public void initialize(
            String username, String password, String server, int port,
            boolean detachOnDestroy)
        throws CannotCreateSessionException, PermissionDeniedException,
               ServerError
    {
        target.initialize(username, password, server, port);
        if (detachOnDestroy) {
            target.getServiceFactory().detachOnDestroy();
        }
        IConfigPrx iConfig = this.target.getServiceFactory().getConfigService();
        this.lsidFormat = String.format("urn:lsid:%s:%%s:%s_%%s:%%s",
                iConfig.getConfigValue("omero.db.authority"),
                iConfig.getDatabaseUuid());
    }

    /**
     * Log in to OMERO with an existing session key.
     *
     * @param server OMERO server URL
     * @param port OMERO server port
     * @param sessionKey existing session key to be reused
     */
    public void initialize(String server, int port, String sessionKey)
            throws CannotCreateSessionException, PermissionDeniedException,
                   ServerError
    {
        initialize(sessionKey, sessionKey, server, port, true);
    }

    /**
     * Import ROIs from the given file.
     * A valid session must have already been established using one of the
     * initialize(...) methods.
     *
     * @param input OME-XML containing ROIs
     * @return list of ROI objects imported, or null
     */
    public List<IObject> importRoisFromFile(File input)
            throws IOException, MissingLibraryException
    {
        log.info("ROI import started");
        String xml = new String(
                Files.readAllBytes(input.toPath()), StandardCharsets.UTF_8);
        log.debug("Importing OME-XML: {}", xml);

        OMEXMLMetadata xmlMeta;
        try {
            xmlMeta = omeXmlService.createOMEXMLMetadata(xml);
            log.info("Converting to OMERO metadata");
            MetadataConverter.convertMetadata(xmlMeta, target);
            log.info("ROI count: {}", xmlMeta.getROICount());
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
        catch (ServiceException s)
        {
            log.error("Exception creating OME-XML metadata", s);
        }
        return null;
    }

    /**
     * Export all ROIs associated with the selected Image ID to the given file.
     * If an ordering annotation is present, it will affect the ROI export order.
     * Mask ROIs are omitted from all exports.
     *
     * @param file output OME-XML file
     * @return list of exported ROIs
     */
    public List<? extends IObject> exportRoisToFile(File file)
            throws Exception {
        log.info("ROI export started");
        final OMEXMLMetadata xmlMeta = omeXmlService.createOMEXMLMetadata();
        xmlMeta.createRoot();
        List<Image> images = getImages();
        List<Roi> rois = getRois();
        List<Roi> orderedRois = new ArrayList<Roi>(rois.size());

        // get all annotations associated with the Image and its ROIs
        List<Annotation> allAnnotations = new ArrayList<Annotation>();
        for (final Image img : images) {
            allAnnotations.addAll(getAnnotations(img));
        }
        for (final Roi roi : rois) {
            List<Annotation> currentAnnotations = getAnnotations(roi);
            allAnnotations.addAll(currentAnnotations);
        }

        boolean foundIndex = false;
        for (final Annotation ann : allAnnotations) {
            if (ann instanceof XmlAnnotation && ann.getNs() != null &&
                ann.getNs().getValue().equals(PATHVIEWER_NS))
            {
                // PathViewer-specific JSON
                // if a valid displayorder annotation exists, reorder the list of ROIs accordingly

                JSONObject json = new JSONObject(((XmlAnnotation) ann).getTextValue().getValue());
                JSONArray shapeIds = json.getJSONArray("displayorder");
                for (int i=0; i<shapeIds.length(); i++) {
                    long shapeId = shapeIds.getLong(i);
                    int roiIndex = -1;
                    for (int r=0; r<rois.size(); r++) {
                        Roi roi = rois.get(r);
                        if (roi.getShape(0).getId().getValue() == shapeId) {
                            roiIndex = r;
                            break;
                        }
                    }
                    if (roiIndex >= 0) {
                        Roi toAdd = rois.get(roiIndex);
                        if (!Mask.class.isAssignableFrom(toAdd.getShape(0).getClass())) {
                            orderedRois.add(toAdd);
                        }
                    }
                    else {
                        orderedRois.add(null);
                    }
                }
                foundIndex = true;
                break;
            }
        }
        if (!foundIndex) {
            for (Roi r : rois) {
                if (!Mask.class.isAssignableFrom(r.getShape(0).getClass())) {
                    orderedRois.add(r);
                }
            }
        }

        log.debug("Annotations: {}", allAnnotations);
        log.info("Converting to OME-XML metadata");
        // translate Image, ROI, and annotation data from OMERO objects to OME objects
        // keeping the Image and annotation data makes it easier to use the OME-XML in
        // downstream applications
        try {
            omeXmlService.convertMetadata(
                    new ImageMetadata(this::getLsid, images), xmlMeta);
        }
        catch (Exception e) {
            log.warn("Failed to fully convert image metadata", e);
        }
        omeXmlService.convertMetadata(
                new ROIMetadata(this::getLsid, orderedRois), xmlMeta);
        omeXmlService.convertMetadata(
                new AnnotationMetadata(this::getLsid, allAnnotations), xmlMeta);
        log.info("ROI count: {}", xmlMeta.getROICount());
        log.info("Writing OME-XML to: {}", file.getAbsolutePath());
        XMLWriter xmlWriter = new XMLWriter();
        xmlWriter.writeFile(file, (OME) xmlMeta.getRoot(), false);
        return orderedRois;
    }

    /**
     * Find the LSID of the given OMERO model object.
     * Ported from <code>org.openmicroscopy.client.downloader.XmlGenerator</code>
     * @param object an OMERO model object, hydrated with its update event
     * @return the LSID for that object
     */
    private String getLsid(IObject object) {
        Class<? extends IObject> objectClass = object.getClass();
        if (objectClass == IObject.class) {
            throw new IllegalArgumentException(
                    "must be of a specific model object type");
        }
        while (objectClass.getSuperclass() != IObject.class) {
            objectClass = objectClass.getSuperclass().asSubclass(IObject.class);
        }
        final long objectId = object.getId().getValue();
        final long updateId =
                object.getDetails().getUpdateEvent().getId().getValue();
        return String.format(
                lsidFormat, objectClass.getSimpleName(), objectId, updateId);
    }

    /**
     * Query the server for the given ROIs.
     * Ported from <code>org.openmicroscopy.client.downloader.XmlGenerator</code>
     * @return the ROIs, hydrated sufficiently for conversion to XML
     * @throws ServerError if the ROIs could not be retrieved
     */
    private List<Roi> getRois() throws ServerError {
        final List<Roi> rois = new ArrayList<Roi>();
        for (final IObject result : target.getIQuery().findAllByQuery(
                "FROM Roi r " +
                "JOIN FETCH r.shapes AS s " +
                "WHERE r.image.id = :id",
                new ParametersI().addId(imageId),
                ALL_GROUPS_CONTEXT)) {
            rois.add((Roi) result);
        }
        return rois;
    }

    /**
     * Query the server for the current image.
     * Ported from <code>org.openmicroscopy.client.downloader.XmlGenerator</code>
     * @return a list containing the current image
     * @throws ServerError if the image could not be retrieved
     */
    private List<Image> getImages() throws ServerError {
        final List<Image> images = new ArrayList<Image>();
        for (final IObject result : target.getIQuery().findAllByQuery(
                "FROM Image i " +
                "LEFT OUTER JOIN FETCH i.pixels AS p " +
                "LEFT OUTER JOIN FETCH p.channels AS c " +
                "LEFT OUTER JOIN FETCH c.logicalChannel AS l " +
                "LEFT OUTER JOIN FETCH p.pixelsType " +
                "LEFT OUTER JOIN FETCH p.planeInfo " +
                "LEFT OUTER JOIN FETCH l.illumination " +
                "LEFT OUTER JOIN FETCH l.mode " +
                "LEFT OUTER JOIN FETCH l.contrastMethod " +
                "LEFT OUTER JOIN FETCH p.details.updateEvent " +
                "LEFT OUTER JOIN FETCH c.details.updateEvent " +
                "WHERE i.id = :id",
                new ParametersI().addId(imageId), ALL_GROUPS_CONTEXT))
        {
            images.add((Image) result);
        }
        return images;
    }

    /**
     * Fetch all annotations associated with a particular Image.
     *
     * @param image OMERO Image
     * @return list of linked annotations
     */
    private List<Annotation> getAnnotations(Image image) throws ServerError {
        final List<Annotation> anns = new ArrayList<Annotation>();

        for (final IObject result : target.getIQuery().findAllByQuery(
                "SELECT DISTINCT a " +
                        "FROM ImageAnnotationLink as l " +
                        "JOIN l.child as a " +
                        "WHERE l.parent.id = :id",
                new ParametersI().addId(image.getId().getValue()),
                ALL_GROUPS_CONTEXT)) {
            anns.add((Annotation) result);
        };

        return anns;
    }

    /**
     * Fetch all annotations associated with a particular ROI and its Shapes.
     *
     * @param roi OMERO Roi
     * @return list of linked annotations
     */
    private List<Annotation> getAnnotations(Roi roi) throws ServerError {
        final List<Annotation> anns = new ArrayList<Annotation>();

        // first get ROI-specific annotations
        for (final IObject result : target.getIQuery().findAllByQuery(
                "SELECT DISTINCT a " +
                        "FROM RoiAnnotationLink as l " +
                        "JOIN l.child as a " +
                        "WHERE l.parent.id = :id",
                new ParametersI().addId(roi.getId().getValue()),
                ALL_GROUPS_CONTEXT)) {
            anns.add((Annotation) result);
        };

        // now get shape-specific annotations for each shape in the ROI (usually just one)
        for (int i=0; i<roi.sizeOfShapes(); i++) {
            Shape shape = roi.getShape(i);
            for (final IObject result : target.getIQuery().findAllByQuery(
                "SELECT DISTINCT a FROM ShapeAnnotationLink as l JOIN l.child as a WHERE l.parent.id = :id",
                new ParametersI().addId(shape.getId().getValue()), ALL_GROUPS_CONTEXT))
            {
                anns.add((Annotation) result);
            }
        }

        return anns;
    }

    /**
     * Log out of the current session.
     */
    public void close()
    {
        if (this.target != null)
        {
            this.target.logout();
        }
    }

}
