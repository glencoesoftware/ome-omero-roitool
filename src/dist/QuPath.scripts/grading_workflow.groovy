/**
 * -----------------------------------------------------------------------------
 *   Copyright (C) 2020 Glencoe Software, Inc. All rights reserved.
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
 * This script will import ROIs from an OME-XML file and
 * set up some custom annotation classes
 *
 * Instructions:
 *   - Edit the grade classes if needed (look for "EDIT HERE")
 *   - Open the image to be annotated in QuPath
 *   - Open this file in the QuPath "Script editor"
 *   - Choose "Run" from the Run menu
 *   - Click "Yes" to clear the annotation class list
 *   - When prompted, choose the OME-XML import script
 *   - Choose the OME-XML ROI file
 */

import javafx.application.Platform
import qupath.lib.gui.prefs.PathPrefs
import qupath.lib.objects.classes.PathClassFactory

gradingWorkflow()

void gradingWorkflow() {
    if (!Platform.isFxApplicationThread()) {
        Platform.runLater({ gradingWorkflow() })
        return
    }

    qupath = QPEx.getQuPath()

    // prompt if annotation classes should be removed

    def clearList = Dialogs.showYesNoDialog("Clear annotation list", "Clear annotation class list?")

    if (clearList) {
        def classList = qupath.getAvailablePathClasses()
        classList.clear()
    }

    // perform OME-XML import
    // automatically load the script if it is installed in the shared scripts directory
    // otherwise, prompt for the script location

    def pathProperty = PathPrefs.scriptsPathProperty()
    def scriptName = "OME_XML_import.groovy"
    if (pathProperty != null && pathProperty.getValue() != null) {
        importScript = new File(pathProperty.getValue(), scriptName)
    }
    else {
        importScript = qupath.getDialogHelper().promptForFile("Choose the OME_XML_import script", null, scriptName, ".groovy")
    }
    evaluate(importScript)

    // add empty classes for grading

    def classList = qupath.getAvailablePathClasses()
    // EDIT HERE: grade classes are specified by the "grades" array
    def grades = [
        "Grade 0",
        "Grade 1",
        "Grade 2",
        "Grade 3",
        "Grade 4"
    ]
    grades.each { grade ->
        def qpClass = PathClassFactory.getPathClass(grade)
        classList.add(qpClass)
    }
}
