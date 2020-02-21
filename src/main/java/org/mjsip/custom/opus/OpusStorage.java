package org.mjsip.custom.opus;

class OpusStorage {

    private OpusDecoder decoder;
    private OpusEncoder encoder;

    public OpusStorage() {
        decoder = new OpusDecoder();
//        decoder.init(SoundRecoder.SAMPLE_RATE, SoundRecoder.NUM_CHANNELS);
        encoder = new OpusEncoder();
//        encoder.init(SoundRecoder.SAMPLE_RATE, SoundRecoder.NUM_CHANNELS, OpusEncoder.OPUS_APPLICATION_VOIP);
    }

    synchronized OpusDecoder getDecoder() {
        return decoder;
    }

    synchronized OpusEncoder getEncoder() {
        return encoder;
    }
}
