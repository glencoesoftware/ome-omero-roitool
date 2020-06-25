/**
 * -----------------------------------------------------------------------------
 *   Copyright (C) 2019 Glencoe Software, Inc. All rights reserved.
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


import java.awt.BasicStroke
import loci.formats.gui.AWTImageTools
import ome.codecs.Base64Codec
import ome.codecs.BitWriter
import ome.codecs.Codec
import ome.codecs.ZlibCodec
import ome.specification.XMLWriter
import ome.units.UNITS
import ome.units.quantity.Length
import ome.xml.model.*
import ome.xml.model.enums.Compression
import ome.xml.model.enums.FillRule
import ome.xml.model.primitives.Color
import ome.xml.model.primitives.NonNegativeInteger
import ome.xml.model.primitives.NonNegativeLong
import qupath.lib.common.ColorTools
import qupath.lib.common.GeneralTools
import qupath.lib.gui.dialogs.Dialogs
import qupath.lib.gui.prefs.PathPrefs
import qupath.lib.gui.scripting.QPEx
import qupath.lib.objects.PathROIObject
import qupath.lib.roi.*

// first check the version; only 0.2.0-m10 and later are supported
version = GeneralTools.getVersion()
versionTokens = version.split("-")
if (versionTokens[0].startsWith("0.1") || (versionTokens.length == 2 && versionTokens[0].equals("0.2.0") && Integer.parseInt(versionTokens[1].substring(1)) < 10)) {
    throw new RuntimeException("Unsupported QuPath version: " + version)
}

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

    shape.setLocked(path.isLocked())

    // Unpack the color
    def packedColor = path.colorRGB
    if (packedColor == null) {
        if (path.pathClass != null) {
            packedColor = path.pathClass.color
        } else {
            packedColor = PathPrefs.colorDefaultObjectsProperty().get()
        }
    }
    if (packedColor != null) {
        def color = new Color(ColorTools.red(packedColor),
                ColorTools.green(packedColor),
                ColorTools.blue(packedColor),
                ColorTools.alpha(packedColor))
        shape.setStrokeColor(color)
        // Note: QuPath does not allow stroke and fill color to be specified separately
        def overlayOptions = QPEx.currentViewer.getOverlayOptions()
        overlayOptions.getFillAnnotations()
        if ((path.isAnnotation() && overlayOptions.fillAnnotations) ||
                (path.isDetection() && overlayOptions.fillDetections)) {
             shape.setFillColor(color)
        }
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
        shape.setStrokeWidth(new Length(PathPrefs.annotationStrokeThicknessProperty().get(), UNITS.PIXEL))
    } else if (path.isDetection()) {
        shape.setStrokeWidth(new Length(PathPrefs.detectionStrokeThicknessProperty().get(), UNITS.PIXEL))
    }

    // Note: Currently, QuPath does not allow for dashed lines
    // shape.setStrokeDashArray()
}

void addROI(qupath.lib.roi.interfaces.ROI roi, PathROIObject path, int index) {
    print(String.format("ROI type: %s", roi.class))
    def mapAnnotationID = String.format("MapAnnotation-%s", index)
    def shapeID = String.format("Shape:%s:%s", index, 0)
    def roiID = String.format("ROI-%s", index)

    // New ROI
    def omeROI = new ROI()
    omeROI.setID(roiID)
    if (path.pathClass != null) {
        omeROI.setName(path.pathClass.name)
    }

    // Map Annotation
    def mapAnnotation = new MapAnnotation()
    mapAnnotation.setID(mapAnnotationID)

    // Add all the key-value pairs
    def pairList = new ArrayList<MapPair>()
    if (path.pathClass != null) {
        pairList.add(new MapPair("qupath:class", path.pathClass.name))
    }
    if (path.getName()) {
        pairList.add(new MapPair("qupath:name", path.getName()))
    }
    else if (path.isCell()) {
        name = roi.equals(path.getNucleusROI()) ? "cell nucleus" : "cell boundary"
        pairList.add(new MapPair("qupath:name", name))
    }
    pairList.add(new MapPair("qupath:is-annotation", path.isAnnotation().toString()))
    pairList.add(new MapPair("qupath:is-detection", path.isDetection().toString()))
    path.retrieveMetadataKeys().each {
        pairList.add(new MapPair("qupath:metadata:" + it, path.retrieveMetadataValue(it).toString()))
    }
    mapAnnotation.setValue(pairList)

    def union = new Union()

    // Instantiate the class of the shape from the type of ROI and set class specific properties
    switch (roi) {
        case EllipseROI:
            def ellipse = roi as EllipseROI
            def shape = new Ellipse()
            shape.setID(shapeID)
            shape.setX(ellipse.getCentroidX())
            shape.setY(ellipse.getCentroidY())
            shape.setRadiusX(ellipse.getBoundsWidth() / 2)
            shape.setRadiusY(ellipse.getBoundsHeight() / 2)
            setCommonProperties(shape, path, roi)
            union.addShape(shape as Shape)
            break
        case LineROI:
            def line = roi as LineROI
            def shape = new Line()
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
            break
        case PointsROI:
            def points = roi as PointsROI
            points.getPointList().eachWithIndex { point, pointidx ->
                def shape = new Point()
                shape.setID(shapeID + "." + pointidx)
                shape.setX(point.x)
                shape.setY(point.y)
                setCommonProperties(shape, path, roi)
                union.addShape(shape)
            }
            break
        case PolygonROI:
            def polygon = roi as PolygonROI
            def shape = new Polygon()
            def points = polygon.getAllPoints().collect { String.format("%f,%f", it.getX(), it.getY()) }.join(" ")
            shape.setID(shapeID)
            shape.setPoints(points)
            setCommonProperties(shape, path, roi)
            union.addShape(shape)
            break
        case AreaROI:
            def polygon = roi as AreaROI
            def shape = new Polygon()
            def points = polygon.getAllPoints().collect { String.format("%f,%f", it.getX(), it.getY()) }.join(" ")
            shape.setID(shapeID)
            shape.setPoints(points)
            setCommonProperties(shape, path, roi)
            union.addShape(shape)
            break
        case PolylineROI:
            def polyline = roi as PolylineROI
            def shape = new Polyline()
            def points = polyline.getAllPoints().collect { String.format("%f,%f", it.getX(), it.getY()) }.join(" ")
            shape.setID(shapeID)
            shape.setPoints(points)
            setCommonProperties(shape, path, roi)
            union.addShape(shape as Shape)
            break
        case RectangleROI:
            def rect = roi as RectangleROI
            def shape = new Rectangle()
            shape.setID(shapeID)
            shape.setX(rect.x)
            shape.setY(rect.y)
            shape.setWidth(rect.getBoundsWidth())
            shape.setHeight(rect.getBoundsHeight())
            setCommonProperties(shape, path, roi)
            union.addShape(shape as Shape)
            break
        case GeometryROI:
            // arbitrary geometry resulting from wand tool
            // may or may not represent distinct polygons
            // easiest way to represent is as a mask
            def geom = roi as GeometryROI

            // construct a blank image matching the ROI bounding box
            originX = geom.getBoundsX()
            originY = geom.getBoundsY()
            width = geom.getBoundsWidth()
            height = geom.getBoundsHeight()
            img = new BufferedImage(width as int, height as int, BufferedImage.TYPE_BYTE_GRAY)

            // draw the shape onto the image
            graphics = img.createGraphics()
            strokeWidth = PathPrefs.annotationStrokeThicknessProperty().get()
            graphics.setStroke(new BasicStroke(strokeWidth))
            graphics.setColor(java.awt.Color.WHITE)

            // make sure to translate shape so that it is within the bounds of the mask
            geom = geom.translate(-1 * originX, -1 * originY)
            drawableShape = geom.getShape()
            graphics.draw(drawableShape)

            // convert the image to a binary mask
            pixels = AWTImageTools.getBytes(img, false)
            nBytes = pixels.length / 8
            if (nBytes * 8 < pixels.length) {
                nBytes++
            }
            bits = new BitWriter(nBytes as int)
            for (byte b : pixels) {
                bits.write(b == 0 ? 0 : 1, 1)
            }

            encoded = bits.toByteArray()
            encoded = new ZlibCodec().compress(encoded, null)

            encoder = new Base64Codec()
            encoded = encoder.compress(encoded, null)

            binData = new BinData()
            binData.setLength(new NonNegativeLong(Long.valueOf(encoded.length)))
            binData.setCompression(Compression.ZLIB)
            binData.setBase64Binary(encoded)

            def mask = new Mask()
            mask.setID(shapeID)
            mask.setX(originX)
            mask.setY(originY)
            mask.setWidth(width)
            mask.setHeight(height)
            mask.setBinData(binData)

            union.addShape(mask as Shape)
            break
        default:
            print("Unsupported ROI type: " + roi)
    }

    if (union.sizeOfShapeList() > 0) {
        omeROI.setUnion(union)
    }

    // Finalize ROI
    omeROI.linkAnnotation(mapAnnotation)
    ome.addROI(omeROI)
    structuredAnnotations.addMapAnnotation(mapAnnotation)
}


nextROI = 0
rois.each { PathROIObject path ->
    def roi = path.getROI()
    addROI(roi, path, nextROI)
    nextROI++

    // PathCellObjects are the result of running a cell detection
    // the result of getROI is the cell boundary
    // the nucleus is defined as a separate ROI and should be included
    // in the OME ROI as a separate shape
    if (path.isCell() && path.hasNucleus()) {
        addROI(path.getNucleusROI(), path, nextROI)
        nextROI++
    }
}

ome.setStructuredAnnotations(structuredAnnotations);

file = Dialogs.promptToSaveFile("Choose OME-XML export location", null, null, "OME-XML", ".ome.xml")
xmlWriter = new XMLWriter();
xmlWriter.writeFile(file, ome, false);
