package org.mjsip.custom.opus;

import javax.sound.sampled.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SoundRecorder {

    public static final int SAMPLE_RATE = 48000;
    public static final int NUM_CHANNELS = 1;
    public static final int SAMPLE_SIZE_IN_BITS = 16;
    public static final boolean BIG_ENDIAN = false;
    //    public static final boolean SIGNED = true;
    public static final AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_SIGNED;
    // Number of samples per frame is not arbitrary,
    // it must match one of the predefined values, specified in the standard.
    public static final int FRAME_SIZE = 960;
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


    public SoundRecorder(Mixer.Info mixer) {
        this.mixer = mixer;
        try {
            format = new AudioFormat(encoding, SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, NUM_CHANNELS, SAMPLE_SIZE_IN_BITS / 8, SAMPLE_RATE, BIG_ENDIAN);
//            format = new AudioFormat(encoding, SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, NUM_CHANNELS, NUM_CHANNELS, SAMPLE_RATE, BIG_ENDIAN);
            info = new DataLine.Info(TargetDataLine.class, format);
            infoForPlay = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("Target Line not supported");
            }
            if (!AudioSystem.isLineSupported(infoForPlay)) {
                System.out.println("Source Line not supported");
            }
            line = AudioSystem.getTargetDataLine(format, mixer);
//            player = AudioSystem.getSourceDataLine(format, mixer);
        } catch (LineUnavailableException | IllegalArgumentException e) {
            e.printStackTrace();
        }
        String name = System.getProperty("os.name").toLowerCase();
        System.out.println(name);
        opusStorage = new OpusStorage();
        soundQueue = new ConcurrentLinkedQueue<>();
    }

    public void startRecording() {
        isRecording = true;
        isAllMsgRecorded = false;
        try {
            line.open(format);
            line.start();
            recordingThread = new Thread(() -> {
                int numOfBytesRead;
                byte[] arrayToRemmember = new byte[line.getBufferSize() / 5];
                opusStorage.getEncoder().init(SoundRecorder.SAMPLE_RATE, SoundRecorder.NUM_CHANNELS, OpusEncoder.OPUS_APPLICATION_VOIP);
                while (isRecording) {
                    numOfBytesRead = line.read(arrayToRemmember, 0, READ_BYTES_FROM_MIC);
                    if (arrayToRemmember != null && arrayForEncode != null) {
                        int encoded = opusStorage.getEncoder().encode(arrayToRemmember, FRAME_SIZE, arrayForEncode);
                        soundQueue.add(Arrays.copyOf(arrayForEncode, encoded));
                        System.err.println(encoded);
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
            }, "AudioRecorder Thread");
            recordingThread.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    public void stopPlaying() {
        isNeedToPlay = false;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void stopRecording() {
        isRecording = false;
    }

    public void startPlaying() {
        isNeedToPlay = true;
        if (recordingThread != null)
            recordingThread.interrupt();
        try {
            player = AudioSystem.getSourceDataLine(format, mixer);
            player.open(format);
            player.start();
            opusStorage.getDecoder().init(SoundRecorder.SAMPLE_RATE, SoundRecorder.NUM_CHANNELS);
            while (isNeedToPlay && soundQueue.size() > 0) {
                byte[] array = soundQueue.poll();
                if (arrayForDecode != null && array != null) {
                    int count = opusStorage.getDecoder().decode(array, arrayForDecode, FRAME_SIZE);
                    player.write(arrayForDecode, 0, arrayForDecode.length);
                } else {
//                    soundQueue.clear();
//                    player.stop();
//                    break;
                }
                Thread.sleep(15);
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
    }

    public void stopPlayLog() {
        player.stop();
        player.close();
    }
}

