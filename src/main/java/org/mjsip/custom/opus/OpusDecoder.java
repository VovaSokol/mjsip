package org.mjsip.custom.opus;

public class OpusDecoder {
    static {
        String arch;
        if (System.getProperty("os.arch").equals("arm")) {
            arch = "arm";
        } else {
            arch = "x86";
        }

        try{
            NativeUtils.loadLibraryFromJar("/lib/" + arch, new String[] {"opusjni"});
        }catch (Exception e){
            e.printStackTrace();
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
