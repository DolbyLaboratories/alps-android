# Release notes

## 1.0.0 (2024-11-14)
ALPS LA RC#1

* Features:
  * Enables simple ALPS integration with Exoplayer using AlpsHttpDataSource class as upstream data 
source
  * Preparing buffer with ISO BMFF segment bytes, and providing it to ALPS core library
  * Passing processed segment data further in the media pipeline, acting as DefaultHttpDataSource
  * Detection whether source defined by DataSpec is AC-4 stream using DASH manifest