/**
 * -----------------------------------------------------------------------------
 *   Copyright (C) 2015 Glencoe Software, Inc. All rights reserved.
 *
 *
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License along
 *   with this program; if not, write to the Free Software Foundation, Inc.,
 *   51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * ------------------------------------------------------------------------------
 *
 * QuPath OME-XML Exporter
 *
 * This script will export ROIs (detections and annotations) from a QuPath file
 * to an OME-XML file.
 *
 * Instructions:
 *   - Open the QuPath file containing the ROIs to be exported
 *   - Open this file in the QuPath "Script editor"
 *   - Choose "Run" from the Run menu
 *   - When prompted, choose the location and filename for the exported OME-XML
 */

import ome.units.UNITS
import ome.units.quantity.Length
import ome.xml.model.*
import ome.xml.model.enums.FillRule
import ome.xml.model.primitives.Color
import ome.xml.model.primitives.NonNegativeInteger
import qupath.lib.common.ColorTools
import qupath.lib.gui.prefs.PathPrefs
import qupath.lib.gui.scripting.QPEx
import qupath.lib.objects.PathROIObject
import qupath.lib.roi.*

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Result
import javax.xml.transform.Source
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

ome = new OME()
structuredAnnotations = new StructuredAnnotations()

detections = QPEx.getDetectionObjects()
annotations = QPEx.getAnnotationObjects()
QPEx.getProjectEntryMetadataValue()
rois = detections + annotations

print(rois.size())

static void setCommonProperties(Shape shape, PathROIObject path, qupath.lib.roi.interfaces.ROI roi) {
    // Set remaining ROI properties
    if (roi.c > -1) {
        shape.setTheC(new NonNegativeInteger(roi.c))
    }
    shape.setTheT(new NonNegativeInteger(roi.t))
    shape.setTheZ(new NonNegativeInteger(roi.z))

    // Unpack the color
    def packedColor = path.colorRGB
    if (packedColor != null) {
        def color = new Color(ColorTools.red(packedColor),
                ColorTools.green(packedColor),
                ColorTools.blue(packedColor),
                ColorTools.alpha(packedColor))
        shape.setStrokeColor(color)
        // Note: QuPath does not allow stroke and fill color to be specified separately
        // shape.setFillColor(color)
    }

    // Note: Not currently used by QuPath, but may be useful for "cutout" ROIs
    shape.setFillRule(FillRule.NONZERO)

    // Note: Currently, QuPath only applies font settings to image labels and scale bars, not ROIs
    // shape.setFontFamily()
    // shape.setFontSize()
    // shape.setFontStyle()

    // Note: QuPath sets stroke thickness as a system-wide property, with one thickness for annotations
    // and another for detections. We'll store the stroke width on each ROI, but when loading an OME-XML
    // the stroke width of last ROI loaded will be used to set the system property.
    if (path.isAnnotation()) {
        shape.setStrokeWidth(new Length(PathPrefs.getThickStrokeThickness(), UNITS.PIXEL))
    } else if (path.isDetection()) {
        shape.setStrokeWidth(new Length(PathPrefs.getThinStrokeThickness(), UNITS.PIXEL))
    }

    // Note: Currently, QuPath does not allow for dashed lines
    // shape.setStrokeDashArray()
}

