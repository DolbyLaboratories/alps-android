# ALPS Android
ALPS (Application Layer Presentation Selection) enables selection of a presentation from a **Dolby**
AC-4 bitstream. The **Dolby** AC-4 decoder will subsequently decode only the selected presentation 
from that bitstream, ignoring all the other presentations. ALPS Android is a JNI-based wrapper 
around the native library for integration with Android playback applications and is built to process 
ISOBMFF media segments.

## Table of Contents
- [Installation](#installation)
- [Quickstart](#quickstart)
- [API](#api)
- [**Dolby** AC-4 presentation signaling](#dolby-ac-4-presentation-signaling)
- [Known limitations](#known-limitations)
- [Support / Contact](#support--contact)
- [License & third-party](#license--third-party)
- [Release notes](#release-notes)

## Installation
This library is not published on Maven Central. You have two options to install the library:
1) Copy the modules you want to use into your project and include it as Gradle modules.
2) Use a prebuilt AAR from `<module>/releases` and link it to your app.

### Modules
ALPS Android project consists of:
- [AlpsCore](AlpsCore/README.md) — Core library module with ALPS functionality.
- [AlpsSamples](AlpsSamples/README.md) — Media3/ExoPlayer integration helpers and examples.
- [App](app/README.md) — ALPS-integrated sample playback app.
- [CLI](CLI/README.md) — Headless Android tool for automated AlpsCore testing.

## Quickstart
### Using AlpsCore only
Instantiate [Alps](AlpsCore/src/main/java/com/dolby/android/alps/Alps.kt). Optionally, set a 
presentations-changed callback, and process ISOBMFF audio segments before passing them to the decoder.
```kotlin
val alps = Alps().apply {
    setPresentationsChangedCallback(object: PresentationsChangedCallback{
        override fun onPresentationsChanged() {
            val presentations = this@apply.getPresentations()
            val selectedPresentationId = 1
            this@apply.setActivePresentationId(selectedPresentationId)
        }
    })
}
```
Note: **Alps object should be used for single period of content.** For multi-period content, multiple
instances of Alps should be used - we recommend usage of 
[AlpsManager](AlpsSamples/src/main/java/com/dolby/android/alps/samples/AlpsManager.kt)
if multi-period content support is needed.

### Using AlpsSamples module
This module consists of couple classes/layers that fit into the Media3 architecture. Using all layers
together is the most straightforward approach but if it does not suite your app, you may also use
just some of them. Going from the outermost layer:
* [AlpsMediaSourceFactory](AlpsSamples/src/main/java/com/dolby/android/alps/samples/AlpsMediaSourceFactory.kt) -
  Custom MediaSource.Factory that allows using custom DashMediaSource.Factory for DASH content
  and DefaultMediaSourceFactory for other content. Allows using AlpsDashChunkSourceFactory.
* [AlpsDashChunkSourceFactory](AlpsSamples/src/main/java/com/dolby/android/alps/samples/AlpsDashChunkSourceFactory.kt) -
  Custom DashChunkSource.Factory class that allows applying ALPS for proper DASH chunks (**Dolby** AC-4).
* [AlpsHttpDataSource](AlpsSamples/src/main/java/com/dolby/android/alps/samples/AlpsHttpDataSource.kt) - Custom
  implementation of BaseDataSource and implements HttpDataSource interface. It adds ALPS library
  processing on top of DefaultHttpDataSource (or other implementation of HttpDataSource interface)
  operations. For better separation of concerns, actual ALPS related processing is done in
  AlpsProcessing.
* [AlpsProcessing](AlpsSamples/src/main/java/com/dolby/android/alps/samples/AlpsProcessing.kt) - 
  Opens http data source, downloads the whole segment and processes it using the ALPS library:
    ```kotlin
    val directByteBuffer = ByteBuffer.allocateDirect(segmentBuffer.size)
    directByteBuffer.put(segmentBuffer)
    alps.processIsobmffSegment(directByteBuffer) <--- direct Alps library call
    directByteBuffer.position(0)
    directByteBuffer.get(segmentBuffer)
    inputStream = ByteArrayInputStream(segmentBuffer)
    ```
  After that it returns requested data portions of the already processed segment.
* [AlpsManager](AlpsSamples/src/main/java/com/dolby/android/alps/samples/AlpsManager.kt) - allows 
  simpler multi-period content handling. We recommend to use it instead of using Alps objects 
  directly. It provides similar API to Alps class but takes care of handling multiple Alps objects
  for each period.
  ```kotlin
  val alpsManager = AlpsManager()
  /** Inject it into AlpsDashChunkSourceFactory when setting ExoPlayer pipeline **/
  val dashChunkSourceFactory = AlpsDashChunkSourceFactory(
      alpsManager = alpsManager,
      DefaultHttpDataSource.Factory()
  )
  /** Use it during playback **/
  /* Provide current period index OR set alpsManager as Player's AnalyticsListener */
  alpsManager.setCurrentPeriodIndex(periodIndex)
  /* Observe presentations */
  alpsManager.presentations.collect { presentations -> ... }
  /* Set desired presentation ID */
  alpsManager.setActivePresentationId(id)
  
  /** Release when playback finished **/
  alpsManager.release()
  ```

### Logging
ALPS library logs can be controlled by the application. By default library will not print any logs. 
To catch library logs and handle them as you like, use
[AlpsLogger](AlpsCore/src/main/java/com/dolby/android/alps/logger/AlpsLogger.kt) interface and
[AlpsLoggerProvider](AlpsCore/src/main/java/com/dolby/android/alps/logger/AlpsLogger.kt) object. 
Just override AlpsLoggerProvider.logger with your own implementation of AlpsLogger, for example:
```kotlin
AlpsLoggerProvider.logger = object: AlpsLogger {
    override fun logInfo(message: String) {
        Napier.i(
            tag = ALPS_LIBRARY_LOGS_TAG,
            message = message
        )
    }

    override fun logWarn(message: String) {
        Napier.w(
            tag = ALPS_LIBRARY_LOGS_TAG,
            message = message
        )
    }

    override fun logError(message: String) {
        Napier.e(
            tag = ALPS_LIBRARY_LOGS_TAG,
            message = message
        )
    }
}
```

## API
AlpsCore library API is represented by [Alps](AlpsCore/src/main/java/com/dolby/android/alps/Alps.kt) 
class.

If [AlpsManager](AlpsSamples/src/main/java/com/dolby/android/alps/samples/AlpsManager.kt) from
AlpsSamples module is used, it can be treated as a library API.

Detailed API description can be found in [HTML code documentation](docs) or in the code directly.

## **Dolby** AC-4 presentation signaling
ALPS supports two signaling methods:

- ISOBMFF init segment level signaling
- External signaling

### ISOBMFF init segment signaling
ALPS supports presentation signaling on ISOBMFF init segment level and provides parsing support for 
this data. An abbreviated example of the ISOBMFF structure with boxes that are relevant to ALPS can be 
found below. **Dolby** AC-4 bitstreams may be packaged containing this information so that the available 
presentations and their properties can be derived from the ISOBMFF segment. Information derived from
those boxes can be accessed through the `getPresentations` method.

```
[moov]
    [trak]
        [tkhd]
            track_ID
[meta]
    [grpl]
        [prsl]
            entity_id
            preselection_tag
            selection_priority
            [elng]
                extended_language
            [labl]
                label_id
                language
                label
            [ardi]
                audio_rendering_indication
            [kind]
                schemeURI
                value
            [udta]
                [diap]
                    dialog_gain

```

### External signaling
ALPS supports presentation signaling by external means, typically done via MPEG-DASH manifest. 
Parsing this data is out-of-scope for the ALPS library and must be performed on application level. 
In such a case ALPS still allows to set the active presentation using `setActivePresentationId`. The 
`getPresentations` method can be used to validate whether the presentations from the external source
match those from the ISOBMFF init segment. Note that ALPS will always validate the selected 
presentation ID against the presentation IDs found in the TOC and disable processing if the 
requested ID is not available in the TOC.

AlpsSamples module provide a helper class
[AlpsManifestParser](AlpsSamples/src/main/java/com/dolby/android/alps/samples/AlpsManifestParser.kt)
which is an extension of Media3's DashManifestParser that adds support for parsing DASH manifest 
<Preselection> elements. This custom parser extends the standard DASH manifest parsing to handle 
additional metadata that can be used for content preselection. The parser creates 
[PeriodWithPreselections](AlpsSamples/src/main/java/com/dolby/android/alps/samples/models/PeriodWithPreselections.kt)
objects instead of standard Period objects, allowing applications to access preselection metadata

## Known limitations
### CMAF compliance
The ALPS library has been tested using **CMAF (Common Media Application Format)** compliant content.
Using the ALPS library with content that uses features beyond the standardized CMAF feature subset
might cause unexpected behavior.

### **Dolby** AC-4 content requirements
ALPS functionality depends on parsing and modifying **Dolby** AC-4 TOC. DRM protected content may be
compatible with ALPS if the TOC is left unencrypted. In that case, only the audio substreams are
encrypted.

### Buffer management
Devices typically buffer some audio to ensure a good playback experience in cases of varying network
conditions. These buffers need to be flushed when changing the presentation to apply the changed
audio configuration quickly after the user selection has been made. Flushing buffers should ideally
be provided by the player and should be triggered when a different presentation is selected.

## Support / Contact
- Questions, issues, or feature requests: please open an issue in this repository.
- For commercial support inquiries, contact your Dolby representative.

## License & third-party
- License: see [LICENSE.md](LICENSE.md)
- Third-party notices: see `thirdPartyLicenses/` directories in modules.

## Release notes
See [RELEASENOTES.md](RELEASENOTES.md)
