MJSIP

Linux desktop - audio.
Raspberry Pi 3 - audio and video (only armv7l).

The mjSIP stack, proxy, and UA reference applications are available under the terms of the GNU GPL license (General Public License).
Official site: http://www.mjsip.org

Added support Opus codec.
Added support H264 codec (Supported only on Raspberry Pi 3). Streaming only from RaspberryPi 3. We cannot receive video packages.

Steps for install:
1) Download, compile and install Opus.
2) Run `sudo ./system_setup.sh` for configure your system.

Basic usage example - org.mjsip.ua.UA
