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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ome.conditions.ApiUsageException;
import ome.formats.Index;
import ome.formats.OMEROMetadataStoreClient;
import ome.util.LSID;
import omero.ServerError;
import omero.api.ServiceFactoryPrx;
import omero.metadatastore.IObjectContainer;
import omero.model.IObject;
import omero.model.Image;
import omero.model.ImageI;
import omero.model.Roi;
import omero.model.Shape;
import omero.model.Annotation;

import static omero.rtypes.unwrap;

public class ROIMetadataStoreClient extends OMEROMetadataStoreClient {

    private static final Logger log =
            LoggerFactory.getLogger(ROIMetadataStoreClient.class);

    /** A list of all objects their LSIDs. */
    private Map<LSID, IObject> lsidMap = new HashMap<LSID, IObject>();

    /** A map of roiIndex vs. ROI object ordered by first access. */
    private Map<Integer, Roi> roiList =
        new LinkedHashMap<Integer, Roi>();

    /**
     * Returns a Roi model object based on its indexes within the
     * OMERO data model.
     * @param roiIndex Roi index.
     * @return See above.
     */
    private Roi getRoi(int roiIndex)
    {
        return roiList.get(roiIndex);
    }

    /**
     * Updates the server side MetadataStore with a list of our objects and
     * references and saves them into the database.
     * @param imageId id of the image to link the Rois to
     * @return List of Rois after database commit.
     */
    public List<IObject> saveToDB(long imageId) throws ServerError
    {
        Collection<IObjectContainer> containers =
                this.getContainerCache().values();
        IObjectContainer[] containerArray =
                containers.toArray(new IObjectContainer[containers.size()]);
        // Containers check
        log.debug("Starting containers....");
        for (LSID key : this.getContainerCache().keySet())
        {
            String s = String.format("%s == %s,%s,%s,%s",
                    key, this.getContainerCache().get(key).sourceObject,
                    this.getContainerCache().get(key).sourceObject.getId(),
                    this.getContainerCache().get(key).sourceObject.isLoaded(),
                    this.getContainerCache().get(key).LSID);
            log.debug(s);
        }
        // Reference check
        log.debug("Starting references....");
        for (String key : this.getReferenceStringCache().keySet())
        {
            for (String value : this.getReferenceStringCache().get(key))
            {
                String s = String.format("%s == %s", key, value);
                log.debug(s);
            }
        }
        log.debug("containerCache contains " + this.getContainerCache().size()
                  + " entries.");
        log.debug("referenceCache contains " + countCachedReferences(null, null)
                  + " entries.");
        // Object updates
        log.debug("Handling # of containers: {}", containerArray.length);
        for (IObjectContainer container : containerArray)
        {
            log.debug("{}, {}", container.LSID, container.indexes,
                      container.sourceObject);
            this.updateObject(container.LSID, container.sourceObject,
                              container.indexes);
        }
        // Reference updates
        String[] referenceKeys = this.getReferenceStringCache().keySet()
                .toArray(new String[this.getReferenceStringCache().size()]);
        log.debug("Handling # of references: {}", referenceKeys.length);
        this.updateReferences(this.getReferenceStringCache());
        // Save to DB
        log.info("Saving to DB");

        linkImage(imageId);
        ServiceFactoryPrx sf = this.getServiceFactory();
        List<IObject> rois = new ArrayList<IObject>(roiList.values());
        rois = sf.getUpdateService().saveAndReturnArray(rois);
        for (IObject roi : rois)
        {
            log.info("Saved ROI with ID: {}", unwrap(roi.getId()));
        }
        return rois;
    }

    /**
     * Updates a given model object in our object graph.
     * @param lsid LSID of model object.
     * @param sourceObject Model object itself.
     * @param indexes Any indexes that should are used to describe the model
     * object's graph location.
     */
    public void updateObject(String lsid, IObject sourceObject,
                             Map<String, Integer> indexes)
    {
        lsidMap.put(new LSID(lsid), sourceObject);
        if (sourceObject instanceof Roi)
        {
            log.debug("Handling Roi");
            handle(lsid, (Roi) sourceObject, indexes);
        }
        else if (sourceObject instanceof Shape)
        {
            log.debug("Handling Shape");
            handle(lsid, (Shape) sourceObject, indexes);
        }
        else if (sourceObject instanceof Annotation)
        {
            log.debug("Handling Annotation");
            handle(lsid, (Annotation) sourceObject, indexes);
        }
        else
        {
            throw new ApiUsageException(
                "Missing object handler for object type: "
                    + sourceObject.getClass());
        }
    }

