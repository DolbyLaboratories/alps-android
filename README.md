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
ALPS Android is published on [Maven Central](https://central.sonatype.com/namespace/com.dolby.android.alps).

Make sure `mavenCentral()` is in your project's repositories.

Then add the modules you need to your app's `build.gradle.kts`:
```kotlin
dependencies {
    // Core ALPS library — always required
    implementation("com.dolby.android.alps:alps-core:<version>")

    // Optional: Media3/ExoPlayer integration helpers and examples
    implementation("com.dolby.android.alps:alps-samples:<version>")
}
```
Replace `<version>` with the latest release (see [Release notes](RELEASENOTES.md)) — for example:
```kotlin
implementation("com.dolby.android.alps:alps-core:3.1.0")
```

[![ALPS Android](https://maven-badges.sml.io/sonatype-central/com.dolby.android.alps/alps-core/badge.svg?subject=ALPS%20Android)](https://central.sonatype.com/artifact/com.dolby.android.alps/alps-core)

### Building from source
The native part of ALPS is built from source. The
[ALPS Native](https://github.com/DolbyLaboratories/alps-native) library is included as a git
submodule in `repos/dlb_alps_native`, so clone the repository together with its submodules:
```shell
git clone --recursive https://github.com/DolbyLaboratories/alps-android.git
```
For an existing clone, run `git submodule update --init --recursive`. Gradle also initializes the
submodule automatically on the first build of a git checkout. Source archives downloaded without git
history (e.g. GitHub "Download ZIP") do not contain the submodule — use `git clone` instead.

Requirements:
- JDK 17 or newer
- Android SDK with NDK `28.2.13676358` and CMake `3.22.1`

Build all modules with:
```shell
./gradlew assemble
```

### Modules
ALPS Android project consists of:
- [AlpsCore](AlpsCore/README.md) — Core library module with ALPS functionality.
- [AlpsSamples](AlpsSamples/README.md) — Media3/ExoPlayer integration helpers and examples.
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
  and DefaultMediaSourceFactory for other content. Allows using AlpsDashChunkSourceFactory. For usage example see the code snippet below.
  * [AlpsDashChunkSourceFactory](AlpsSamples/src/main/java/com/dolby/android/alps/samples/dash/AlpsDashChunkSourceFactory.kt) -
    Custom DashChunkSource.Factory class that allows applying ALPS for proper DASH chunks (**Dolby** AC-4).
* [AlpsDashHttpDataSource](AlpsSamples/src/main/java/com/dolby/android/alps/samples/dash/AlpsDashHttpDataSource.kt) - Custom
  implementation of BaseDataSource and implements HttpDataSource interface. It adds ALPS library
  processing on top of DefaultHttpDataSource (or other implementation of HttpDataSource interface)
  operations. For better separation of concerns, actual ALPS related processing is done in
  AlpsProcessing. Used internally by AlpsDashChunkSourceFactory.
* [AlpsHlsHttpDataSource](AlpsSamples/src/main/java/com/dolby/android/alps/samples/hls/AlpsHlsHttpDataSource.kt) - Similar to AlpsDashHttpDataSource but for HLS content.
* [AlpsHlsDataSourceFactory](AlpsSamples/src/main/java/com/dolby/android/alps/samples/hls/AlpsHlsDataSourceFactory.kt) - Similar to AlpsDashHttpDataSource but for HLS content.
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
* [DelegatingHlsPlaylistTracker](AlpsSamples/src/main/java/com/dolby/android/alps/samples/hls/DelegatingHlsPlaylistTracker.kt) - 
  A delegating HlsPlaylistTracker that intercepts playlist refreshes to expose the multivariant and 
  media playlists (along with the stream URI) via callbacks, enabling AlpsManagerHls to detect AC-4
  streams and assign Alps instances to them.
* [AlpsManager](AlpsSamples/src/main/java/com/dolby/android/alps/samples/AlpsManager.kt) - allows 
  simpler multi-period content handling. We recommend to use it instead of using Alps objects 
  directly. It provides similar API to Alps class but takes care of handling multiple Alps objects
  for each period. There are separate implementations for DASH
  [AlpsManagerDash](AlpsSamples/src/main/java/com/dolby/android/alps/samples/dash/AlpsManagerDash.kt) 
  and HLS [AlpsManagerHls](AlpsSamples/src/main/java/com/dolby/android/alps/samples/hls/AlpsManagerHls.kt).
  ```kotlin
  // ------ SETUP STEP -------
  
  /** Create instances of AlpsManager 
    * Note: This sample shows the setup for DASH and HLS streams. You may only need one of them depending on your use case.
  */
  val alpsManagerDash = AlpsManagerDash()
  val alpsManagerHls = AlpsManagerHls()
    
  /** Inject it into AlpsDashChunkSourceFactory when setting ExoPlayer pipeline **/
  val alpsChunkSourceFactory = AlpsDashChunkSourceFactory(
   alpsManager = alpsManagerDash,
   defaultHttpDataSourceFactory = DefaultHttpDataSource.Factory()
  )
  
  val dashMediaSourceFactory = DashMediaSource.Factory(
      alpsChunkSourceFactory,
      DefaultHttpDataSource.Factory()
  )
  
  // Optionally use AlpsDashManifestParser for external (Manifest) signaling support
  val parser = AlpsDashManifestParser()
  dashMediaSourceFactory.setManifestParser(parser)

  val defaultMediaSourceFactory = DefaultMediaSourceFactory(context)
      .setDataSourceFactory(dataSourceFactory)
  
  val hlsMediaSourceFactory =
      HlsMediaSource.Factory(
          AlpsHlsDataSourceFactory(
              alpsManagerHls,
              DefaultHttpDataSource.Factory(),
          ),
      )
  hlsMediaSourceFactory.setPlaylistTrackerFactory(alpsManagerHls.playlistTrackerFactory)

  val adSourceFactory = HlsInterstitialsAdsLoader.AdsMediaSourceFactory(
      adsLoader,
      binding.playerView,
      hlsMediaSourceFactory
  )
  
  val mediaSourceFactory = AlpsMediaSourceFactory(
      alpsDashFactory = dashMediaSourceFactory,
      alpsHlsFactory = adSourceFactory,
      defaultFactory = defaultMediaSourceFactory
  )
  // Use with ExoPlayer
  val player = ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build()
  
  // Finish setting up player and prepare it with media item as usual

  // ------ PLAYBACK STEP -------
  /* Provide current period index/stream URI OR set alpsManager as Player's AnalyticsListener */

  // OPTION 1: alpsManagerDash.setCurrentPeriodIndex(periodIndex)
  //           alpsManagerHls.setCurrentStreamUri(uri)
  
  // OPTION 2: player.addAnalyticsListener(alpsManagerDash)
  //           player.addAnalyticsListener(alpsManagerHls)

  /* Observe presentations */
  alpsManagerDash.isobmffPresentations.collect { presentations -> ... }
  alpsManagerHls.isobmffPresentations.collect { presentations -> ... }
  // Experimental API, see "Experimental APIs" below
  alpsManagerHls.playlistPresentationsForActiveRendition.collect { presentations -> ... }
  /* Set desired presentation ID */
  alpsManagerDash.setActivePresentationId(id)
  alpsManagerHls.setActivePresentationId(id)

  // ------ CLEANUP STEP -------

  alpsManagerDash.release()
  alpsManagerHls.release()
  ```

#### Multi-track content
**DASH:** AlpsManagerDash and AlpsDashChunkSourceFactory support content with multiple audio
Adaptation Sets per Period, including multiple AC-4 Adaptation Sets or a mix of AC-4 and non-AC-4
Adaptation Sets. ALPS processing is applied only to the AC-4 track currently selected in the player,
using a single ALPS instance per Period. When the player switches to a different AC-4 Adaptation
Set, the exposed presentations are updated from the newly loaded init segment. Manifest-signalled
preselections expose `preselectionComponents`, which can be used to map each preselection to its
Adaptation Set(s).

**HLS:** AlpsManagerHls supports streams with multiple audio renditions, including multiple AC-4
renditions or a mix of AC-4 and non-AC-4 renditions. The exposed presentations follow the audio
rendition currently selected in the player, which requires AlpsManagerHls to be registered as the
player's `AnalyticsListener` (OPTION 2 above). Until the selected rendition is known,
`isobmffPresentations` and `setActivePresentationId` use the first AC-4 rendition, while
`playlistPresentationsForActiveRendition` contains presentations of all renditions.

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

### Experimental APIs
APIs annotated with
[ExperimentalAlpsApi](AlpsCore/src/main/java/com/dolby/android/alps/annotations/ExperimentalAlpsApi.kt)
may change or be removed in any future release without a deprecation cycle. Using such an API 
produces a compiler warning unless you opt in, either with `@OptIn(ExperimentalAlpsApi::class)` on
the calling declaration or for a whole module with the compiler argument
`-opt-in=com.dolby.android.alps.annotations.ExperimentalAlpsApi`.

Currently, the HLS playlist presentations exposed by
[AlpsManagerHls](AlpsSamples/src/main/java/com/dolby/android/alps/samples/hls/AlpsManagerHls.kt)
(`playlistPresentationsForActiveRendition` and the deprecated `playlistPresentations`) are
experimental, as the definition of **Dolby** AC-4 presentation signaling in HLS playlists is
not final yet.

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
[AlpsDashManifestParser](AlpsSamples/src/main/java/com/dolby/android/alps/samples/dash/AlpsDashManifestParser.kt)
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
- Third-party notices: see [THIRDPARTY.md](THIRDPARTY.md) and the `thirdPartyLicenses/` directories
  in modules. ALPS Native third-party notices are listed in
  [NativeLibrary_THIRDPARTY.md](AlpsCore/thirdPartyLicenses/NativeLibrary_THIRDPARTY.md).

## Release notes
See [RELEASENOTES.md](RELEASENOTES.md)
