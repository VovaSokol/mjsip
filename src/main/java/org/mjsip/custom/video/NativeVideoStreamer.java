package org.mjsip.custom.video;



import org.mjsip.custom.transport.UDPInputStream;
import org.mjsip.custom.transport.UDPOutputStream;
import org.mjsip.media.*;
import org.mjsip.rtp.RtpControl;
import org.mjsip.ua.UserAgent;
import org.zoolu.net.SocketAddress;
import org.zoolu.net.UdpSocket;
import org.zoolu.sound.SimpleAudioSystem;
import org.zoolu.util.Logger;

import java.io.InputStream;
import java.io.OutputStream;

import static org.mjsip.media.AudioStreamer.*;


/** Media streamer based on a native command-line application.
  */
public class NativeVideoStreamer implements MediaStreamer, RtpStreamSenderListener, RtpStreamReceiverListener {

	/** Logger */
	Logger logger=null;

	/** Runtime media process (native media application) */
	Process media_process=null;

	int local_port;
	int remote_port;
	String remote_addr;

	/** Media application command */
	FlowSpec video_flow;

	/** Command-line arguments */
	String[] args;

	//video settings
	String codec_name = "h264";
	int payload_type = 97;
	int sample_rate = 90000;


    /** Stream direction */
    FlowSpec.Direction dir;

    /** UDP socket */
    UdpSocket udp_socket=null;

    /** RtpStreamSender */
    protected RtpStreamSender rtp_sender=null;

    /** RtpStreamReceiver */
    protected RtpStreamReceiver rtp_receiver=null;

    /** Whether using system audio capture */
    boolean video_input=false;

    /** Whether using system audio playout */
    boolean video_output=false;

    /** RTCP */
    RtpControl rtp_control=null;


	/** Creates a new media streamer.
	  * @param video_flow the command-line program to be run
	  * @param args command-line arguments that have to be passed to the program
	  * @param logger the logger where running information are logged */
	public NativeVideoStreamer(FlowSpec video_flow, String[] args, Logger logger) {
		init(video_flow,args,0,0,logger);
	}

	/** Creates a new media streamer.
	  * @param video_flow the command-line program to be run
	  * @param args command-line arguments that have to be passed to the program
	  * @param local_port org.local media port
	  * @param remote_port remote media port
	  * @param logger the logger where running information are logged */
	public NativeVideoStreamer(FlowSpec video_flow, String[] args, int local_port, int remote_port, Logger logger) {
		init(video_flow,args,local_port,remote_port,logger);
	}

	/** Inits the media streamer.
	  * @param video_flow the command-line program to be run
	  * @param args command-line arguments that have to be passed to the program
	  * @param local_port org.local media port
	  * @param remote_port remote media port
	  * @param logger the logger where running information are logged */
	private void init(FlowSpec video_flow, String[] args, int local_port, int remote_port, Logger logger) {


        RtpStreamReceiver.DEBUG = true;
        RtpStreamSender.DEBUG = true;

		this.logger=logger;
		this.remote_addr=video_flow.getRemoteAddress();
		this.video_flow=video_flow;
		this.args=args;
		this.local_port=local_port;
		this.remote_port=remote_port;
	}
	private TestFFmpeg testFFmpeg;

	public boolean start1(){
        System.out.println("starting java video");
        if (rtp_sender!=null) {
            System.out.println("start sending");
//            if (video_input) SimpleAudioSystem.startAudioInputLine();
            rtp_sender.start();
        }
        if (rtp_receiver!=null) {
            System.out.println("start receiving");
//            if (video_output) SimpleAudioSystem.startAudioOutputLine();
            rtp_receiver.start();
        }
        return true;
    }

	/** Starts this media streams. */
	public boolean start() {
		// udp flow adaptation for media streamer
		if (local_port!=remote_port)  {
			log("UDP org.local relay: src_port="+local_port+", dest_port="+remote_port);
			log("UDP org.local relay: src_port="+UserAgent.videoLocalPort+", dest_port="+UserAgent.videoRemotePort);
			log("UDP org.local relay: src_port="+(UserAgent.audioLocalPort)+", dest_port="+(UserAgent.audioRemotePort));
//            new UdpRelay(UserAgent.videoLocalPort,remote_addr,UserAgent.videoRemotePort,null);
//            new UdpRelay(local_port+1,"127.0.0.1",remote_port+1,null);
        }
		else {
			log("local_port==remote_port --> no UDP relay is needed");
		}

		// try to start the media application
		try {
			testFFmpeg = new TestFFmpeg();
			testFFmpeg.startAndWait(args);
			return true;
		}
		catch (Exception e) {
			e.printStackTrace();
			return false;
		}          
	}

    /** Stops media streams. */
    public boolean halt1() {
        System.out.println("stopping java audio");
        if (rtp_sender!=null) {
            rtp_sender.halt();
            rtp_sender=null;
            System.out.println("sender halted");
        }
        if (video_input) SimpleAudioSystem.stopAudioInputLine();

        if (rtp_receiver!=null) {
            rtp_receiver.halt();
            rtp_receiver=null;
            System.out.println("receiver halted");
        }
        if (video_output) SimpleAudioSystem.stopAudioOutputLine();

        // try to take into account the resilience of RtpStreamSender
        try { Thread.sleep(RtpStreamReceiver.SO_TIMEOUT); } catch (Exception e) {}
        udp_socket.close();
        if (rtp_control!=null) rtp_control.halt();
        return true;
    }

	/** Stops this media streams. */
	public boolean halt() {
//		if (media_process!=null) media_process.destroy();
		testFFmpeg.stop();
		return true;
	}


	// ****************************** Logs *****************************

	/** Adds a new string to the default Log.
	  * @param str the string message to be logged. */
	private void log(String str) {
		if (logger!=null) System.out.println("NativeMediaApp: "+str);
		System.out.println("NativeMediaApp: "+str);
	}

    @Override
    public void onRemoteSoAddressChanged(RtpStreamReceiver rr, SocketAddress remote_soaddr) {
        try {
            if (true && rtp_sender!=null) rtp_sender.setRemoteSoAddress(remote_soaddr);
        }
        catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void onRtpStreamReceiverTerminated(RtpStreamReceiver rr, Exception error) {
        if (error!=null) error.printStackTrace();
    }

    @Override
    public void onRtpStreamSenderTerminated(RtpStreamSender rs, Exception error) {
        if (error!=null) error.printStackTrace();
    }
}
