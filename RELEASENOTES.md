# 3.0.0

This release includes the following changes since the 2.0.0

### Features

- Updated ALPS Native dependency to v3.0.0
- Support HLS
  - Updated `media3` dependency from `1.4.1` to `1.8.0`, adding support for `media3-exoplayer-hls`.
  - Structural changes:
      - Moved `AlpsDashChunkSourceFactory` to `com.dolby.android.alps.samples.dash` package.
      - Renamed `AlpsHttpDataSource` to `AlpsDashHttpDataSource` and moved to `com.dolby.android.alps.samples.dash` package
      - Renamed `AlpsManifestParser` to `AlpsDashManifestParser` and moved to  `com.dolby.android.alps.samples.dash` package
      - `AlpsManager` is now an interface, with two concrete implementations `AlpsManagerDash` (DASH) and `AlpsManagerHls` (HLS), each in their respective packages.
      - Renamed the `presentations` flow exposed by the `AlpsManager` to `isobmffPresentations`
  - HLS Support:
      - Introduced `AlpsHlsHttpDataSource`, `AlpsHlsDataSourceFactory`, and `AlpsManagerHls` for ALPS processing in HLS streams
      - Added `DelegatingHlsPlaylistTracker` to monitor playlist updates and detect AC-4 renditions
      - `AlpsManagerHls` handles HLS streams with support for interstitials.
  - API Updates:
      - `AlpsMediaSourceFactory` now supports HLS via new `alpsHlsFactory` parameter. For usage examples check README.md
- Added an API to get the active presentation ID from AlpsManager
- Increased `compileSdk` and `targetSdk` from 34 to 35 across all modules.


### BREAKING CHANGES

- This update breaks compatibility with streams using 16-bit ISOBMFF Dialog Gain signalling

### Bug Fixes

- Prevent a race condition in releaseUnusedAlpsObjects
- Removed `app` module - support for sample application has been dropped

# 2.0.0

This release includes the following changes since the 1.0.0

### Features

- Based on ALPS Native v2.0.0
- Improved ISOBMFF signalling - `Presentation` object provides more information parsed from ISOBMFF
- External signalling support
- Support for multiple ALPS objects
- Lowered minSdk to 28
- **AlpsSamples:** Support for multi-period DASH content using `AlpsManager`
- **AlpsSamples:** Added `AlpsDashChunkSourceFactory` - allows using ALPS for AC-4 DASH chunks
- **AlpsSamples:** Added `AlpsMediaSourceFactory` - allows using custom `DashMediaSource.Factory` 
for DASH content
- **AlpsSamples:** Added `AlpsManifestParser` - adds support for parsing DASH manifest 
`<Preselection>` elements and converting them to `Presentation`- can be used for external signalling
- **app:** Added Multi-period DASH content support using `AlpsManager`
- **app:** Added setting to disable ALPS

### BREAKING CHANGES

- Alps `close` API renamed to `release`
- `Presentation` object structure has changed: `extendedLanguage` is nullable now, `label` has 
changed to `labels` and is now a list of objects
- Removed `Ac4DataSourceDetector` - AC-4 detection functionality moved to 
`AlpsDashChunkSourceFactory`

# 1.0.0

First public release

### Features

- Based on ALPS Native v1.0.0
- Selection of active presentation from AC-4 bitstream
- Processing ISOBMFF segment to force selected presentation decoding
- Fetching presentations list available in the stream
- Fetching active presentation ID
- Presentations list change detection - callback when detected
- Library logs can be controlled by application
- **AlpsSamples:** Added `AlpsHttpDataSource` class as upstream data source for simple ALPS 
integration with ExoPlayer
- **AlpsSamples:** Added `DashManifestAc4DataSourceDetector` for detection whether source defined by 
`DataSpec` is AC-4 stream using DASH manifest
- **CLI:** Processing directory of mp4 segments (.mp4 & .m4s files) using ALPS core library
- **CLI:** Modifying AC-4 bitstream accordingly to provided enabled presentation ID
- **CLI:** Saving processed segments in selected output directory
- **CLI:** Logging library logs to logcat
- **app:** Integrated ALPS and provided UI for presentations switching
- **app:** Content selection screen
- **app:** Dynamic settings (app configuration) loading
- **app:** Basic DRM content playback support
- **app:** `AlpsLogger` setting example
