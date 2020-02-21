package org.mjsip.custom.opus;

import org.zoolu.sound.SimpleAudioSystem;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

/*
Copyright 2007 Creare Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
/*
 *****************************************************************
 ***                ***
 ***  Name :  UDPInputStream                                 ***
 ***  By   :  U. Bergstrom   (Creare Inc., Hanover, NH)  ***
 ***  For  :  E-Scan            ***
 ***  Date :  October, 2001          ***
 ***                ***
 ***  Copyright 2001 Creare Inc.        ***
 ***  All Rights Reserved          ***
 ***                ***
 ***  Description :            ***
 ***       This class extends InputStream, providing its API   ***
 ***   for calls to a UDPSocket.                               ***
 ***                ***
 *****************************************************************
 */
//package com.rbnb.utility;

public class OpusInputStream extends AudioInputStream {
    public static final int SAMPLE_RATE = 48000;
    public static final int NUM_CHANNELS = 1;
    public static final int SAMPLE_SIZE_IN_BITS = 16;
    public static final boolean BIG_ENDIAN = false;
    //    public static final boolean SIGNED = true;
    public static final AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_SIGNED;
    // Number of samples per frame is not arbitrary,
    // it must match one of the predefined values, specified in the standard.
    public static final int FRAME_SIZE = 480;
    public static final int READ_BYTES_FROM_MIC = FRAME_SIZE * NUM_CHANNELS * 2;


    private boolean isRecording = false;
    //    private byte[] inputArray = new byte[FRAME_SIZE * NUM_CHANNELS * 2];
    private byte[] arrayForEncode = new byte[READ_BYTES_FROM_MIC];
    //    private short[] arrayForDecode = new short[FRAME_SIZE * NUM_CHANNELS];
    private byte[] arrayForDecode = new byte[READ_BYTES_FROM_MIC];
    private Thread recordingThread;
    private Thread streamingThread;
    private boolean streamingThreadStarted;
    private OpusStorage opusStorage;

    private AudioFormat format;
    private DataLine.Info info;
    private DataLine.Info infoForPlay;
    private TargetDataLine line;
    private SourceDataLine player;
    private ConcurrentLinkedQueue<byte[]> soundQueue;
    private boolean isNeedToPlay;
    private boolean isAllMsgRecorded = true;
    private boolean isPlayingLog;
    private Mixer.Info mixer;

    public OpusInputStream() throws LineUnavailableException {
        super(AudioSystem.getTargetDataLine(new AudioFormat(encoding, SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, NUM_CHANNELS, SAMPLE_SIZE_IN_BITS / 8, SAMPLE_RATE, BIG_ENDIAN), SimpleAudioSystem.getMixer_info()));
        try {
            format = new AudioFormat(encoding, SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, NUM_CHANNELS, SAMPLE_SIZE_IN_BITS / 8, SAMPLE_RATE, BIG_ENDIAN);
            SimpleAudioSystem.setMixer(format);
            this.mixer = SimpleAudioSystem.getMixer_info();
            line = AudioSystem.getTargetDataLine(format, mixer);
        } catch (LineUnavailableException | IllegalArgumentException e) {
            e.printStackTrace();
        }
        String name = System.getProperty("os.name").toLowerCase();
        System.out.println(name);
        opusStorage = new OpusStorage();
        soundQueue = new ConcurrentLinkedQueue<>();
    }

    public void open(){
    }

    public void start(){
        isRecording = true;
        isAllMsgRecorded = false;
        try {
            line.open(format);
            line.start();
            opusStorage.getEncoder().init(SoundRecorder.SAMPLE_RATE, SoundRecorder.NUM_CHANNELS, OpusEncoder.OPUS_APPLICATION_VOIP);
            /*recordingThread = new Thread(() -> {
                System.out.println("========= OPUS MIC ENCODER THREAD STARTED " + Thread.currentThread().getName());
                int numOfBytesRead;
                byte[] arrayToRemmember = new byte[line.getBufferSize() / 5];
                opusStorage.getEncoder().init(SoundRecorder.SAMPLE_RATE, SoundRecorder.NUM_CHANNELS, OpusEncoder.OPUS_APPLICATION_VOIP);
                while (isRecording) {
                    numOfBytesRead = line.read(arrayToRemmember, 0, READ_BYTES_FROM_MIC);
                    if (arrayToRemmember != null && arrayForEncode != null) {
                        int encoded = opusStorage.getEncoder().encode(arrayToRemmember, FRAME_SIZE, arrayForEncode);
                        soundQueue.add(Arrays.copyOf(arrayForEncode, encoded));
//                        System.err.println(encoded);
                    }
                    try {
                        Thread.sleep(1L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        line.close();
                        opusStorage.getEncoder().close();
                        isRecording = false;
                        isAllMsgRecorded = true;
                    }
                }
                line.close();
                opusStorage.getEncoder().close();
                System.out.println("========= OPUS MIC ENCODER THREAD ENDED " + Thread.currentThread().getName());
            }, "AudioRecorder Thread");
            recordingThread.start();*/
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    public void close(){
        System.out.println("========= OPUS MIC ENCODER THREAD CLOSING... " + Thread.currentThread().getName());
        isRecording = false;
        if (recordingThread != null){
            recordingThread.interrupt();
        }
        soundQueue.clear();
        line.stop();
        line.close();
    }


    public int read(byte[] buff) throws IOException {
        buff = soundQueue.poll();
        if (buff != null) {
            return buff.length;
        }
        return 0;
    }

    public byte[] readData(){
        return soundQueue.poll();
    }

    public int read(byte b[], int off, int len) throws IOException {
        byte[] arrayToRemmember = new byte[line.getBufferSize() / 5];
        byte[] buff = null;
        line.read(arrayToRemmember, 0, READ_BYTES_FROM_MIC);
        if (arrayToRemmember != null && arrayForEncode != null) {
            int encoded = opusStorage.getEncoder().encode(arrayToRemmember, FRAME_SIZE, arrayForEncode);
            Arrays.copyOf(arrayForEncode, encoded);
            buff = Arrays.copyOf(arrayForEncode, encoded);
        }
        if(buff != null) {
            int count = buff.length;
            for(int i = 0; i < count; i++){
                b[off + i] = buff[i];
            }
            return count;
        }
        return 0;
    }
}
