/*
 * Copyright (C) 2013 Luca Veltri - University of Parma - Italy
 * 
 * This source code is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * Author(s):
 * Luca Veltri (luca.veltri@unipr.it)
 */

package org.mjsip.custom.opus;


import org.mjsip.media.*;
import org.mjsip.rtp.AmrRtpPayloadFormat;
import org.mjsip.rtp.RtpControl;
import org.zoolu.net.SocketAddress;
import org.zoolu.net.UdpSocket;
import org.zoolu.sound.AudioOutputStream;
import org.zoolu.sound.CodecType;
import org.zoolu.sound.SimpleAudioSystem;
import org.zoolu.util.*;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.io.OutputStream;


/** Full-duplex audio streamer based on javax.sound.
  */
public class OpusAudioStreamer implements MediaStreamer, RtpStreamSenderListener, RtpStreamReceiverListener {

	/** Verbose debug mode */
	public static boolean VERBOSE_DEBUG=false;

	/** Internal buffer size. */
	public static final int INTERNAL_BUFFER_SIZE=40960;

	/** Whether using RTP payload format in Bandwidth-Efficient Mode */
	public static boolean RTP_BANDWIDTH_EFFICIENT_MODE=false;

	/** Whether using symmetric RTP by default */
	public static final boolean DEFAULT_SYMMETRIC_RTP=false;

	/** Whether discarding out-of-sequence and duplicated packets */
	public static boolean SEQUENCE_CHECK=false;

	/** Whether filling silence intervals with (silence-equivalent) void data */
	public static boolean SILENCE_PADDING=false;

	/** Unknown payload type */
	public static final int UNKNOWN_PAYLOAD_TYPE=97;


	/** Default codec */
	public static final String DEFAULT_CODEC_NAME="OPUS";

	/** Default payload type */
	public static final int DEFAULT_PAYLOAD_TYPE=97;

	/** Default sample rate [sample/sec] */
	public static final int DEFAULT_SAMPLE_RATE=48000;

	/** Default number of audio channels (1 for mono, 2 for stereo) */
	public static final int DEFAULT_CHANNELS=1;

	/** Default codec frame size [byte] */
	public static final int DEFAULT_FRAME_SIZE=960;

	/** Default codec frame rate [frame/sec] */
	public static final int DEFAULT_FRAME_RATE=48000;

	/** Default packet size [byte] */
	//public static final int DEFAULT_PACKET_SIZE=160;

	/** Default packet rate [pkt/sec] */
	//public static final int DEFAULT_PACKET_RATE=50;

	/** Default inter-packet time [millisecs] */
	public static final int DEFAULT_PACKET_TIME=20;

	/** By default whether using big-endian rule for byte ordering */
	public static final boolean DEFAULT_BIG_ENDIAN=false;


	/** Whether using symmetric_rtp */
	boolean symmetric_rtp=DEFAULT_SYMMETRIC_RTP;

	/** Logger */
	Logger logger=null;

	/** Audio format */
	//AudioFormat audio_format;

	/** Stream direction */
	FlowSpec.Direction dir;

	/** UDP socket */
	UdpSocket udp_socket=null;

	/** RtpStreamSender */
	protected RtpStreamSenderOpus rtp_sender=null;

	/** RtpStreamReceiver */
	protected RtpStreamReceiverOpus rtp_receiver=null;

	/** Whether using system audio capture */
	boolean audio_input=false;

	/** Whether using system audio playout */
	boolean audio_output=false;

	/** RTCP */
	RtpControl rtp_control=null;

	private OpusOutputStream audio_output_stream;
	private OpusInputStream audio_input_stream;


	/** Creates a new audio streamer. */
	public OpusAudioStreamer(RtpStreamSenderOpus rtp_sender, RtpStreamReceiverOpus rtp_receiver, boolean symmetric_rtp, Logger logger) {
		this.logger=logger;
		this.rtp_sender=rtp_sender;
		this.rtp_receiver=rtp_receiver;
		this.symmetric_rtp=symmetric_rtp;
		log(LogLevel.DEBUG,"codec: [unknown]");
	}


