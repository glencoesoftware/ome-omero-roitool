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
 * QuPath OME-XML Importer
 *
 * This script will import ROIs from an OME-XML file and generate detections and
 * annotations for the current image opened in QuPath
 *
 * Instructions:
 *   - Open the image to be annotated in QuPath
 *   - Open this file in the QuPath "Script editor"
 *   - Choose "Run" from the Run menu
 *   - When prompted, choose the OME-XML file to import
 *   - If the imported ROIs contain more than one value of stroke width, you will
 *     be prompted for which value to use for all annotations and/or detections
 */



import ij.plugin.filter.ThresholdToSelection
import ij.process.ByteProcessor
import ij.process.ImageProcessor
import java.util.Base64
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import loci.common.RandomAccessInputStream
import loci.common.services.ServiceFactory
import loci.formats.services.OMEXMLService
import ome.codecs.ZlibCodec
import ome.units.UNITS
import ome.units.quantity.Length
import ome.xml.meta.OMEXMLMetadata
import ome.xml.model.enums.Compression
import ome.xml.model.primitives.Color
import ome.xml.model.primitives.NonNegativeInteger
import qupath.imagej.tools.ROIConverterIJ
import qupath.lib.common.ColorTools
import qupath.lib.geom.Point2
import qupath.lib.gui.dialogs.Dialogs
import qupath.lib.gui.dialogs.ParameterPanelFX
import qupath.lib.gui.prefs.PathPrefs
import qupath.lib.gui.scripting.QPEx
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.objects.PathCellObject
import qupath.lib.objects.PathDetectionObject
import qupath.lib.objects.PathROIObject
import qupath.lib.objects.classes.PathClassFactory
import qupath.lib.plugins.parameters.ParameterChangeListener
import qupath.lib.plugins.parameters.ParameterList
import qupath.lib.regions.ImagePlane
import qupath.lib.roi.*

qupath = QPEx.getQuPath()
file = Dialogs.promptForFile("Choose an OME-XML to import", null, "image.ome.xml", ".ome.xml")
xml = file.readLines().join("\n")

factory = new ServiceFactory()
service = factory.getInstance(OMEXMLService.class)
omexml = service.createOMEXMLMetadata(xml)

nameIndexes = new HashMap<String, Integer>();

roiCount = omexml.getROICount()

if (roiCount < 1) {
    println("No ROIs found to import.")
    return
}

println("ROI count: " + omexml.getROICount())
newPathObjects = []
thinLineStrokeWidths = new HashSet<>()
thickLineStrokeWidths = new HashSet<>()
pathClasses = new HashSet<>()

// unpack a NonNegativeInteger from OME-XML, using a default value of 0 if null
int getValue(NonNegativeInteger v) {
    return v == null ? 0 : v.numberValue.intValue()
}

void setPathClassAndStroke(PathROIObject path, String className, Color color, Length strokeWidth) {
    def qpColor = null
    if (color != null) {
        qpColor = ColorTools.makeRGBA(color.red, color.green, color.blue, color.alpha)
    }

    if (strokeWidth != null) {
        strokeWidthValue = strokeWidth.value(UNITS.PIXEL)
        switch (path) {
            case PathDetectionObject:
                thinLineStrokeWidths.add(strokeWidthValue)
                break
            case PathAnnotationObject:
                thickLineStrokeWidths.add(strokeWidthValue)
                break
        }
    }

    if (className == null) {
        // No class to set, so just set the color directly
        if (qpColor != null) {
            path.setColorRGB(qpColor)
        }
    } else {
        // set the class on the object
        def qpClass = PathClassFactory.getPathClass(className, qpColor)
        path.setPathClass(qpClass)

        // update list of unique classes so that the UI can be updated later
        pathClasses.add(qpClass);
    }
}

