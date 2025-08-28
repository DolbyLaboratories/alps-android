# ALPS Samples
Library samples module. Includes code that might be helpful to integrate AlpsCore with ExoPlayer 
based playback applications. If implemented helper classes don't fit to the app, it still can be 
treated as an example code, showing how to use AlpsCore.

## Table of Contents
- [Installation](#installation)
- [Usage](#usage)
- [Logging](#logging)

## Installation
ALPS library is not published on Maven Central or other central repository. So too use it, there are
2 options:
- copy code of this module and build it together with application
- download prebuilt version of AlpsSamples module (.aar file) from [releases](releases) and link it 
to your application

## Usage
Main class of this module is 
[AlpsHttpDataSource](src/main/java/com/dolby/android/alps/samples/AlpsHttpDataSource.kt) which is 
a custom implementation of BaseDataSource and implements HttpDataSource interface. Thanks to that it
can be used as Upstream Data Source in Exoplayer pipeline. So AlpsHttpDataSource is an alternative 
for DefaultHttpDataSource. It adds ALPS library processing on top of DefaultHttpDataSource (or other 
implementation of HttpDataSource interface) operations.

For better separation of concerns, actual ALPS related processing is done in
[AlpsProcessing](src/main/java/com/dolby/android/alps/samples/AlpsProcessing.kt).
AlpsHttpDataSource responsibility is to decide whether current data source should be processed by 
ALPS. To achieve that, it uses 
[Ac4DataSourceDetector](src/main/java/com/dolby/android/alps/samples/utils/Ac4DataSourceDetector.kt).
It's an interface with one function ***isAc4DataSource(uri: Uri)*** that is supposed to give answer
whether data source pointed by URI is an AC-4 stream. Default implementation of that interface is
using DASH manifest to align URI with specific Adaptation set or Representation and its mime type.

[AlpsProcessing](src/main/java/com/dolby/android/alps/samples/AlpsProcessing.kt) opens http data 
source, downloads the whole segment and processes it using ALPS library:
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