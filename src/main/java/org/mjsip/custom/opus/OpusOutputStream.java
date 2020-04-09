package org.mjsip.custom.opus;


import org.mjsip.custom.opus.OpusStorage;
import org.mjsip.custom.opus.SoundRecorder;
import org.zoolu.sound.AudioOutputStream;
import org.zoolu.sound.SimpleAudioSystem;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OpusOutputStream extends AudioOutputStream {
    public static final int SAMPLE_RATE = 48000;
    public static final int NUM_CHANNELS = 1;
    public static final int SAMPLE_SIZE_IN_BITS = 16;
    public static final boolean BIG_ENDIAN = false;
    public static final AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_SIGNED;
    public static final int FRAME_SIZE = 960;
    public static final int READ_BYTES_FROM_MIC = FRAME_SIZE * NUM_CHANNELS * 2;


    private byte[] arrayForDecode = new byte[READ_BYTES_FROM_MIC];
    private OpusStorage opusStorage;

    private AudioFormat format;
    private SourceDataLine player;
    private ConcurrentLinkedQueue<byte[]> soundQueue;
    private boolean isNeedToPlay;
    private Mixer.Info mixer;
    private Thread playingThread;

    public OpusOutputStream (){
        super();
        init(SimpleAudioSystem.getMixer_info());
    }

    private void init(Mixer.Info mixer){
        this.mixer = mixer;
        try {
            format = new AudioFormat(encoding, SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, NUM_CHANNELS, SAMPLE_SIZE_IN_BITS / 8, SAMPLE_RATE, BIG_ENDIAN);
        } catch (IllegalArgumentException e) {
            System.out.println("OpusOS:46 New FORMAT exception. " + e.getMessage());
        }
        String name = System.getProperty("os.name").toLowerCase();
        System.out.println(name);
        opusStorage = new OpusStorage();
        soundQueue = new ConcurrentLinkedQueue<>();
    }

    public void start() {
        isNeedToPlay = true;
        playingThread = new Thread(() -> {
            System.out.println("========= OPUS SPEAK DECODER THREAD STARTED " + Thread.currentThread().getName());
            try{
                player = AudioSystem.getSourceDataLine(format, mixer);
                try{
                    player.open(format);
                } catch (IllegalStateException e){
                    System.out.println("==========OpusOS:63  First run. player.open(format); Error");
                    try{
                        System.out.println("========OpusOS:65 Thy REOPEN!!!");
                        player = AudioSystem.getSourceDataLine(format, mixer);
                        player.open(format);
                    } catch (IllegalStateException er){
                        System.out.println("========OpusOS:68 Thy REOPEN!!! ERROR==============");
                    }
                }
                player.start();
                opusStorage.getDecoder().init(SoundRecorder.SAMPLE_RATE, SoundRecorder.NUM_CHANNELS);
                while (isNeedToPlay/* && soundQueue.size() > 0*/) {
                    if (soundQueue.size() > 0){
                        byte[] array = soundQueue.poll();
                        if (arrayForDecode != null && array != null) {
                            int count = opusStorage.getDecoder().decode(array, arrayForDecode, FRAME_SIZE);
                            if (player == null) {
                                try{
                                    System.out.println("Try reinit opus output");
                                    player.open(format);
                                    System.out.println("Try reinit opus output SUCCESS");
                                } catch (IllegalStateException e){
                                    System.out.println("Try reinit opus output ERROR");
                                }
                                Thread.sleep(100);
                            }else{
                                player.write(arrayForDecode, 0, arrayForDecode.length);
                            }
//                            System.out.println("Opus Decoder decoded: " + count);
                        }
                    }
                    Thread.sleep(1);
                }
                opusStorage.getDecoder().close();
                player.drain();
                player.close();
                isNeedToPlay = false;
            } catch (LineUnavailableException | InterruptedException e) {
                e.printStackTrace();
                player.drain();
                player.close();
                opusStorage.getDecoder().close();
                isNeedToPlay = false;
            }
            System.out.println("========= OPUS SPEAK DECODER THREAD ENDED " + Thread.currentThread().getName());
        });
        playingThread.start();
    }

    public void close() {
        System.out.println("========= OPUS SPEAK DECODER THREAD CLOSING... " + Thread.currentThread().getName());
        isNeedToPlay = false;
        soundQueue.clear();
        playingThread.interrupt();
    }

    public void write(byte[] data) {
        soundQueue.add(data);
    }

    public void flush(){}
    public void write(int value){}
    public void write(byte[] data, int off, int len){
        byte[] buff = new byte[len];
        for(int i = 0; i < len; i++){
            buff[i] = data[off + i];
        }
        soundQueue.add(buff);
    }
}