// convert the OME-XML shape with the given indexes to a QuPath ROI
qupath.lib.roi.interfaces.ROI importShape(PathROIObject path, int roiIdx, int shapeIdx, String className) {
    def shapeType = omexml.getShapeType(roiIdx, shapeIdx)
    println(String.format("ROI %d:%d has type '%s'", roiIdx, shapeIdx, shapeType))

    def locked = null
    def roi = null

    switch (shapeType) {
        case "Ellipse":
            def color = omexml.getEllipseStrokeColor(roiIdx, shapeIdx)
            def strokeWidth = omexml.getEllipseStrokeWidth(roiIdx, shapeIdx)
            setPathClassAndStroke(path, className, color, strokeWidth)

            locked = omexml.getEllipseLocked(roiIdx, shapeIdx)

            def c = getValue(omexml.getEllipseTheC(roiIdx, shapeIdx))
            def z = getValue(omexml.getEllipseTheZ(roiIdx, shapeIdx))
            def t = getValue(omexml.getEllipseTheT(roiIdx, shapeIdx))
            def plane = new ImagePlane(c, z, t)
            def centroidX = omexml.getEllipseX(roiIdx, shapeIdx)
            def centroidY = omexml.getEllipseY(roiIdx, shapeIdx)
            def radiusX = omexml.getEllipseRadiusX(roiIdx, shapeIdx)
            def radiusY = omexml.getEllipseRadiusY(roiIdx, shapeIdx)
            def x = centroidX - radiusX
            def y = centroidY - radiusY
            def width = radiusX * 2
            def height = radiusY * 2
            roi = new EllipseROI(x, y, width, height, plane)

            break
        case "Line":
            def color = omexml.getLineStrokeColor(roiIdx, shapeIdx)
            def strokeWidth = omexml.getLineStrokeWidth(roiIdx, shapeIdx)
            setPathClassAndStroke(path, className, color, strokeWidth)

            locked = omexml.getLineLocked(roiIdx, shapeIdx)

            def c = getValue(omexml.getLineTheC(roiIdx, shapeIdx))
            def z = getValue(omexml.getLineTheZ(roiIdx, shapeIdx))
            def t = getValue(omexml.getLineTheT(roiIdx, shapeIdx))
            def plane = new ImagePlane(c, z, t)
            def x = omexml.getLineX1(roiIdx, shapeIdx)
            def y = omexml.getLineY1(roiIdx, shapeIdx)
            def x2 = omexml.getLineX2(roiIdx, shapeIdx)
            def y2 = omexml.getLineY2(roiIdx, shapeIdx)
            roi = new LineROI(x, y, x2, y2, plane)

            break
        case "Point":
            def color = omexml.getPointStrokeColor(roiIdx, shapeIdx)
            def strokeWidth = omexml.getPointStrokeWidth(roiIdx, shapeIdx)
            setPathClassAndStroke(path, className, color, strokeWidth)

            locked = omexml.getPointLocked(roiIdx, shapeIdx)

            def c = getValue(omexml.getPointTheC(roiIdx, shapeIdx))
            def z = getValue(omexml.getPointTheZ(roiIdx, shapeIdx))
            def t = getValue(omexml.getPointTheT(roiIdx, shapeIdx))
            def plane = new ImagePlane(c, z, t)
            def x = omexml.getPointX(roiIdx, shapeIdx)
            def y = omexml.getPointY(roiIdx, shapeIdx)
            roi = new PointsROI(x, y, plane)

            break
        case "Polygon":
            def color = omexml.getPolygonStrokeColor(roiIdx, shapeIdx)
            def strokeWidth = omexml.getPolygonStrokeWidth(roiIdx, shapeIdx)
            setPathClassAndStroke(path, className, color, strokeWidth)

            locked = omexml.getPolygonLocked(roiIdx, shapeIdx)

            def c = getValue(omexml.getPolygonTheC(roiIdx, shapeIdx))
            def z = getValue(omexml.getPolygonTheZ(roiIdx, shapeIdx))
            def t = getValue(omexml.getPolygonTheT(roiIdx, shapeIdx))
            def plane = new ImagePlane(c, z, t)
            def pointsString = omexml.getPolygonPoints(roiIdx, shapeIdx)
            def points = pointsString.split(/ /).collect { point ->
                def (x, y) = point.split(/,/)
                new Point2(x.toDouble(), y.toDouble())
            }
            roi = new PolygonROI(points, plane)

            break
        case "Polyline":
            def color = omexml.getPolylineStrokeColor(roiIdx, shapeIdx)
            def strokeWidth = omexml.getPolylineStrokeWidth(roiIdx, shapeIdx)
            setPathClassAndStroke(path, className, color, strokeWidth)

            locked = omexml.getPolylineLocked(roiIdx, shapeIdx)

            def c = getValue(omexml.getPolylineTheC(roiIdx, shapeIdx))
            def z = getValue(omexml.getPolylineTheZ(roiIdx, shapeIdx))
            def t = getValue(omexml.getPolylineTheT(roiIdx, shapeIdx))
            def plane = new ImagePlane(c, z, t)
            def pointsString = omexml.getPolylinePoints(roiIdx, shapeIdx)
            def points = pointsString.split(/ /).collect { point ->
                def (x, y) = point.split(/,/)
                new Point2(x.toDouble(), y.toDouble())
            }
            roi = new PolylineROI(points, plane)

            break
        case "Rectangle":
            def color = omexml.getRectangleStrokeColor(roiIdx, shapeIdx)
            def strokeWidth = omexml.getRectangleStrokeWidth(roiIdx, shapeIdx)
            setPathClassAndStroke(path, className, color, strokeWidth)

            locked = omexml.getRectangleLocked(roiIdx, shapeIdx)

            def c = getValue(omexml.getRectangleTheC(roiIdx, shapeIdx))
            def z = getValue(omexml.getRectangleTheZ(roiIdx, shapeIdx))
            def t = getValue(omexml.getRectangleTheT(roiIdx, shapeIdx))
            def plane = new ImagePlane(c, z, t)
            def x = omexml.getRectangleX(roiIdx, shapeIdx)
            def y = omexml.getRectangleY(roiIdx, shapeIdx)
            def width = omexml.getRectangleWidth(roiIdx, shapeIdx)
            def height = omexml.getRectangleHeight(roiIdx, shapeIdx)
            roi = new RectangleROI(x, y, width, height, plane)

            break
        case "Mask":
            def x = omexml.getMaskX(roiIdx, shapeIdx)
            def y = omexml.getMaskY(roiIdx, shapeIdx)
            def width = omexml.getMaskWidth(roiIdx, shapeIdx).intValue()
            def height = omexml.getMaskHeight(roiIdx, shapeIdx).intValue()
            def binData = omexml.getMaskBinData(roiIdx, shapeIdx)
            def compression = omexml.getMaskBinDataCompression(roiIdx, shapeIdx)

            def bits = Base64.getDecoder().decode(binData)
            if (compression == Compression.ZLIB) {
                bits = new ZlibCodec().decompress(bits, null)
            }
            def stream = new RandomAccessInputStream(bits)
            def bytes = new byte[width * height]
            (0..(bytes.length - 1)).each { bitIndex ->
                bytes[bitIndex] = stream.readBits(1) * Byte.MAX_VALUE;
            }

            // see https://petebankhead.github.io/qupath/scripting/2018/03/13/script-export-import-binary-masks.html
            def bp = new ByteProcessor(width, height, bytes)
            bp.setThreshold(Byte.MAX_VALUE - 1, 255, ImageProcessor.NO_LUT_UPDATE)
            def ijROI = new ThresholdToSelection().convert(bp)

            if (ijROI != null) {
                def c = getValue(omexml.getMaskTheC(roiIdx, shapeIdx))
                def z = getValue(omexml.getMaskTheZ(roiIdx, shapeIdx))
                def t = getValue(omexml.getMaskTheT(roiIdx, shapeIdx))

                roi = ROIConverterIJ.convertToAreaROI(ijROI, -1 * x, -1 * y, 1, c, z, t)
            }

            break
        default:
            throw new Exception(String.format("ROI %d:%d is of unknown type: %s", roiIdx, shapeIdx, shapeType))
    }

    if (locked != null) {
        path.setLocked(locked)
    }

    return roi
}

