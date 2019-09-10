OME and OMERO ROI tool
======================

Annotation converter to and from OME-XML

Requirements
============

* OMERO 5.4.x+
* Java 8+

Workflow
========

ROI import
----------

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

Example
-------

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

Development Installation
========================

1. Clone the repository::

    git clone git@github.com:glencoesoftware/ome-omero-roitool.git

1. Run the Gradle build and utilize the artifacts as required::

    ./gradlew installDist
    cd build/install
    ...

Running Tests
=============

Using Gradle run the unit tests:

    ./gradlew test

Eclipse Configuration
=====================

1. Run the Gradle Eclipse task::

    ./gradlew eclipse