	/** Creates a new audio streamer. */
	/*public AudioStreamer(int local_port, String remote_addr, int remote_port, MediaApp.MediaDirection direction, Logger logger) {
		init(local_port,remote_addr,remote_port,direction,null,null,null,-1,0,0,true,symmetric_rtp,logger);
	}*/


	/** Creates a new audio streamer.
	  * @param flow_spec the flow specification
	  * @param direct_convertion whether using explicit external converter (i.e. direct access to an external conversion provider)
	  *    instead of that provided by javax.sound.sampled.spi.
	  *    It applies only when javax sound is used, that is when no other audio streamers (such as jmf or rat) are used
	  * @param additional_encoding additional audio encoder/decoder (optional)
	  * @param do_sync whether enforcing time synchronization to RTP source stream.
	  *    If synchronization is explicitly performed, the departure time of each RTP packet is equal to its nominal time.
	  *    Note that when using audio capturing, synchronization with the sample rate
	  *    is implicitly performed by the audio capture device and frames are read at constant bit rate.
	  *    However, an explicit re-synchronization is suggested
	  *    in order to let the read() method be non-blocking (in the other case
	  *    the UA audio performance seems decreasing
	  * @param random_early_drop receiver random early drop (RED) rate.
	  *    Actually it is the inverse of packet drop rate.
	  *    It can used to prevent long play back delay.
	  *    A value less or equal to 0 means that no packet dropping is explicitly
	  *    performed at the RTP receiver
	  * @param symmetric_rtp whether using symmetric_rtp
	  * @param logger a logger for recording log messages (or <i>null</i>) */
	public OpusAudioStreamer(FlowSpec flow_spec, String audiofile_in, String audiofile_out, boolean direct_convertion, Codec additional_encoding, boolean do_sync, int random_early_drop, boolean symmetric_rtp, Logger logger) {
		MediaSpec audio_spec=flow_spec.getMediaSpec();
		System.out.println("DEBUG: "+SystemUtils.getClassSimpleName(OpusAudioStreamer.class.getName())+": audio_spec: "+audio_spec.toString());
		init(flow_spec.getLocalPort(),flow_spec.getRemoteAddress(),flow_spec.getRemotePort(),flow_spec.getDirection(),audiofile_in,audiofile_out,audio_spec.getCodec(),audio_spec.getAVP(),audio_spec.getSampleRate(),audio_spec.getChannels(),audio_spec.getPacketSize(),direct_convertion,additional_encoding,do_sync,random_early_drop,symmetric_rtp,false,logger);
	}


	/** Creates a new audio streamer.
	  * @param flow_spec the flow specification
	  * @param direct_convertion whether using explicit external converter (i.e. direct access to an external conversion provider)
	  *    instead of that provided by javax.sound.sampled.spi.
	  *    It applies only when javax sound is used, that is when no other audio streamers (such as jmf or rat) are used
	  * @param additional_encoding additional audio encoder/decoder (optional)
	  * @param do_sync whether enforcing time synchronization to RTP source stream.
	  *    If synchronization is explicitly performed, the departure time of each RTP packet is equal to its nominal time.
	  *    Note that when using audio capturing, synchronization with the sample rate
	  *    is implicitly performed by the audio capture device and frames are read at constant bit rate.
	  *    However, an explicit re-synchronization is suggested
	  *    in order to let the read() method be non-blocking (in the other case
	  *    the UA audio performance seems decreasing
	  * @param random_early_drop receiver random early drop (RED) rate.
	  *    Actually it is the inverse of packet drop rate.
	  *    It can used to prevent long play back delay.
	  *    A value less or equal to 0 means that no packet dropping is explicitly
	  *    performed at the RTP receiver
	  * @param symmetric_rtp whether using symmetric_rtp
	  * @param rtcp whether using rtcp
	  * @param logger a logger for recording log messages (or <i>null</i>) */
	public OpusAudioStreamer(FlowSpec flow_spec, String audiofile_in, String audiofile_out, boolean direct_convertion, Codec additional_encoding, boolean do_sync, int random_early_drop, boolean symmetric_rtp, boolean rtcp, Logger logger) {
		MediaSpec audio_spec=flow_spec.getMediaSpec();
		System.out.println("DEBUG: "+SystemUtils.getClassSimpleName(OpusAudioStreamer.class.getName())+": audio_spec: "+audio_spec.toString());
		init(flow_spec.getLocalPort(),flow_spec.getRemoteAddress(),flow_spec.getRemotePort(),flow_spec.getDirection(),audiofile_in,audiofile_out,audio_spec.getCodec(),audio_spec.getAVP(),audio_spec.getSampleRate(),audio_spec.getChannels(),audio_spec.getPacketSize(),direct_convertion,additional_encoding,do_sync,random_early_drop,symmetric_rtp,rtcp,logger);
	}


