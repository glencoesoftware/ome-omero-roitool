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
import ome.units.UNITS
import ome.xml.meta.OMEXMLMetadata
import ome.xml.model.primitives.Color
import qupath.imagej.tools.ROIConverterIJ
import qupath.lib.common.ColorTools
import qupath.lib.geom.Point2
import qupath.lib.gui.dialogs.ParameterPanelFX
import qupath.lib.gui.prefs.PathPrefs
import qupath.lib.gui.scripting.QPEx
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.objects.PathDetectionObject
import qupath.lib.objects.PathROIObject
import qupath.lib.objects.classes.PathClassFactory
import qupath.lib.plugins.parameters.ParameterChangeListener
import qupath.lib.plugins.parameters.ParameterList
import qupath.lib.regions.ImagePlane
import qupath.lib.roi.*

qupath = QPEx.getQuPath()
file = qupath.getDialogHelper().promptForFile("Choose an OME-XML to import", null, "image.ome.xml", ".ome.xml")
xml = file.readLines().join("\n")

factory = new ServiceFactory()
service = factory.getInstance(OMEXMLService.class)
OMEXMLMetadata omexml = service.createOMEXMLMetadata(xml)

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

void setPathClassAndStroke(PathROIObject path, String className, Color color, Number strokeWidth) {
    def qpColor = null
    if (color != null) {
        qpColor = ColorTools.makeRGBA(color.red, color.green, color.blue, color.alpha)
    }

    if (strokeWidth != null) {
        switch (path) {
            case PathDetectionObject:
                thinLineStrokeWidths.add(strokeWidth)
                break
            case PathAnnotationObject:
                thickLineStrokeWidths.add(strokeWidth)
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

(0..(roiCount - 1)).each { roiIdx ->

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
    (0..(omexml.getShapeCount(roiIdx) - 1)).each { shapeIdx ->

        PathROIObject path
        if (mapAnnotations["qupath:is-detection"] == "true") {
            path = new PathDetectionObject()
        } else {
            // Treat all externally created ROIs as Annotations from QuPath's perspective
            path = new PathAnnotationObject()
        }
        def roi

        def shapeType = omexml.getShapeType(roiIdx, shapeIdx)
        switch (shapeType) {
            case "Ellipse":
                println(String.format("ROI %d:%d is an Elipse", roiIdx, shapeIdx))

                def color = omexml.getEllipseStrokeColor(roiIdx, shapeIdx)
                def strokeWidth = null
                if (omexml.getEllipseStrokeWidth(roiIdx, shapeIdx) != null) {
                    strokeWidth = omexml.getEllipseStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL)
                }
                def className = mapAnnotations["qupath:class"]
                if (className == null) {
                    className = mapAnnotations["class"]
                }
                if (className == null) {
                    // If there is no explicit class name, but the ROI has a name, use that as class name
                    className = omexml.getROIName(roiIdx)
                }
                setPathClassAndStroke(path, className, color, strokeWidth)

                if (omexml.getEllipseLocked(roiIdx, shapeIdx) != null) {
                    path.setLocked(omexml.getEllipseLocked(roiIdx, shapeIdx))
                }

                def c = omexml.getEllipseTheC(roiIdx, shapeIdx)
                c = c != null ? c.numberValue.intValue() : 0
                def z = omexml.getEllipseTheZ(roiIdx, shapeIdx)
                z = z != null ? z.numberValue.intValue() : 0
                def t = omexml.getEllipseTheT(roiIdx, shapeIdx)
                t = t != null ? t.numberValue.intValue() : 0
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
                println(String.format("ROI %d:%d is a Line", roiIdx, shapeIdx))

                def color = omexml.getLineStrokeColor(roiIdx, shapeIdx)
                def strokeWidth = null
                if (omexml.getLineStrokeWidth(roiIdx, shapeIdx) != null) {
                    strokeWidth = omexml.getLineStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL)
                }
                def className = mapAnnotations["qupath:class"]
                if (className == null) {
                    className = mapAnnotations["class"]
                }
                if (className == null) {
                    // If there is no explicit class name, but the ROI has a name, use that as class name
                    className = omexml.getROIName(roiIdx)
                }
                setPathClassAndStroke(path, className, color, strokeWidth)

                if (omexml.getLineLocked(roiIdx, shapeIdx) != null) {
                    path.setLocked(omexml.getLineLocked(roiIdx, shapeIdx))
                }

                def c = omexml.getLineTheC(roiIdx, shapeIdx)
                c = c != null ? c.numberValue.intValue() : 0
                def z = omexml.getLineTheZ(roiIdx, shapeIdx)
                z = z != null ? z.numberValue.intValue() : 0
                def t = omexml.getLineTheT(roiIdx, shapeIdx)
                t = t != null ? t.numberValue.intValue() : 0
                def plane = new ImagePlane(c, z, t)
                def x = omexml.getLineX1(roiIdx, shapeIdx)
                def y = omexml.getLineY1(roiIdx, shapeIdx)
                def x2 = omexml.getLineX2(roiIdx, shapeIdx)
                def y2 = omexml.getLineY2(roiIdx, shapeIdx)
                roi = new LineROI(x, y, x2, y2, plane)
                
                break
            case "Point":
                println(String.format("ROI %d:%d is a Point", roiIdx, shapeIdx))
                
                def color = omexml.getPointStrokeColor(roiIdx, shapeIdx)
                def strokeWidth = null
                if (omexml.getPointStrokeWidth(roiIdx, shapeIdx) != null) {
                    strokeWidth = omexml.getPointStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL)
                }
                def className = mapAnnotations["qupath:class"]
                if (className == null) {
                    className = mapAnnotations["class"]
                }
                if (className == null) {
                    // If there is no explicit class name, but the ROI has a name, use that as class name
                    className = omexml.getROIName(roiIdx)
                }
                setPathClassAndStroke(path, className, color, strokeWidth)

                if (omexml.getPointLocked(roiIdx, shapeIdx) != null) {
                    path.setLocked(omexml.getPointLocked(roiIdx, shapeIdx))
                }

                def c = omexml.getPointTheC(roiIdx, shapeIdx)
                c = c != null ? c.numberValue.intValue() : 0
                def z = omexml.getPointTheZ(roiIdx, shapeIdx)
                z = z != null ? z.numberValue.intValue() : 0
                def t = omexml.getPointTheT(roiIdx, shapeIdx)
                t = t != null ? t.numberValue.intValue() : 0
                def plane = new ImagePlane(c, z, t)
                def x = omexml.getPointX(roiIdx, shapeIdx)
                def y = omexml.getPointY(roiIdx, shapeIdx)
                roi = new PointsROI(x, y, plane)
                
                break
            case "Polygon":
                println(String.format("ROI %d:%d is a Polygon", roiIdx, shapeIdx))

                def color = omexml.getPolygonStrokeColor(roiIdx, shapeIdx)
                def strokeWidth = null
                if (omexml.getPolygonStrokeWidth(roiIdx, shapeIdx) != null) {
                    strokeWidth = omexml.getPolygonStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL)
                }
                def className = mapAnnotations["qupath:class"]
                if (className == null) {
                    className = mapAnnotations["class"]
                }
                if (className == null) {
                    // If there is no explicit class name, but the ROI has a name, use that as class name
                    className = omexml.getROIName(roiIdx)
                }
                setPathClassAndStroke(path, className, color, strokeWidth)

                if (omexml.getPolygonLocked(roiIdx, shapeIdx) != null) {
                    path.setLocked(omexml.getPolygonLocked(roiIdx, shapeIdx))
                }

                def c = omexml.getPolygonTheC(roiIdx, shapeIdx)
                c = c != null ? c.numberValue.intValue() : 0
                def z = omexml.getPolygonTheZ(roiIdx, shapeIdx)
                z = z != null ? z.numberValue.intValue() : 0
                def t = omexml.getPolygonTheT(roiIdx, shapeIdx)
                t = t != null ? t.numberValue.intValue() : 0
                def plane = new ImagePlane(c, z, t)
                def pointsString = omexml.getPolygonPoints(roiIdx, shapeIdx)
                def points = pointsString.split(/ /).collect { point ->
                    def (x, y) = point.split(/,/)
                    new Point2(x.toDouble(), y.toDouble())
                }
                roi = new PolygonROI(points, plane)
                
                break
            case "Polyline":
                println(String.format("ROI %d:%d is a Polyline", roiIdx, shapeIdx))

                def color = omexml.getPolylineStrokeColor(roiIdx, shapeIdx)
                def strokeWidth = null
                if (omexml.getPolylineStrokeWidth(roiIdx, shapeIdx) != null) {
                    strokeWidth = omexml.getPolylineStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL)
                }
                def className = mapAnnotations["qupath:class"]
                if (className == null) {
                    className = mapAnnotations["class"]
                }
                if (className == null) {
                    // If there is no explicit class name, but the ROI has a name, use that as class name
                    className = omexml.getROIName(roiIdx)
                }
                setPathClassAndStroke(path, className, color, strokeWidth)

                if (omexml.getPolylineLocked(roiIdx, shapeIdx) != null) {
                    path.setLocked(omexml.getPolylineLocked(roiIdx, shapeIdx))
                }

                def c = omexml.getPolylineTheC(roiIdx, shapeIdx)
                c = c != null ? c.numberValue.intValue() : 0
                def z = omexml.getPolylineTheZ(roiIdx, shapeIdx)
                z = z != null ? z.numberValue.intValue() : 0
                def t = omexml.getPolylineTheT(roiIdx, shapeIdx)
                t = t != null ? t.numberValue.intValue() : 0
                def plane = new ImagePlane(c, z, t)
                def pointsString = omexml.getPolylinePoints(roiIdx, shapeIdx)
                def points = pointsString.split(/ /).collect { point ->
                    def (x, y) = point.split(/,/)
                    new Point2(x.toDouble(), y.toDouble())
                }
                roi = new PolylineROI(points, plane)
                
                break
            case "Rectangle":
                println(String.format("ROI %d:%d is a Rectangle", roiIdx, shapeIdx))

                def color = omexml.getRectangleStrokeColor(roiIdx, shapeIdx)
                def strokeWidth = null
                if (omexml.getRectangleStrokeWidth(roiIdx, shapeIdx) != null) {
                    strokeWidth = omexml.getRectangleStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL)
                }
                def className = mapAnnotations["qupath:class"]
                if (className == null) {
                    className = mapAnnotations["class"]
                }
                if (className == null) {
                    // If there is no explicit class name, but the ROI has a name, use that as class name
                    className = omexml.getROIName(roiIdx)
                }
                setPathClassAndStroke(path, className, color, strokeWidth)

                if (omexml.getRectangleLocked(roiIdx, shapeIdx) != null) {
                    path.setLocked(omexml.getRectangleLocked(roiIdx, shapeIdx))
                }

                def c = omexml.getRectangleTheC(roiIdx, shapeIdx)
                c = c != null ? c.numberValue.intValue() : 0
                def z = omexml.getRectangleTheZ(roiIdx, shapeIdx)
                z = z != null ? z.numberValue.intValue() : 0
                def t = omexml.getRectangleTheT(roiIdx, shapeIdx)
                t = t != null ? t.numberValue.intValue() : 0
                def plane = new ImagePlane(c, z, t)
                def x = omexml.getRectangleX(roiIdx, shapeIdx)
                def y = omexml.getRectangleY(roiIdx, shapeIdx)
                def width = omexml.getRectangleWidth(roiIdx, shapeIdx)
                def height = omexml.getRectangleHeight(roiIdx, shapeIdx)
                roi = new RectangleROI(x, y, width, height, plane)

                break
            case "Mask":
                println(String.format("ROI %d:%d is a Mask", roiIdx, shapeIdx))

                def x = omexml.getMaskX(roiIdx, shapeIdx)
                def y = omexml.getMaskY(roiIdx, shapeIdx)
                def width = omexml.getMaskWidth(roiIdx, shapeIdx).intValue()
                def height = omexml.getMaskHeight(roiIdx, shapeIdx).intValue()
                def binData = omexml.getMaskBinData(roiIdx, shapeIdx)

                def bits = Base64.getDecoder().decode(binData)
                def stream = new RandomAccessInputStream(bits)
                def bytes = new byte[stream.length() * 8]
                (0..(bytes.length - 1)).each { bitIndex ->
                    bytes[bitIndex] = stream.readBits(1) * Byte.MAX_VALUE;
                }

                // see https://petebankhead.github.io/qupath/scripting/2018/03/13/script-export-import-binary-masks.html
                def bp = new ByteProcessor(width, height, bytes)
                bp.setThreshold(Byte.MAX_VALUE - 1, 255, ImageProcessor.NO_LUT_UPDATE)
                def ijROI = new ThresholdToSelection().convert(bp)

                if (ijROI != null) {
                    def c = omexml.getMaskTheC(roiIdx, shapeIdx)
                    c = c != null ? c.numberValue.intValue() : 0
                    def z = omexml.getMaskTheZ(roiIdx, shapeIdx)
                    z = z != null ? z.numberValue.intValue() : 0
                    def t = omexml.getMaskTheT(roiIdx, shapeIdx)
                    t = t != null ? t.numberValue.intValue() : 0

                    roi = ROIConverterIJ.convertToAreaROI(ijROI, x, y, 1, c, z, t);
                }

                break
            default:
                throw new Exception(String.format("ROI %d:%d is of unknown type: %s", roiIdx, shapeIdx, shapeType))
        }
        if (roi != null) {
            if (mapAnnotations["qupath:name"] != null) {
                path.setName(mapAnnotations["qupath:name"])
            } else if (omexml.getROIName(roiIdx) != null) {
                path.setName(omexml.getROIName(roiIdx))
            }
            path.setROI(roi)
            mapAnnotations.keySet().each {
                if (it.toString().startsWith("qupath:metadata:")) {
                    path.storeMetadataValue(it.toString().replaceFirst(/qupath:metadata:/, ""),
                            mapAnnotations[it].toString())
                }
            }
            newPathObjects.add(path)
        }
    }
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
        classList.add(qpClass)
    }
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
