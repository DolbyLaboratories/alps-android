# Release notes

## 1.1.0 (2025-07-14)

* New Features:
  * Supports multi-period DASH content
    * Introduces the AlpsManager class to manage multiple ALPS objects
    * Each period is associated with one ALPS object
    * AlpsMediaSourceFactory, a helper class that allows using custom DashMediaSource.Factory for DASH content
    * AlpsDashChunkSourceFactory,a helper class that allows using ALPS for AC-4 DASH chunks
* Removed:
  * Ac4DataSourceDetector - AC-4 detection functionality moved to AlpsDashChunkSourceFactory 

## 1.0.0 (2024-11-14)
ALPS LA RC#1

* Features:
  * Enables simple ALPS integration with Exoplayer using AlpsHttpDataSource class as upstream data 
source
  * Preparing buffer with ISO BMFF segment bytes, and providing it to ALPS core library
  * Passing processed segment data further in the media pipeline, acting as DefaultHttpDataSource
  * Detection whether source defined by DataSpec is AC-4 stream using DASH manifest
