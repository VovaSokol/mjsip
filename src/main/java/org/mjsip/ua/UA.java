/*
 * Copyright (C) 2006 Luca Veltri - University of Parma - Italy
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

package org.mjsip.ua;



import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.Vector;

import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import org.mjsip.custom.opus.RtpStreamReceiverOpus;
import org.mjsip.custom.opus.RtpStreamSenderOpus;
import org.mjsip.media.RtpStreamReceiver;
import org.mjsip.media.RtpStreamSender;
import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.address.SipURI;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.sip.provider.SipStack;
import org.mjsip.ua.cli.UserAgentCli;
import org.mjsip.ua.gui.UserAgentGui;
import org.zoolu.sound.SimpleAudioSystem;
import org.zoolu.util.Flags;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;


/** Simple SIP UA (User Agent) that can be executed with a GUI (it runs a GraphicalUA)
  * or with a command-line interface (it runs a CommandLineUA).
  * <p>
  * Class UA allows the user to set several configuration parameters
  * directly from comman-line before starting the proper UA (GraphicalUA or CommandLineUA).
  */
public class UA {
	static {
		System.out.println( System.getProperty("java.library.path"));
		try {/*
			System.loadLibrary("opus");
			System.loadLibrary("opusjni");
			System.out.println("Loaded Libs");*/
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	/** No GUI */ 
	protected static Boolean no_gui=new Boolean(false);

	/** Print stream */ 
	public static PrintStream stdout=System.out;


	/** Configuration file */ 
	//public static String file=null;

	/** SipProvider */ 
	public static SipProvider sip_provider=null;

	/** UserAgentProfile */ 
	public static UserAgentProfile ua_profile=null;


	/** Parses command-line options and inits the SIP stack, a SIP provider and an UA profile. */
	public static boolean init(String program, String[] args) {
		
		Flags flags=new Flags(args);
		while (flags.getBoolean("--skip",null)); // skip
		boolean help=flags.getBoolean("-h","prints this message");
		String config_file=flags.getString("-f","<file>",null,"loads configuration from the given file");
		
		Boolean unregist=flags.getBoolean("-u",null,"unregisters the contact address with the registrar server (the same as -g 0)");
		Boolean unregist_all=flags.getBoolean("-z",null,"unregisters ALL contact addresses");
		int regist_time=flags.getInteger("-g","<time>",-1,"registers the contact address with the registrar server for a gven duration, in seconds");

		Boolean no_offer=flags.getBoolean("-n",null,"no offer in invite (offer/answer in 2xx/ack)");
		String call_to=flags.getString("-c","<call_to>",null,"calls a remote user");      
		int accept_time=flags.getInteger("-y","<secs>",-1,"auto answers after given seconds");      
		int hangup_time=flags.getInteger("-t","<secs>",-1,"auto hangups after given seconds (0 means manual hangup)");
		int re_call_time=flags.getInteger("--re-call-time","<time>",-1,"re-calls after given seconds");
		int re_call_count=flags.getInteger("--re-call-count","<n>",-1,"number of successive automatic re-calls");
		String redirect_to=flags.getString("-r","<uri>",null,"redirects the call to new user <uri>");
		String[] transfer=flags.getStringTuple("-q",2,"<uri> <secs>",null,"transfers the call to <uri> after <secs> seconds");
		String transfer_to=transfer!=null? transfer[0] : null; 
		int transfer_time=transfer!=null? Integer.parseInt(transfer[1]) : -1;
		int re_invite_time=flags.getInteger("-i","<secs>",-1,"re-invites after given seconds");
		
		int host_port=flags.getInteger("-p","<port>",SipStack.default_port,"org.local SIP port, used ONLY without -f option");
		int media_port=flags.getInteger("-m","<port>",0,"(first) org.local media port");
		String via_addr=flags.getString("--via-addr","<addr>",SipProvider.AUTO_CONFIGURATION,"host via address, used ONLY without -f option");
		String outbound_proxy=flags.getString("-o","<addr>[:<port>]",null,"uses the given outbound proxy");
		long keepalive_time=flags.getLong("--keep-alive","<msecs>",-1,"send keep-alive packets each given milliseconds");

		Boolean audio=flags.getBoolean("-a",null,"audio");
		Boolean video=flags.getBoolean("-v",null,"video");

		Boolean loopback=flags.getBoolean("--loopback",null,"loopback mode, received media are sent back to the remote sender");
		Boolean recv_only=flags.getBoolean("--recv-only",null,"receive only mode, no media is sent");
		Boolean send_only=flags.getBoolean("--send-only",null,"send only mode, no media is received");
		Boolean send_tone=flags.getBoolean("--send-tone",null,"send only mode, an audio test tone is generated");
		String  send_file=flags.getString("--send-file","<file>",null,"audio is played from the specified file");
		String  recv_file=flags.getString("--recv-file","<file>",null,"audio is recorded to the specified file");
		String  send_video_file=flags.getString("--send-video-file","<file>",null,"video is played from the specified file");
		String  recv_video_file=flags.getString("--recv-video-file","<file>",null,"video is recorded to the specified file");
		Boolean no_system_audio=flags.getBoolean("--no-audio",null,"do not use system audio");

		String contact_uri=flags.getString("--contact-uri","<uri>",null,"user's contact URI");
		String display_name=flags.getString("--display-name","<str>",null,"display name");
		String user=flags.getString("--user","<user>",null,"user name");
		// for backward compatibility
		String from_uri=flags.getString("--from-uri","<uri>",null,"user's address-of-record (AOR)");
		
		String proxy=flags.getString("--proxy","<proxy>",null,"proxy server");
		String registrar=flags.getString("--registrar","<registrar>",null,"registrar server");
		
		String transport=flags.getString("--transport","<proto>",null,"use the given transport protocol for SIP");
		
		String auth_user=flags.getString("--auth-user","<user>",null,"user name used for authenticat");
		String auth_realm=flags.getString("--auth-realm","<realm>",null,"realm used for authentication");
		String auth_passwd=flags.getString("--auth-passwd","<passwd>",null,"passwd used for authentication");

		int debug_level=flags.getInteger("--debug-level","<level>",-1,"debug level (level=0 means no log)");
		String log_path=flags.getString("--log-path","<path>",null,"log folder");

		no_gui=flags.getBoolean("--no-gui",no_gui,"do not use graphical user interface");
		Boolean no_prompt=flags.getBoolean("--no-prompt",null,"do not prompt");
		String[] remaining_params=flags.getRemainingStrings(true,null,null);
		
		if (remaining_params.length>0) {
			println("unrecognized param '"+remaining_params[0]+"'\n");
		}
		if (remaining_params.length>0 || help) {
			println(flags.toUsageString(program));
			return false;
		}
		try {
			String serverIp = "192.168.0.135:5160";
			String username = "14";
			String pass = "qrqfakFXWb";
			String callTo = "2128";

			// init SipStack
			SimpleAudioSystem.begin_mixer_from = 0;

			for (Mixer.Info info : AudioSystem.getMixerInfo()) {
				System.out.println("descr: <" + info.getDescription() +"> name: <"+ info.getName() + "> vend: <" +
						info.getVendor() + ">");
			}

			SipStack.init(config_file);
			SipStack.debug_level = 100;
			if (log_path!=null) SipStack.log_path=log_path;

			// init sip_provider
			sip_provider=new SipProvider(
				SipProvider.AUTO_CONFIGURATION,
				SipStack.default_port,
				new String[]{SipProvider.PROTO_UDP}
			);
			sip_provider.setOutboundProxy(new SipURI(serverIp));
			// init ua_profile
			ua_profile=new UserAgentProfile(config_file);
			if (no_prompt!=null) ua_profile.no_prompt=no_prompt.booleanValue();
			if (no_system_audio!=null) ua_profile.no_system_audio=no_system_audio.booleanValue();

			if (unregist!=null) ua_profile.do_unregister=unregist.booleanValue();
			if (unregist_all!=null) ua_profile.do_unregister_all=unregist_all.booleanValue();
			if (regist_time>=0) {  ua_profile.do_register=true;  ua_profile.expires=regist_time;  }
			if (keepalive_time>=0) ua_profile.keepalive_time=keepalive_time;
			if (no_offer!=null) ua_profile.no_offer=no_offer.booleanValue();
			if (redirect_to!=null) ua_profile.redirect_to=new NameAddress(redirect_to);
			if (transfer_to!=null) ua_profile.transfer_to=new NameAddress(transfer_to);
			if (transfer_time>0) ua_profile.transfer_time=transfer_time;
			if (accept_time>=0) ua_profile.accept_time=accept_time;
			if (hangup_time>0) ua_profile.hangup_time=hangup_time;
			if (re_invite_time>0) ua_profile.re_invite_time=re_invite_time;
			if (re_call_time>0) ua_profile.re_call_time=re_call_time;
			if (re_call_count>0) ua_profile.re_call_count=re_call_count;
			if (audio!=null) ua_profile.audio=audio.booleanValue();
			if (video!=null) ua_profile.video=video.booleanValue();
			//if (media_port>0) ua_profile.media_port=media_port;
			if (media_port>0) ua_profile.setMediaPort(media_port);

			ua_profile.call_to=new NameAddress(callTo+"@"+serverIp);
			ua_profile.display_name=username;
			ua_profile.user=username;
			ua_profile.registrar=username+"@" + serverIp;
			ua_profile.do_register=true;
			ua_profile.auth_user=username;
			ua_profile.auth_realm="asterisk";
			ua_profile.auth_passwd=pass;
			ua_profile.setUserURI(new NameAddress(username+"@" + serverIp));
			ua_profile.javax_sound_direct_convertion = true;
			ua_profile.javax_sound_sync = false;

			ua_profile.video = true;
			ua_profile.use_vic = true;
			ua_profile.audio=true;

			ua_profile.proxy = serverIp;

//			ua_profile.bin_vic = "/usr/local/bin/ffmpeg -f v4l2 -i /dev/video0 -vcodec libopenh264 -vb 32k -vc 1 -f rtp rtp://";
			ua_profile.bin_vic = "/usr/local/bin/ffmpeg -f v4l2 -i /dev/video0 -vcodec libopenh264 -payload_type 97 -f rtp rtp://";

			RtpStreamSenderOpus.DEBUG = true;
			RtpStreamReceiverOpus.DEBUG = true;
			SimpleAudioSystem.DEBUG = true;
			/*FFmpeg.atPath(Paths.get("/usr/local/bin/"))//
					.addArguments("-protocol_whitelist","file,http,https,tcp,tls,crypto,rtp")
					.addArguments("-f","video4linux2")
					.addArguments("-i","/dev/video0")
					.addOutput(//
							UrlOutput.toUrl("udp://127.0.0.1:34999")//
									.addArgument("-an")
									.setPixelFormat("yuv420p")// vova dell e6440
//									.addArguments("-payload_type","96")
									.addArguments("-profile:v","baseline") //video cam
									.addArguments("-level:v","3.0") //video cam
									.addArguments("-tune","zerolatency") //video cam
									.setFrameRate(15) //video cam
									.addArguments("-s","640x480") //video cam
									.setCodec(StreamType.VIDEO, "libopenh264")//
									.setFormat("h264")//
					).execute();*/



//			Process process = Runtime.getRuntime().exec(String.format("/usr/local/bin/ffmpeg -n -protocol_whitelist file,http,https,tcp,tls,crypto,rtp -f video4linux2 -i /dev/video0 -f h264 -r 15 -c:v libopenh264 -pix_fmt yuv420p -an -profile:v baseline -level:v 3.0 -tune zerolatency -s 640x480 udp://127.0.0.1:34999"));
//			Process process1 = Runtime.getRuntime().exec(String.format("/usr/local/bin/ffmpeg -i udp://127.0.0.1:35999 -f h264 -r 15 -c:v libopenh264 -pix_fmt yuv420p -an -profile:v baseline -level:v 3.0 -tune zerolatency -s 640x480 udp://127.0.0.1:36999"));

//			process.destroy();
//			Runtime.getRuntime().exec(String.format("/usr/bin/raspivid -fps 30 -h 1280 -w 720 -vf -n -t 0 -b 2000000 -o - | gst-launch-1.0 -v fdsrc ! h264parse ! rtph264pay config-interval=1 pt=96 ! gdppay ! tcpserversink host=192.168.0.108 port=5000"));

			return true;
		}
		catch (Exception e) {			
			e.printStackTrace();
			return false;
		}
	}


	/** Prints a message to standard output. */
	protected static void println(String str) {
		if (stdout!=null) stdout.println(str);
	}


	/** The main method. */
	public static void main(String[] args) {
		
		println("MJSIP UA "+SipStack.version);

		if (!init("org.local.ua.UA",args)) System.exit(0);
		// else
		if (no_gui.booleanValue()) new UserAgentCli(sip_provider,ua_profile);
		else new UserAgentGui(sip_provider,ua_profile);
	}

}