	/** Initializes the audio streamer. */
	private void init(int local_port, String remote_addr, int remote_port, FlowSpec.Direction direction, String audiofile_in, String audiofile_out, String codec_name, int payload_type, int sample_rate, int channels, int packet_size, boolean direct_convertion, Codec additional_encoding, boolean do_sync, int random_early_drop, boolean symmetric_rtp, boolean rtcp, Logger logger) {
		if (VERBOSE_DEBUG) println("X-DEBUG: "+SystemUtils.getClassSimpleName(OpusAudioStreamer.class.getName())+" initalization");
//		SimpleAudioSystem.initMixerAndLines(DEFAULT_SAMPLE_RATE,DEFAULT_CHANNELS);
		this.logger=logger;
		this.dir=direction;
		this.symmetric_rtp=symmetric_rtp;
		// 1) in case not defined, use default values
		if (codec_name==null) codec_name=DEFAULT_CODEC_NAME;
		//if (payload_type<0) payload_type=DEFAULT_PAYLOAD_TYPE;
		if (sample_rate<=0) sample_rate=DEFAULT_SAMPLE_RATE;
		//if (packet_size<=0) packet_size=DEFAULT_PACKET_SIZE;
		
		// 2) codec name translation
		codec_name=codec_name.toUpperCase();
		log(LogLevel.DEBUG,"codec: "+codec_name);

		// 3) payload_type, frame_size, frame_rate, packet_size, packet_time
		int frame_size = DEFAULT_FRAME_SIZE;
		int frame_rate = DEFAULT_FRAME_RATE;
		 payload_type = DEFAULT_PAYLOAD_TYPE;
		//int packet_rate=(packet_size>0)? frame_rate*frame_size/packet_size : DEFAULT_PACKET_RATE;
		long packet_time = DEFAULT_PACKET_TIME;
//		if (packet_size<=0) packet_size=(int)(frame_rate*frame_size*DEFAULT_PACKET_TIME/1000);
		packet_size=97;
		log(LogLevel.DEBUG,"packet size: "+packet_size+ "B");
		log(LogLevel.DEBUG,"packet time: "+packet_time+ "ms");
		log(LogLevel.DEBUG,"packet rate: "+(1000/packet_time)+ "pkt/s");
	
		// 4) find the proper supported AudioFormat
		log(LogLevel.DEBUG,"base audio format: "+SimpleAudioSystem.getBaseAudioFormat(sample_rate,channels).toString());
		AudioFormat audio_format=null;
		AudioFormat.Encoding encoding=null;
		// get the proper audio format encoding

		Encoder additional_encoder=null;
		Encoder additional_decoder=null;
		
		System.out.println("DEBUG: "+getClass().getSimpleName()+": sample rate: "+sample_rate+ "Hz");
		System.out.println("DEBUG: "+getClass().getSimpleName()+": packet size: "+packet_size+ "B");
		System.out.println("DEBUG: "+getClass().getSimpleName()+": packet time: "+packet_time+ "ms");
		System.out.println("DEBUG: "+getClass().getSimpleName()+": packet rate: "+(1000/packet_time)+ "pkt/s");
		System.out.println("DEBUG: "+getClass().getSimpleName()+": audio format: "+audio_format);

		try {
			// 5) udp socket
			udp_socket=new UdpSocket(local_port);
			
			// 6) sender
			audio_input_stream= new OpusInputStream();
			audio_output_stream = new OpusOutputStream();
			if ((dir==FlowSpec.SEND_ONLY || dir==FlowSpec.FULL_DUPLEX)) {
				log(LogLevel.DEBUG,"new audio sender to "+remote_addr+":"+remote_port);
				// javax sound
				// use standard java embedded conversion provider
				// sender
				rtp_sender=new RtpStreamSenderOpus(audio_output_stream, audio_input_stream,do_sync,payload_type,sample_rate,channels,packet_time,packet_size,additional_encoder,udp_socket,remote_addr,remote_port,this);
				//if (sync_adj>0) sender.setSyncAdj(sync_adj);
				audio_input=true;
			}
			
			// 7) receiver
			if (dir==FlowSpec.RECV_ONLY || dir==FlowSpec.FULL_DUPLEX) {
				// receiver
				rtp_receiver=new RtpStreamReceiverOpus(audio_output_stream,additional_decoder,udp_socket,this);
				if (random_early_drop>0) rtp_receiver.setRED(random_early_drop);
				audio_output=true;
			}
			// RTCP
			if (rtcp) {
				rtp_control=new RtpControl(null,udp_socket.getLocalPort()+1,remote_addr,remote_port+1);
				if (rtp_sender!=null) rtp_sender.setControl(rtp_control);
			}
			// SEQUENCE CHECK
			if (rtp_receiver!=null) rtp_receiver.setSequenceCheck(SEQUENCE_CHECK);
			
			// SILENCE PADDING
			if (rtp_receiver!=null) rtp_receiver.setSilencePadding(SILENCE_PADDING);
		}
		catch (Exception e) {  log(LogLevel.INFO,e);  }
		if (VERBOSE_DEBUG) println("DEBUG: Frame rate: "+frame_rate+" frame/s");
		if (VERBOSE_DEBUG) println("DEBUG: Frame size: "+frame_size+" B");
		if (VERBOSE_DEBUG) println("DEBUG: Packet time: "+packet_time+" ms");
		if (VERBOSE_DEBUG) println("DEBUG: Packet rate: "+(1000/packet_time)+" pkt/s");
		if (VERBOSE_DEBUG) println("DEBUG: Packet size: "+packet_size+" B");
		if (VERBOSE_DEBUG) if (random_early_drop>0) println("DEBUG: Random early drop at receiver: 1 packet out of "+random_early_drop);
	}


