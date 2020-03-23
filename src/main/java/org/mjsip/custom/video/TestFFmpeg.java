package org.mjsip.custom.video;

import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffmpeg.*;
import org.mjsip.ua.UserAgent;

import java.io.IOException;
import java.nio.file.Paths;

/**
 *
 * @author dyorgio
 */
public class TestFFmpeg {
    public static int videoWidth = 480;
    public static int videoHeight = 640;
    public static int videoFps = 25;
    public static String videoBitrateK = "640K";
    private  FFmpeg ffmpeg;
    private  Thread thread;
    private  Process process;

    public void startAndWait(String[] host_addrs) throws IOException {
        process = Runtime.getRuntime().exec(String.format("/usr/bin/v4l2-ctl -v width=" + TestFFmpeg.videoWidth +
                                                          ",height=" + TestFFmpeg.videoHeight +
                                                          ",pixelformat=yuv420p"));

        String videoCodec = "libx264";
        if(System.getProperty("os.arch").toLowerCase().equals("arm")){
            videoCodec = "h264_omx";
        }

        ffmpeg = FFmpeg.atPath(Paths.get("/usr/bin/"))//
                .addArguments("-f","video4linux2")
                .addArguments("-i","/dev/video0")
                .addOutput(//
                        UrlOutput.toUrl("rtp://" + host_addrs[0] + "?localrtpport=" + UserAgent.videoLocalPort)//
                                .addArgument("-an")
                                .setPixelFormat("yuv420p")// vova dell e6440
                                .addArguments("-payload_type","96")
                                .addArguments("-b:v", TestFFmpeg.videoBitrateK)
                                .addArguments("-keyint_min", "0")
                                .addArguments("-g", "30")
                                .addArguments("-r", String.valueOf(TestFFmpeg.videoFps))
                                .addArguments("-s", TestFFmpeg.videoWidth + "x" + TestFFmpeg.videoHeight) //video cam
                                .setCodec(StreamType.VIDEO, videoCodec)//
                                .setFormat("rtp")//
                );
        System.out.println(ffmpeg.toString());
        System.out.println("===================== RTP DATA ===========================");
        for (String host_addr : host_addrs) {
            System.out.println(host_addr);
        }
        thread = new Thread(() -> {
            ffmpeg.execute();
        });
        thread.start();
        System.out.println("===================== STREAM START ===========================");
    }
    public void stop(){
        process.destroy();
        thread.interrupt();
        System.out.println("===================== STREAM STOP ===========================");
    }
}
