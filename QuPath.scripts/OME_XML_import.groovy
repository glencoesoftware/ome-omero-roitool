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



import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import loci.common.services.ServiceFactory
import loci.formats.services.OMEXMLService
import ome.units.UNITS
import ome.xml.meta.OMEXMLMetadata
import qupath.lib.common.ColorTools
import qupath.lib.geom.Point2
import qupath.lib.gui.helpers.dialogs.ParameterPanelFX
import qupath.lib.gui.prefs.PathPrefs
import qupath.lib.gui.scripting.QPEx
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.objects.PathDetectionObject
import qupath.lib.objects.PathROIObject
import qupath.lib.plugins.parameters.Parameter
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

println("ROI count:" + omexml.getROICount())
newPathObjects = []
thinLineStrokeWidths = new HashSet<>()
thickLineStrokeWidths = new HashSet<>()

(0..(omexml.getROICount() - 1)).each { roiIdx ->

    def mapAnnotations = [:]
    (0..(omexml.getROIAnnotationRefCount(roiIdx) - 1)).each { annRefIdx ->
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
                if (color != null) {
                    path.setColorRGB(ColorTools.makeRGBA(color.red, color.green, color.blue, color.alpha))
                }

                switch (path) {
                    case PathDetectionObject:
                        thinLineStrokeWidths.add(omexml.getEllipseStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL))
                        break
                    case PathAnnotationObject:
                        thickLineStrokeWidths.add(omexml.getEllipseStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL))
                        break
                }

                def c = omexml.getEllipseTheC(roiIdx, shapeIdx)
                c = c != null ? c.numberValue.intValue() : 0
                def z = omexml.getEllipseTheZ(roiIdx, shapeIdx)
                z = z != null ? z.numberValue.intValue() : 0
                def t = omexml.getEllipseTheT(roiIdx, shapeIdx)
                t = t != null ? t.numberValue.intValue() : 0
                def plane = new ImagePlane(c, z, t)
                def x = omexml.getEllipseX(roiIdx, shapeIdx)
                def y = omexml.getEllipseY(roiIdx, shapeIdx)
                def width = omexml.getEllipseRadiusX(roiIdx, shapeIdx)
                def height = omexml.getEllipseRadiusY(roiIdx, shapeIdx)
                roi = new EllipseROI(x, y, width, height, plane)
                
                break
            case "Line":
                println(String.format("ROI %d:%d is a Line", roiIdx, shapeIdx))

                def color = omexml.getLineStrokeColor(roiIdx, shapeIdx)
                if (color != null) {
                    path.setColorRGB(ColorTools.makeRGBA(color.red, color.green, color.blue, color.alpha))
                }

                switch (path) {
                    case PathDetectionObject:
                        thinLineStrokeWidths.add(omexml.getLineStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL))
                        break
                    case PathAnnotationObject:
                        thickLineStrokeWidths.add(omexml.getLineStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL))
                        break
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
                if (color != null) {
                    path.setColorRGB(ColorTools.makeRGBA(color.red, color.green, color.blue, color.alpha))
                }

                switch (path) {
                    case PathDetectionObject:
                        thinLineStrokeWidths.add(omexml.getPointStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL))
                        break
                    case PathAnnotationObject:
                        thickLineStrokeWidths.add(omexml.getPointStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL))
                        break
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
                if (color != null) {
                    path.setColorRGB(ColorTools.makeRGBA(color.red, color.green, color.blue, color.alpha))
                }

                switch (path) {
                    case PathDetectionObject:
                        thinLineStrokeWidths.add(omexml.getPolygonStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL))
                        break
                    case PathAnnotationObject:
                        thickLineStrokeWidths.add(omexml.getPolygonStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL))
                        break
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
                if (color != null) {
                    path.setColorRGB(ColorTools.makeRGBA(color.red, color.green, color.blue, color.alpha))
                }

                switch (path) {
                    case PathDetectionObject:
                        thinLineStrokeWidths.add(omexml.getPolylineStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL))
                        break
                    case PathAnnotationObject:
                        thickLineStrokeWidths.add(omexml.getPolylineStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL))
                        break
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
                if (color != null) {
                    path.setColorRGB(ColorTools.makeRGBA(color.red, color.green, color.blue, color.alpha))
                }

                switch (path) {
                    case PathDetectionObject:
                        thinLineStrokeWidths.add(omexml.getRectangleStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL))
                        break
                    case PathAnnotationObject:
                        thickLineStrokeWidths.add(omexml.getRectangleStrokeWidth(roiIdx, shapeIdx).value(UNITS.PIXEL))
                        break
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
            default:
                throw new Exception(String.format("ROI %d:%d is of unknown type: %s", roiIdx, shapeIdx, shapeType))
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
QPEx.getCurrentHierarchy().addPathObjects(newPathObjects)

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

    borderPane.setCenter(panel.getPane())
    borderPane.setPadding(new Insets(10, 10, 10, 10))

    frame.setScene(new Scene(borderPane))
    frame.show()
}

chooseLineWidths()