	/** Starts media streams. */
	public boolean start() {
        /*try {
            Runtime.getRuntime().exec(String.format("/usr/sbin/i2cset -y 1 0x6c 0x03 0x00"));
            Thread.sleep(20);
			System.out.println("=============MAX==============");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }*/
		log(LogLevel.INFO,"starting java audio");
		if (rtp_receiver!=null) {
			log(LogLevel.DEBUG,"start receiving");
//			if (audio_output) SimpleAudioSystem.startAudioOutputLine();
			audio_output_stream.start();
			rtp_receiver.start();
		}
		if (rtp_sender!=null) {
			log(LogLevel.DEBUG,"start sending");
//			if (audio_input) SimpleAudioSystem.startAudioInputLine();
			audio_input_stream.start();
			rtp_sender.start();
		}
		return true;
	}


	/** Stops media streams. */
	public boolean halt() {
		log(LogLevel.INFO,"stopping java audio");    
		if (rtp_sender!=null) {
			rtp_sender.halt();
			audio_input_stream.close();
			rtp_sender=null;
			log(LogLevel.DEBUG,"sender halted");
		}      
//		if (audio_input) SimpleAudioSystem.stopAudioInputLine();
	 
		if (rtp_receiver!=null) {
			audio_output_stream.close();
			rtp_receiver.halt();
			rtp_receiver=null;
			log(LogLevel.DEBUG,"receiver halted");
		}      
//		if (audio_output) SimpleAudioSystem.stopAudioOutputLine();

		// try to take into account the resilience of RtpStreamSender
		try { Thread.sleep(RtpStreamReceiver.SO_TIMEOUT); } catch (Exception e) {}
		udp_socket.close();
		if (rtp_control!=null) rtp_control.halt();
		/*try {
			Runtime.getRuntime().exec(String.format("/usr/sbin/i2cset -y 1 0x6c 0x03 0x00"));
			Thread.sleep(20);
			System.out.println("=============MIN==============");
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}*/
		return true;
	}
	
	
	/** Sets symmetric RTP mode. */
	public void setSymmetricRtp(boolean symmetric_rtp) {
		this.symmetric_rtp=symmetric_rtp;
	}