    /**
     * Updates our object graph references.
     * @param referenceCache Client side LSID reference cache.
     */
    public void updateReferences(Map<String, String[]> referenceCache)
    {
        // This function is mostly processing back-references. e.g. If the OME
        // Schema has a AnnotationRef in ROI the referenceObject is Annotation
        // and the targetObject is ROI.
        for (String target : referenceCache.keySet())
        {
            for (String reference : referenceCache.get(target))
            {
                LSID targetLSID = new LSID(target);
                IObject targetObject = lsidMap.get(targetLSID);
                IObject referenceObject = lsidMap.get(
                        new LSID(stripCustomSuffix(reference)));
                log.debug(String.format(
                        "Updating reference handler for %s(%s) --> %s(%s).",
                        reference, referenceObject, target, targetObject));
                if (targetObject instanceof Roi)
                {
                    if (referenceObject instanceof Annotation) {
                        log.debug("Roi -> Annotation");
                        handleReference((Roi) targetObject,
                                        (Annotation) referenceObject);
                        continue;
                    }
                }
            }
        }
    }

    /**
     * Strips custom, reference only suffixes from LSID so that the object
     * may be correctly looked up.
     * @param LSID The LSID string to strip the suffix from.
     * @return A new LSID string with the suffix stripped or <code>LSID</code>.
     */
    private String stripCustomSuffix(String LSID)
    {
        if (LSID.endsWith("OMERO_EMISSION_FILTER")
            || LSID.endsWith("OMERO_EXCITATION_FILTER"))
        {
            return LSID.substring(0, LSID.lastIndexOf(':'));
        }
        return LSID;
    }

    /**
     * Handles inserting a specific type of model object into our object graph.
     * @param LSID LSID of the model object.
     * @param sourceObject Model object itself.
     * @param indexes Any indexes that should be used to reference the model
     * object.
     */
    private void handle(String LSID, Roi sourceObject,
                        Map<String, Integer> indexes)
    {
        roiList.put(indexes.get("roiIndex"), sourceObject);
    }

    /**
     * Handles inserting a specific type of model object into our object graph.
     * @param LSID LSID of the model object.
     * @param sourceObject Model object itself.
     * @param indexes Any indexes that should be used to reference the model
     * object.
     */
    private void handle(String LSID, Shape sourceObject,
                        Map<String, Integer> indexes)
    {
        log.debug("Adding shape");
        int roiIndex = indexes.get("roiIndex");
        Roi r = getRoi(roiIndex);
        r.addShape(sourceObject);
    }

    /**
     * Handles inserting a specific type of model object into our object graph.
     * @param LSID LSID of the model object.
     * @param sourceObject Model object itself.
     * @param indexes Any indexes that should be used to reference the model
     * object.
     */
    private void handle(String LSID, Annotation sourceObject,
                        Map<String, Integer> indexes)
    {
        // No-op.
    }

    /**
     * Handles linking a specific reference object to a target object in our
     * object graph.
     * @param target Target model object.
     * @param reference Reference model object.
     */
    private void handleReference(Roi target, Annotation reference)
    {
        target.linkAnnotation(reference);
    }

    public void linkImage(long imageId)
    {
        Image image = new ImageI(imageId, false);
        log.info("Linking ROIs to Image:{}", imageId);
        for (Roi roi : roiList.values())
        {
            log.debug("ROI name: {}", unwrap(roi.getName()));
            roi.setImage(image);
        }
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setROIName(java.lang.String, int)
     */
    @Override
    public void setROIName(String name, int ROIIndex)
    {
        
        LinkedHashMap<Index, Integer> indexes =
                new LinkedHashMap<Index, Integer>();
        indexes.put(Index.ROI_INDEX, ROIIndex);
        Roi o = (Roi) getIObjectContainer(Roi.class, indexes).sourceObject;
        o.setName(toRType(name));
    }
}
