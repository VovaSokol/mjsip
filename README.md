MJSIP

Linux desktop - audio.
Raspberry Pi 3 - audio and video.

The mjSIP stack, proxy, and UA reference applications are available under the terms of the GNU GPL license (General Public License).
Official site: http://www.mjsip.org

Added support Opus codec.
Added support H264 codec (Supported only on Raspberry Pi 3). Streaming only from RaspberryPi 3. We cannot receive video packages.

Steps for install:
1) Download and install `Opus`. Command `udo apt install libopus0`
2) Download and install `ffmpeg`. Command `sudo apt install ffmpeg`

Basic usage example - org.mjsip.ua.UA
