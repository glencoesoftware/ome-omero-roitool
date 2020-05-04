OME and OMERO ROI tool
======================

Annotation converter to and from OME-XML. This repository contains two distinct but related tools.

```ome-omero-roitool``` is a command line tool for importing or exporting ROIs associated with a
particular OMERO Image ID.  The input (for import) and output (for export) is an OME-XML file
with ROIs defined as described in https://docs.openmicroscopy.org/ome-model/6.1.0/developers/roi.html

A set of scripts for importing and exporting OME-XML ROIs within QuPath are in ```src/dist/QuPath.scripts```.


# ome-omero-roitool

## Requirements

* OMERO 5.6.x+
* Java 8+

## Workflow

### ROI import

```
$ ome-omero-roitool import --help
13:12:01.925 [main] INFO com.glencoesoftware.roitool.Main - ROI tool 0.1.0-SNAPSHOT started
Usage: <main class> import [--debug] [--help] [--key=<sessionKey>]
                           [--password=<password>] [--port=<port>]
                           [--server=<server>] [--username=<username>]
                           <imageId> <input>
Import ROIs from OME-XML file into an OMERO server
      <imageId>            OMERO Image ID to link the ROIs
      <input>              Input OME-XML file
      --debug              Set logging level to DEBUG
      --help               Display this help and exit
      --key=<sessionKey>   OMERO session key
      --password=<password>
                           OMERO password
      --port=<port>        OMERO server port
      --server=<server>    OMERO server address
      --username=<username>
                           OMERO user name
```

#### Example

```
$ ome-omero-roitool import --server localhost --username test --password test 30101 test.ome.xml
13:10:35.719 [main] INFO com.glencoesoftware.roitool.Main - ROI tool 0.1.0-SNAPSHOT started
13:10:35.926 [main] INFO ome.formats.OMEROMetadataStoreClient - Attempting initial SSL connection to localhost:4064
13:10:37.753 [main] INFO ome.formats.OMEROMetadataStoreClient - Insecure connection requested, falling back
13:10:40.728 [main] INFO com.glencoesoftware.roitool.OMEOMEROConverter - ROI import started
13:10:40.796 [main] INFO com.glencoesoftware.roitool.OMEOMEROConverter - Creating omexmlMeta
13:10:40.880 [main] INFO com.glencoesoftware.roitool.OMEOMEROConverter - Converting to OMERO metadata
13:10:40.930 [main] WARN ome.formats.OMEROMetadataStoreClient - No annotators linked. Ignoring setMapAnnotationAnnotator(null, 0(
13:10:40.935 [main] WARN ome.formats.OMEROMetadataStoreClient - No annotators linked. Ignoring setMapAnnotationAnnotator(null, 1(
13:10:40.935 [main] WARN ome.formats.OMEROMetadataStoreClient - No annotators linked. Ignoring setMapAnnotationAnnotator(null, 2(
13:10:40.935 [main] WARN ome.formats.OMEROMetadataStoreClient - No annotators linked. Ignoring setMapAnnotationAnnotator(null, 3(
13:10:40.971 [main] INFO com.glencoesoftware.roitool.OMEOMEROConverter - ROI count: 4
13:10:40.971 [main] WARN ome.formats.model.ChannelProcessor - Unexpected null reader.
13:10:40.980 [main] INFO com.glencoesoftware.roitool.ROIMetadataStoreClient - Saving to DB
13:10:40.988 [main] INFO com.glencoesoftware.roitool.ROIMetadataStoreClient - Linking ROIs to Image:253353
13:10:41.508 [main] INFO com.glencoesoftware.roitool.ROIMetadataStoreClient - Saved ROI with ID: 534875
13:10:41.508 [main] INFO com.glencoesoftware.roitool.ROIMetadataStoreClient - Saved ROI with ID: 534876
13:10:41.509 [main] INFO com.glencoesoftware.roitool.ROIMetadataStoreClient - Saved ROI with ID: 534877
13:10:41.509 [main] INFO com.glencoesoftware.roitool.ROIMetadataStoreClient - Saved ROI with ID: 534878
```

