package org.mjsip.custom.video;

import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffmpeg.*;
import com.github.kokorin.jaffree.ffprobe.FFprobe;
import org.mjsip.custom.opus.NativeUtils;
import org.mjsip.ua.UserAgent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;

/**
 *
 * @author dyorgio
 */
public class TestFFmpeg {
    public static int videoWidth = 480;
    public static int videoHeight = 640;
    public static int videoFps = 25;

    static {
        String arch;
        if (System.getProperty("os.arch").equals("arm")) {
            arch = "arm";
        } else {
            arch = "x86";
        }

        System.out.println("SYSTEM: " + arch);

        try{
            NativeUtils.loadLibraryFromJar("/lib/" + arch, new String[] {"videostreamer_jni"});
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public static void main(String[] args) throws IOException {
        TestFFmpeg test = new TestFFmpeg(1280, 720, 25);
        // Mac
//        test.startAndWait("h264_videotoolbox");

        // Others
//         test.startAndWait(null, true);
    }

    private final int width;
    private final int height;
    private final int framerate;
    private final float framePts;
    private  FFmpeg ffmpeg;
    private  FFmpeg ffmpeg1;
    private  FFprobe fFprobe;
    private  Thread thread;
    private Process process;

    public TestFFmpeg(int width, int height, int framerate) {
        this.width = width;
        this.height = height;
        this.framerate = framerate;
        this.framePts = (1000f / framerate) * 90f;
    }

    private native int startVideoStreaming(String host, int port, int height, int width, int fps);
    private native int stopVideoStreaming();

    public void startAndWait(String[] host_addrs) throws IOException {
        //example
        //ffmpeg -f v4l2 -i /dev/video0 -f alsa -i hw:CARD=PCH,DEV=0 -profile:v high -pix_fmt yuv420p -level:v 4.1 -preset ultrafast -tune zerolatency -vcodec libopenh264 -r 10 -b:v 512k -s 1280x720 -acodec aac -strict -2 -ac 2 -ab 32k -ar 44100 -f mpegts -flush_packets 0 udp://192.168.0.104:20234

        //rtp
        //ffmpeg -f v4l2 -i /dev/video0 -profile:v high -pix_fmt yuvj420p
        // -level:v 4.1 -preset ultrafast  -tune zerolatency -vcodec libvpx
        // -r 30 -b:v 512k  -s 1280x720  -vn -f rtp rtp://127.0.0.1:20234
        // -f alsa -i hw:CARD=PCH,DEV=0 -acodec opus -strict -2 -ac 2 -ab 32k -an
        // -f rtp rtp://127.0.0.1:20235 -protocol_whitelist "file,http,https,tcp,tls,crypto,rtp"

        ffmpeg = FFmpeg.atPath(Paths.get("/usr/local/bin/"))//
//                    .addArgument("-re") //linux video
                .addArguments("-protocol_whitelist","file,http,https,tcp,tls,crypto,rtp")
                .addArguments("-f","video4linux2")
                .addArguments("-i","/dev/video0")
//                .addArguments("-f","alsa")
//                .addArguments("-i","hw:CARD=PCH,DEV=0") //vova dell e6440
//                .addArguments("-i","plughw:CARD=Device,DEV=0") //rpi3
                .addOutput(//
                        UrlOutput.toUrl("rtp://" + host_addrs[0] + "?localrtpport=" + UserAgent.videoLocalPort)//
                                .addArgument("-an")
//                                .setPixelFormat("yuv420p")// vova dell e6440
//                                .setPixelFormat("YU12")// rpi3
                                .addArguments("-payload_type","96")
//                                .addArguments("-r","10")
//                                .addArguments("-profile:v","baseline") //video cam
//                                .addArguments("-level:v","4.1") //video cam
//                                .addArguments("-preset","ultrafast") //video cam
                                .addArguments("-tune","zerolatency") //video cam
//                                .addArguments("-filter:v","crop=850:570:200:150") //video cam
//                                .addArguments("-tune","film") //video cam
//                                .addArguments("-crf", "51")
//                                .addArguments("-b:v", "800K")
//                                .addArguments("-r", "30")
//                                .addArguments("-bufsize", "1536K")
                                .addArguments("-s","480x640") //video cam
//                                .addArguments("-s","300x400") //video cam
//                                .addArguments("-s","720x1280") //video cam
                                .setCodec(StreamType.VIDEO, "libopenh264")//
//                                .addArguments("-sdp_file","video.sdp")
                                .setFormat("rtp")//
                )
                /*.addOutput(
                        UrlOutput.toUrl("rtp://" + host_addrs[1])//
                                .addArgument("-vn")
                                .addArguments("-strict","-2") //audio "aplay -L | grep :CARD"
                                .addArguments("-ac","2") //audio "aplay -L | grep :CARD"
                                .addArguments("-ab","64k") //audio "aplay -L | grep :CARD"
                                .addArguments("-ar","48000") //audio "aplay -L | grep :CARD"
                                .setCodec(StreamType.AUDIO, "opus")//
//                                .addArguments("-sdp_file","audio.sdp")
                                .setFormat("rtp")//
                )*/
                /*.addOutput( // pcmu
                        UrlOutput.toUrl("rtp://" + host_addrs[1] + "?localrtpport=" + UserAgent.audioLocalPort)//
                                .addArgument("-vn")
//                                .addArguments("-strict","-2") //audio "aplay -L | grep :CARD"
                                .addArguments("-ac","1") //audio "aplay -L | grep :CARD"
                                .addArguments("-ab","128") //audio "aplay -L | grep :CARD"
                                .addArguments("-ar","8000") //audio "aplay -L | grep :CARD"
                                .setCodec(StreamType.AUDIO, "pcm_mulaw")//
//                                .addArguments("-sdp_file","audio.sdp")
                                .setFormat("rtp")//
                )*/;
//        ffmpeg.execute();

        /*try{
            System.out.println("Address: " + host_addrs[2] + " port: " + host_addrs[3]);
            process = Runtime.getRuntime().exec(String.format("/usr/bin/raspivid -fps 30 -h 1280 -w 720 -vf -n -t 0 -b 2000000 -o - | /usr/bin/gst-launch-1.0 -v fdsrc ! h264parse ! rtph264pay config-interval=1 pt=96 ! gdppay ! tcpserversink port=" + host_addrs[3]));
        }catch (IOException e){
            System.out.println("Cannot run video! " + e.getMessage());
        }

        thread = new Thread(() -> {
            try {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;
                System.out.println("Process output: START");
                while ((line = bufferedReader.readLine()) != null) {
                    System.out.println("Process output: " + line);
                }
                System.out.println("Process output: END");
                process.getInputStream().close();
                bufferedReader.close();
            }catch (IOException e){
                System.out.println("Cannot read from process. IOException!");
            }
        });*/
//        thread.start();
//        host_addrs[3] = "0"; //if 0 - HARDCODE IP
        System.out.println("===================== RTP DATA ===========================");
        for (String host_addr : host_addrs) {
            System.out.println(host_addr);
        }
        thread = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {

            }
            this.startVideoStreaming(host_addrs[2], Integer.parseInt(host_addrs[3]), videoHeight, videoWidth, videoFps);
        });
        thread.start();
        System.out.println("===================== STREAM START ===========================");
    }
    public void stop(){
        this.stopVideoStreaming();
        thread.interrupt();
        System.out.println("===================== STREAM STOP ===========================");
//        process.destroy();
    }
}
