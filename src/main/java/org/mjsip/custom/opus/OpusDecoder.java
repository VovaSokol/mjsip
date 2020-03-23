package org.mjsip.custom.opus;

import org.mjsip.custom.LibraryLoader;

public class OpusDecoder {
    static {
        try{
            LibraryLoader.loadLibrary("libopusjni");
        }catch (Exception e){
            System.out.println("Opus library not loaded: " + e.getMessage());
        }
    }


    /* Native pointer to OpusDecoder */
    private long address;

    private native int nativeInitDecoder(int samplingRate, int numberOfChannels);

    private native int nativeDecodeShorts(byte[] in, short[] out, int frames);

    private native int nativeDecodeBytes(byte[] in, byte[] out, int frames);

    private native boolean nativeReleaseDecoder();

    public void init(int sampleRate, int channels) {
        this.nativeInitDecoder(sampleRate, channels);
    }

    public int decode(byte[] encodedBuffer, short[] buffer, int frames) {
        return this.nativeDecodeShorts(encodedBuffer, buffer, frames);
    }

    public int decode(byte[] encodedBuffer, byte[] buffer, int frames) {
        return this.nativeDecodeBytes(encodedBuffer, buffer, frames);
    }

    public void close() {
        this.nativeReleaseDecoder();
    }
}
