# Release notes


## 1.1.0 (2025-07-14)

* New features:
  * Supports multi-period DASH content
  * ALPS
    * Uses ALPS for Android v1.1.0
    * Added AlpsManager usage example



## 1.0.1 (2024-12-03)
* New features:
  * Added setting to disable ALPS

## 1.0.0 (2024-11-14)
* New features:
  * ALPS
    * Uses ALPS for Android v1.0.0
    * Added PresentationsChangedCallback setting example
    * Added AlpsLogger setting example
* Bug fixes:
  * Fixed incorrectly showing selected presentation on presentations list UI widget
* Known limitations:
  * Multi-period DASH content not supported

## 0.3.0 (2024-10-09)
* New features:
  * DRM content playback support
    * See [README](README.md) for details

## 0.2.0 (2024-09-26)
* New features:
  * Dynamic settings (app configuration) loading
    * See App configuration in [README](README.md) for details
  * Presentations list UI widget
    * Supports any kind of AC-4 stream
    * Alternative for "Dialog enhancer popup" to handle presentation selection
    * Should be used for content that doesn't meet Dialog Enhancement demo content requirements

## 0.1.0 (2024-09-13)
This is the first release of Android ALPS sample application. It provides basic functionalities for
ALPS library testing and demoing.

* Features:
  * ALPS
    * Based on ALPS for Android v0.3.0
    * AlpsHttpDataSource used for intercepting media pipeline data. Used as upstream datasource in 
CacheDataSource.
    * AC-4 data source detection based on DASH Manifest, init segments URLs
      * Based on detection results, only AC-4 data source are being processed
    * Multi-period DASH content not supported
  * Content screen
    * Content loaded dynamically based on content JSON stored on cloud. Link to the content is 
hardcoded during app building.
    * Presents available categories based on contents "category" field values
    * Proper content shown based on selected category
    * Poster in the background adjusts to last selected content item with smooth animation when changing
    * Content info adjusts to last selected content - based on "title" and "description" fields for 
particular content in content JSON.
  * Player screen
    * Basic playback controls (play/pause/...) available. Custom Dolby UI design applied.
    * "Dialog enhancer" button available
      * Dialog enhancer popup window shown if content has enough presentations (at least 2)
      * For proper work of dialog enhancer UI element, AC-4 stream has to follow DE demoing 
assumptions. For details see [README](README.md) -> Dialog Enhancement -> Content requirements.
      * Dialog Enhancement level can be set from "Dialog Off", through "Dialog original", to higher
dialog enhancement levels (these levels depend on how many presentations are available in AC-4 stream)
      * To lower Dialog Enhancement level use LEFT or DOWN arrow on remote
      * To rise Dialog Enhancement level use RIGHT or UP arrow on remote

