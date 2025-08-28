# Android ALPS project
ALPS (Application Layer Presentation Selection) enables selection of a presentation from an AC-4 
bitstream. The AC-4 decoder will subsequently decode the selected presentation from that bitstream 
only, ignoring all the other presentations. Android ALPS is designed for integration with Android 
playback application and built to process ISOBMFF media segments.

## Modules
Android ALPS project consists of:
- [AlpsCore](AlpsCore/README.md) - Core library module. Provides all necessary ALPS functionalities.
- [AlpsSamples](AlpsSamples/README.md) - Library samples module. Includes code that might be helpful 
to integrate AlpsCore with ExoPlayer based playback applications. If code doesn't fit to the app, it
still can be treated as an example code, showing how to use AlpsCore.
- [App](app/README.md) - Sample playback application with ALPS library integrated.
- [CLI](CLI/README.md) - Command Line Interface Android tool/app. Simple tool for automated AlpsCore 
testing.

More details about each module can be found in its README, click on the module name to open it.

