# ALPS Core
AlpsCore module includes core ALPS library functionality.

## Table of Contents
- [Installation](#installation)
- [Usage](#usage)
- [API](#api)
- [Logging](#logging)
- [AC-4 content requirements](#ac-4-content-requirements)
- [Buffer management](#buffer-management)
- [Known issues](#known-issues)

## Installation
ALPS library is not published on Maven Central or other central repository. So to use it, there are 
2 options:
- copy code of this module and build it together with application
- download prebuilt version of AlpsCore module (.aar file) from [releases](releases) and link it to
your application

## Usage
Import and instantiate [Alps](src/main/java/com/dolby/android/alps/Alps.kt). You can set a callback 
function that will be called if the list of presentations changes. The following example updates the 
list of presentations. For more details check out the [API](#api) section.
```
import com.dolby.android.alps.Alps;

val alps = Alps().apply {
    setPresentationsChangedCallback(object: PresentationsChangedCallback{
        override fun onPresentationsChanged() {
            val presentations = this@apply.getPresentations()
            val selectedPresentationId = 1
            // you can change presentation ID of the presentation you want to be played, by calling setActivePresentationId method:
            this@apply.setActivePresentationId(selectedPresentationId)
            // if not set explicit alps will try to play previouvsly selected activePresentationId
        }
    })
}
```
**Alps object should be used for single period of content.** For multi-period content, multiple
instances of Alps should be used. 

Alps processes memory buffers that contain ISO BMFF segment data. These buffers are typically the 
result of network requests performed by the media player. After the player has downloaded the ISO 
BMFF segment data, data is processed using the ALPS method 'processIsobmffSegment'. The processed 
data is then returned to the media player for forwarding to the decoder. Integration with the media 
player depends on the player and differs from player to player implementation. 

[AlpsSamples](../AlpsSamples) module provides some helper classes showing how ALPS can be 
wrapped/used to integrate it with ExoPlayer. [Sample app](../app) module uses both AlpsCore and 
AlpsSamples helper classes and provide working example of playback application with ALPS library 
integrated.

## API
AlpsCore library API is represented by [Alps](src/main/java/com/dolby/android/alps/Alps.kt) class.
Detailed API description can be found in [HTML code documentation](../docs) or in the code directly.

## Logging
ALPS library logs can be controlled by application. By default library will not print any logs. To
catch library logs and handle them as you like, use 
[AlpsLogger](src/main/java/com/dolby/android/alps/logger/AlpsLogger.kt) interface and 
[AlpsLoggerProvider](src/main/java/com/dolby/android/alps/logger/AlpsLogger.kt) object. Just 
override AlpsLoggerProvider.logger with you own implementation of AlpsLogger, for example:
```
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

## AC-4 content requirements
ALPS discovers available presentations by parsing ISO BMFF level information in accordance with 
latest MPEG standards. Below is an abbreviated example of ISO BMFF structure with boxes that are 
relevant to ALPS. ALPS requires content to be packaged containing this information so that the 
available presentations and their properties is known.
```
[moov]
    [trak]
        [tkhd]
            track_ID
[meta]
    [grpl]
        [prsl]
            entities
                entity_id
            preselection_tag
            [labl]
            [elng]
```
ALPS functionality depends on parsing and modifying AC-4 TOC. DRM protected content may be 
compatible with ALPS if the TOC is left  unencrypted. In that case, only the audio substreams are 
encrypted.

## Buffer management
Devices typically buffer some audio to ensure a good playback experience in cases of varying network
conditions. These buffers need to be flushed when changing the presentation to apply the changed 
audio configuration quickly after the user selection has been made. Flushing buffers should ideally
be provided by the player and should be triggered when a different presentation is selected. Since 
not all players have built-in support for buffer flushing, in Sample app we used a workaround that
stops the player and prepares it again, what causes buffer flush.
```
private fun flushBuffer() {
    if (player != null) {
        val currentPosition = player!!.currentPosition

    player!!.stop()

    player!!.seekTo(currentPosition)
    player!!.prepare()

    player!!.playWhenReady = true
    }
}
```
This workaround is far from optimal, so it is recommended to implement buffer flushing mechanism
accordingly to player/application implementation to optimize switching presentation.

## Known issues

### Content Limitations
The ALPS library has been tested using **CMAF (Common Media Application Format)** compliant content.
Using the ALPS library with content that uses features beyond the standardized CMAF feature
subset may cause unexpected behavior.