rois.eachWithIndex { PathROIObject path, int i ->
    def roi = path.getROI()
    print(String.format("ROI type: %s", roi.class))
    def mapAnnotationID = String.format("MapAnnotation-%s", i)
    def shapeID = String.format("Shape:%s", i)
    def roiID = String.format("ROI-%s", i)

    // New ROI
    def omeROI = new ROI()
    omeROI.setID(roiID)

    // Instantiate the class of the shape from the type of ROI and set class specific properties
    switch (roi) {
        case EllipseROI:
            def ellipse = roi as EllipseROI
            def shape = new Ellipse()
            def union = new Union()
            shape.setID(shapeID)
            shape.setX(ellipse.x)
            shape.setY(ellipse.y)
            shape.setRadiusX(ellipse.x2 - ellipse.x)
            shape.setRadiusY(ellipse.y2 - ellipse.y)
            setCommonProperties(shape, path, roi)
            union.addShape(shape as Shape)
            omeROI.setUnion(union)
            break
        case LineROI:
            def line = roi as LineROI
            def shape = new Line()
            def union = new Union()
            shape.setID(shapeID)
            shape.setX1(line.x1)
            shape.setY1(line.y1)
            shape.setX2(line.x2)
            shape.setY2(line.y2)
            // Note: Currently, QuPath does not make use of line endcaps (i.e. arrows)
            // shape.setMarkerStart()
            // shape.setMarkerEnd()
            setCommonProperties(shape, path, roi)
            union.addShape(shape as Shape)
            omeROI.setUnion(union)
            break
        case PointsROI:
            def points = roi as PointsROI
            def union = new Union()
            points.getPointList().eachWithIndex { point, pointidx ->
                def shape = new Point()
                shape.setID(shapeID + "." + pointidx)
                shape.setX(point.x)
                shape.setY(point.y)
                setCommonProperties(shape, path, roi)
                union.addShape(shape)
            }
            omeROI.setUnion(union)
            break
        case PolygonROI:
            def polygon = roi as PolygonROI
            def shape = new Polygon()
            def union = new Union()
            def points = polygon.getPolygonPoints().collect { String.format("%f,%f", it.getX(), it.getY()) }.join(" ")
            shape.setID(shapeID)
            shape.setPoints(points)
            setCommonProperties(shape, path, roi)
            union.addShape(shape)
            omeROI.setUnion(union)
            break
        case PolylineROI:
            def polyline = roi as PolylineROI
            def shape = new Polyline()
            def union = new Union()
            def points = polyline.getPolygonPoints().collect { String.format("%f,%f", it.getX(), it.getY()) }.join(" ")
            shape.setID(shapeID)
            shape.setPoints(points)
            setCommonProperties(shape, path, roi)
            union.addShape(shape as Shape)
            omeROI.setUnion(union)
            break
        case RectangleROI:
            def rect = roi as RectangleROI
            def shape = new Rectangle()
            def union = new Union()
            shape.setID(shapeID)
            shape.setX(rect.x)
            shape.setY(rect.y)
            shape.setWidth(rect.x2 - rect.x)
            shape.setHeight(rect.y2 - rect.y)
            setCommonProperties(shape, path, roi)
            union.addShape(shape as Shape)
            omeROI.setUnion(union)
            break
    }

    // Map Annotation
    def mapAnnotation = new MapAnnotation()
    mapAnnotation.setID(mapAnnotationID)

    // Add all the key-value pairs
    def pairList = new ArrayList<MapPair>()
    pairList.add(new MapPair("qupath:class", roi.class.name))
    if (path.getName()) {
        pairList.add(new MapPair("qupath:name", path.getName()))
    }
    pairList.add(new MapPair("qupath:is-annotation", path.isAnnotation().toString()))
    pairList.add(new MapPair("qupath:is-detection", path.isDetection().toString()))
    path.retrieveMetadataKeys().each {
        pairList.add(new MapPair("qupath:metadata:" + it, path.retrieveMetadataValue(it).toString()))
    }
    mapAnnotation.setValue(pairList)

    // Finalize ROI
    omeROI.linkAnnotation(mapAnnotation)
    ome.addROI(omeROI)
    structuredAnnotations.addMapAnnotation(mapAnnotation)
}

ome.setStructuredAnnotations(structuredAnnotations);

// Create document
document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
root = ome.asXMLElement(document)
document.appendChild(root)

// Transform to string
transformer = TransformerFactory.newInstance().newTransformer()
transformer.setOutputProperty(OutputKeys.INDENT, "yes")
transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
source = new DOMSource(document) as Source
os = new ByteArrayOutputStream()
result = new StreamResult(new OutputStreamWriter(os, "utf-8")) as Result
transformer.transform(source, result)
xmlDocument = os.toString()
qupath = QPEx.getQuPath()
file = qupath.getDialogHelper().promptToSaveFile("Choose OME-XML export location", null, null, "OME-XML", ".ome.xml")
outputStream = new FileOutputStream(file)
outputStream.write(xmlDocument.getBytes())
