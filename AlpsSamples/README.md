# ALPS Samples
Library samples module. Includes code that might be helpful to integrate AlpsCore with ExoPlayer 
based playback applications. If implemented helper classes don't fit to the app, it still can be 
treated as an example code, showing how to use AlpsCore.

## Table of Contents
- [Installation](#installation)
- [Usage](#usage)
- [Logging](#logging)
- [External Signaling](#external-signaling)
- [Known issues](#known-issues)

## Installation
ALPS library is not published on Maven Central or other central repository. So too use it, there are
2 options:
- copy code of this module and build it together with application
- download prebuilt version of AlpsSamples module (.aar file) from [releases](releases) and link it 
to your application

## Usage
This module consists of couple classes/layers that fit into Media3 architecture. Using all layers
together is the most straight forward approach but if it doesn't fit to your app, it's fine to use
just some of them. Going from the most outer layer:
* [AlpsMediaSourceFactory](src/main/java/com/dolby/android/alps/samples/AlpsMediaSourceFactory.kt) -
Custom MediaSource.Factory that allows using custom DashMediaSource.Factory for DASH content 
and DefaultMediaSourceFactory for other content. Allows using AlpsDashChunkSourceFactory.
* [AlpsDashChunkSourceFactory](src/main/java/com/dolby/android/alps/samples/AlpsDashChunkSourceFactory.kt) -
Custom DashChunkSource.Factory class that allows applying ALPS for proper DASH chunks (AC-4).
* [AlpsHttpDataSource](src/main/java/com/dolby/android/alps/samples/AlpsHttpDataSource.kt) - Custom 
implementation of BaseDataSource and implements HttpDataSource interface. It adds ALPS library 
processing on top of DefaultHttpDataSource (or other implementation of HttpDataSource interface) 
operations. For better separation of concerns, actual ALPS related processing is done in 
AlpsProcessing.
* [AlpsProcessing](src/main/java/com/dolby/android/alps/samples/AlpsProcessing.kt) - Opens http data source, downloads the whole segment and processes it using
ALPS library: 
    ```
    val directByteBuffer = ByteBuffer.allocateDirect(segmentBuffer.size)
    directByteBuffer.put(segmentBuffer)
    alps.processIsobmffSegment(directByteBuffer) <--- direct Alps library call
    directByteBuffer.position(0)
    directByteBuffer.get(segmentBuffer)
    inputStream = ByteArrayInputStream(segmentBuffer)
    ```
  After that it returns requested data portions of already processed segment.


For more details about these classes see [HTML code documentation](../docs) or the actual code.

## Logging
AlpsSamples depends on AlpsCore, so it also uses the same logging mechanism as core library. See
[AlpsCore README](../AlpsCore/README.md/#logging) for more details.

## External Signaling
The ALPS library handles multiple ways of signaling what presentations exist in a stream, and what
is their priority. A list of existing presentations can be read from:
  - The DASH manifest, using the [AlpsManifestParser](src/main/java/com/dolby/android/alps/samples/AlpsManifestParser.kt)
  - The MP4 init segment // TODO

* [AlpsManifestParser](src/main/java/com/dolby/android/alps/samples/AlpsManifestParser.kt) - Extension
  of Media3's [DashManifestParser] that adds support for parsing DASH manifest <Preselection> elements.
  This custom parser extends the standard DASH manifest parsing to handle additional metadata that
  can be used for content preselection. The parser creates [PeriodWithPreselections](src/main/java/com/dolby/android/alps/samples/models/PeriodWithPreselections.kt)
  objects instead of standard Period objects, allowing applications to access preselection metadata

## Known issues

### Content Limitations
The ALPS library has been tested using **CMAF (Common Media Application Format)** compliant content.
Using the ALPS library with content that uses features beyond the standardized CMAF feature
subset may cause unexpected behavior.