roiIdx = 0
while (roiIdx < roiCount) {
    def mapAnnotations = [:]
    def annotationCount = omexml.getROIAnnotationRefCount(roiIdx)
    if (annotationCount > 0) {
        println("Found " + annotationCount + " annotations for ROI: " + roiIdx)
        (0..(annotationCount - 1)).each { annRefIdx ->
            def (annType, annIdx) = omexml.getROIAnnotationRef(roiIdx, annRefIdx).split(/-/)
            switch (annType) {
            // Only import Map Annotations for now
                case "MapAnnotation":
                    def mapPairs = omexml.getMapAnnotationValue(annIdx.toInteger())
                    mapPairs.each {
                        mapAnnotations[it.name] = it.value
                    }
                    break
                default:
                    println("OME-XML import does not handle annotations of type \"" + annType + "\" at this time.")
            }
        }
    }

    println("Shape count: " + omexml.getShapeCount(roiIdx))
    effectiveCount = omexml.getShapeCount(roiIdx)

    (0..(effectiveCount - 1)).each { shapeIdx ->

        PathROIObject path
        if (mapAnnotations["qupath:is-detection"] == "true") {
            if (mapAnnotations["qupath:name"] == "cell boundary") {
                path = new PathCellObject()
            }
            else {
                path = new PathDetectionObject()
            }
        } else {
            // Treat all externally created ROIs as Annotations from QuPath's perspective
            path = new PathAnnotationObject()
        }
        def className = mapAnnotations["qupath:class"]
        if (className == null) {
            className = mapAnnotations["class"]
            if (className == null) {
                // If there is no explicit class name, but the ROI has a name, use that as class name
                className = omexml.getROIName(roiIdx)
            }
        }

        roi = importShape(path, roiIdx, shapeIdx, className)

        if (roi != null) {
            if (mapAnnotations["qupath:name"] != null) {
                path.setName(mapAnnotations["qupath:name"])
            } else {
                def roiName = omexml.getROIName(roiIdx);
                if (roiName != null) {
                    path.setName(String.format("%s #%d", roiName, getIndex(roiName)));
                }
            }
            path.setROI(roi)
            mapAnnotations.keySet().each {
                if (it.toString().startsWith("qupath:metadata:")) {
                    path.storeMetadataValue(it.toString().replaceFirst(/qupath:metadata:/, ""),
                            mapAnnotations[it].toString())
                }
            }

            // store the second shape as the cell nucleus
            if (path.isCell() && effectiveCount == 1) {
                roiIdx++
                nucleusROI = importShape(path, roiIdx, shapeIdx, className)

                // no setter for the nucleus ROI
                path = new PathCellObject(roi, nucleusROI, path.getPathClass())
            }

            newPathObjects.add(path)
        }
    }
    roiIdx++
}
QPEx.getCurrentHierarchy().addPathObjects(newPathObjects)
updatePathClasses()

