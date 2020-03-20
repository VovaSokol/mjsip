package org.mjsip.custom.opus;

class OpusStorage {

    private OpusDecoder decoder;
    private OpusEncoder encoder;

    public OpusStorage() {
        decoder = new OpusDecoder();
        encoder = new OpusEncoder();
    }

    synchronized OpusDecoder getDecoder() {
        return decoder;
    }

    synchronized OpusEncoder getEncoder() {
        return encoder;
    }
}