### ROI export

```
$ ome-omero-roitool export --help
13:12:01.925 [main] INFO com.glencoesoftware.roitool.Main - ROI tool 0.1.0-SNAPSHOT started
Usage: <main class> export [--help] [--key=<sessionKey>]
                           [--password=<password>] [--port=<port>]
                           [--server=<server>] [--username=<username>]
                           <imageId> <output>
Export ROIs to an OME-XML file from an OMERO server
      <imageId>            OMERO Image ID to export ROIs from
      <output>             Path to write OME-XML file to
      --help               Display this help and exit
      --key=<sessionKey>   OMERO session key
      --password=<password>
                           OMERO password
      --port=<port>        OMERO server port
      --server=<server>    OMERO server address
      --username=<username>
                           OMERO user name
```

#### Example

```
$ ome-omero-roitool import --server localhost --username test --password test 30101 test.ome.xml
11:43:46.728 [main] INFO com.glencoesoftware.roitool.Main - ROI tool 0.1.0-SNAPSHOT started
11:43:46.987 [main] INFO ome.formats.OMEROMetadataStoreClient - Attempting initial SSL connection to localhost:4064
11:43:49.566 [main] INFO ome.formats.OMEROMetadataStoreClient - Insecure connection requested, falling back
11:43:52.850 [main] INFO com.glencoesoftware.roitool.OMEOMEROConverter - ROI export started
11:43:53.240 [main] INFO com.glencoesoftware.roitool.OMEOMEROConverter - Converting to OME-XML metadata
11:43:53.286 [main] INFO com.glencoesoftware.roitool.OMEOMEROConverter - ROI count: 8
11:43:53.286 [main] INFO com.glencoesoftware.roitool.OMEOMEROConverter - Writing OME-XML to: test.ome.xml
```

## Development Installation

1. Clone the repository::

    git clone git@github.com:glencoesoftware/ome-omero-roitool.git

1. Run the Gradle build and utilize the artifacts as required::

    ./gradlew installDist
    cd build/install
    ...

## Running Tests

Using Gradle run the unit tests:

    ./gradlew test

## Eclipse Configuration

1. Run the Gradle Eclipse task::

    ./gradlew eclipse


# QuPath scripts

## Requirements

* QuPath 0.2.0-m10 or later

## Installation

Drag and drop the import or export .groovy file onto the QuPath window.
For frequent use, set the QuPath shared scripts directory (```Automate > Shared scripts... > Set script directory...```)
and then copy all *.groovy files to the chosen directory.  The scripts can then be accessed via
```Automate > Shared scripts...``` in QuPath.

## Import OME-XML ROIs

1. Open an image in QuPath.
2. Open the ```OME_XML_import.groovy``` script in QuPath and select ```Run```.
3. Choose the OME-XML file to import.
4. Wait a bit; the import is fast for a small number of ROIs, but can take minutes if thousands of ROIs are present.
5. Click the ```Annotations``` tab in QuPath to see a list of imported ROIs.

The chosen OME-XML file is expected to contain only ROIs and optionally MapAnnotations.  Other data in the file will be ignored.

## Export OME-XML ROIs

1. Open an image in QuPath.
2. Draw or import annotations, or create a cell detection.
3. Open the ```OME_XML_export.groovy``` script in QuPath and select ```Run```.
4. Choose the output OME-XML file.

Most QuPath objects have a direct counterpart in the OME-XML schema.  The exception is geometry ROIs created by the wand and brush tools.
These will be represented in OME-XML as masks covering the bounding box of the ROI (not the whole image).  Some loss of precision may occur
when exporting geometry ROIs.

## Grading workflow

The ```grading_workflow.groovy``` script shows an example of how to build upon the import script to provide additional features.
In this case, five new QuPath annotation classes are defined (```Grade 0``` to ```Grade 4```), and all other classes are optionally
removed from the list.  ROIs are imported from the chosen OME-XML file by calling ```OME_XML_import.groovy```, and can then be
easily re-classified into the new grade classes.