// make sure each object class is added to the list in the GUI
void updatePathClasses() {
    if (!Platform.isFxApplicationThread()) {
        Platform.runLater({ updatePathClasses() })
        return
    }

    def classList = qupath.getAvailablePathClasses()
    pathClasses.each { qpClass ->
        if (!classList.contains(qpClass)) {
            classList.add(qpClass)
        }
    }
}

Integer getIndex(String roiName) {
    def index = nameIndexes.get(roiName);
    if (index != null) {
        nameIndexes.put(roiName, index + 1);
        return index;
    }
    nameIndexes.put(roiName, 2);
    return 1;
}

void chooseLineWidths() {

    new JFXPanel()
    if (!Platform.isFxApplicationThread()) {
        Platform.runLater({ chooseLineWidths() })
        return
    }

    def frame = new Stage()
    frame.setTitle("Line Thickness")
    def lineParams = new ParameterList().addTitleParameter("Choose Line Thickness")
            .addEmptyParameter("More than one value for line thickness was imported.")
            .addEmptyParameter("Please choose which value you'd like to use:")
    if (thickLineStrokeWidths.size() > 1) {
        def widths = thickLineStrokeWidths.toArray().sort()
        lineParams.addChoiceParameter("annotation", "Annotation Thickness", widths[0] as Number,
                widths as ArrayList<Number>, "Numeric choice")
    }
    if (thinLineStrokeWidths.size() > 1) {
        def widths = thinLineStrokeWidths.toArray().sort()
        lineParams.addChoiceParameter("detection", "Detection Thickness", widths[0] as Number,
                widths as ArrayList<Number>, "Numeric choice")
    }

    def borderPane = new BorderPane()
    def panel = new ParameterPanelFX(lineParams)

    panel.addParameterChangeListener(new ParameterChangeListener() {
        @Override
        void parameterChanged(ParameterList params, String key, boolean isAdjusting) {
            def param = params.getParameters().get(key)
            if (key == "annotation") {
                PathPrefs.setThickStrokeThickness(param.getValue() as float)
            }
            if (key == "detection") {
                PathPrefs.setThinStrokeThickness(param.getValue() as float)
            }
        }
    })

    def button = new Button("OK")
    button.setDefaultButton(true)
    button.setOnAction({ frame.close() })
    borderPane.setCenter(panel.getPane())
    borderPane.setBottom(button)
    borderPane.setPadding(new Insets(10, 10, 10, 10))

    frame.setScene(new Scene(borderPane))
    frame.show()
}

if (thickLineStrokeWidths.size() > 1 || thinLineStrokeWidths.size() > 1) {
    chooseLineWidths()
}