	/** whether symmetric RTP mode is set. */
	public boolean isSymmetricRtp() {
		return symmetric_rtp;
	}


	/** From RtpStreamReceiverListener. When the remote socket address (source) is changed. */
	public void onRemoteSoAddressChanged(RtpStreamReceiver rr, SocketAddress remote_soaddr) {
		try {
			if (symmetric_rtp && rtp_sender!=null) rtp_sender.setRemoteSoAddress(remote_soaddr);
		}
		catch (Exception e) {  log(LogLevel.INFO,e);  }
	}


	/** From RtpStreamReceiverListener. When the stream receiver terminated. */
	public void onRtpStreamReceiverTerminated(RtpStreamReceiver rr, Exception error) {
		if (error!=null) log(LogLevel.INFO,error);
	}


	/** From RtpStreamSenderListener. When the stream sender terminated. */
	public void onRtpStreamSenderTerminated(RtpStreamSender rs, Exception error) {
		if (error!=null) log(LogLevel.INFO,error);
	}


	/** Sets the synchronization adjustment time (in milliseconds). 
	  * It accelerates (sync_adj &lt; 0) or reduces (sync_adj &gt; 0) the sending rate respect to the nominal value.
	  * @param sync_adj the difference between the actual inter-packet sending time respect to the nominal value (in milliseconds). */
	public void setSyncAdj(long sync_adj) {
		if (rtp_sender!=null) rtp_sender.setSyncAdj(sync_adj);
		if (VERBOSE_DEBUG) println("X-DEBUG: Inter-packet time adjustment at sender: "+sync_adj+" ms every packet");
	}


	/** Sets the receiver packet random early drop (RED) value; if greater than 0, it is the inverse of the packet drop rate.
	  * @param random_early_drop the number of packets that separates two drops at receiver; a value of 0 means no drop. */
	public void setRED(int random_early_drop) {
		if (rtp_receiver!=null) rtp_receiver.setRED(random_early_drop);
	}



	// ****************************** Logs *****************************

	/** Adds a new string to the default Log */
	protected void log(String str) {
		log(LogLevel.INFO,str);
	}


	/** Adds a new string to the default Log */
	protected void log(LogLevel level, String str) {
		if (logger!=null) logger.log(level,this.getClass().getName()+": "+str);  
		if (level.getValue()>=LogLevel.INFO.getValue()) System.out.println(SystemUtils.getClassSimpleName(getClass().getName())+": "+str);
	}

	/** Adds the Exception message to the default Log */
	protected void log(LogLevel level, Exception e) {
		log(level,"Exception: "+ExceptionPrinter.getStackTraceOf(e));
	}


	/** Prints a new string message. */
	protected static void println(String str) {
		System.out.println(str);
	}


}
