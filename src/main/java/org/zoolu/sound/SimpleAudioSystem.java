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

package org.zoolu.sound;



import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import com.github.kokorin.jaffree.nut.StreamHeader;
import org.zoolu.util.SystemUtils;

import java.util.Locale;


/** SimpleAudioSystem is a static class that allows the access to system audio
  * input and output in pure-java style using the javax.sound.sampled library (package).
  * <p>
  * SimpleAudioSystem has the following main methods: <br>
  * <ul>
  * <li> {@link #initAudioInputLine(float, int)} - to initialize the audio input line</li>
  *
  * <li> {@link #startAudioInputLine()} - to start capturing audio</li>
  *
  * <li> {@link #getInputStream(AudioFormat)} - to get the audio input stream</li>
  * 
  * <li> {@link #stopAudioInputLine()} - to stop capturing audio</li>
  *
  * <li> {@link #initAudioOutputLine(float, int)} - to initialize the audio output line</li>
  *
  * <li> {@link #startAudioOutputLine()} - to start playing audio out</li>
  *
  * <li> {@link #getOutputStream(AudioFormat)} - to get the audio output stream</li>
  * 
  * <li> {@link #stopAudioOutputLine()} - to stop playing audio out</li>
  *
  * </ul>
  */
public class SimpleAudioSystem {
	
	/** Whether printing debugging information on standard error output. */
	public static boolean DEBUG=false;
	
	/** Internal buffer size */
	public static final int INTERNAL_BUFFER_SIZE=40960;

	/** Default audio format */
	public static final AudioFormat DEFAULT_AUDIO_FORMAT=new AudioFormat(AudioFormat.Encoding.ULAW,8000.0F,8,1,1,8000.0F,false);

	/** Base system audio format (PCM 8000Hz, Linear, 16bit, Mono, Little endian) */
	//private static final AudioFormat base_format=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,8000.0F,16,1,2,8000.0F,false);

	/** Input line (TargetDataLine) for audio capturing */
	private static TargetDataLine target_line=null;
	
	/** Output line (SourceDataLine) for audio playout */
	private static SourceDataLine source_line;

	public static int begin_mixer_from = 1;

	public static void setMixer(AudioFormat audioFormat){
		Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();

		for(int i = begin_mixer_from; i < mixerInfo.length; ++i) {
			Mixer.Info info = mixerInfo[i];
			debug(String.format(Locale.US, "====== MIXER INFO num: %02d ====== \ndescr: <%s> \nname: <%s> \nvend: <%s> \nver: <%s>", i, info.getDescription(), info.getName(), info.getVendor(), info.getVersion()));
			Mixer mixer = AudioSystem.getMixer(info);
			javax.sound.sampled.Line.Info[] srcLinesInfo = mixer.getSourceLineInfo();
			debug("srcLinesInfo cnt: " + srcLinesInfo.length);

			for(int i1 = 0; i1 < srcLinesInfo.length; ++i1) {
				debug(String.format(Locale.US, "srcLineInfo num: %02d srcLineInfo: <%s>", i1, srcLinesInfo[i1].toString()));
			}

			javax.sound.sampled.Line.Info[] dstLinesInfo = mixer.getTargetLineInfo();
			debug("dstLinesInfo cnt: " + dstLinesInfo.length);

			for(int i1 = 0; i1 < dstLinesInfo.length; ++i1) {
				debug(String.format(Locale.US, "dstLineInfo num: %02d dstLineInfo: <%s>", i1, dstLinesInfo[i1].toString()));
			}

			TargetDataLine dst = null;
			SourceDataLine src = null;

			try {
				dst = AudioSystem.getTargetDataLine(audioFormat, info);
				debug("dstDataLine OK " + dst.getLineInfo());
			} catch (Exception var13) {
				error("Error: dstDataLine not supported");
			}

			try {
				src = AudioSystem.getSourceDataLine(audioFormat, info);
				debug("srcDataLine OK " + src.getLineInfo());
			} catch (Exception var12) {
				error("Error: srcDataLine not supported");
			}

			if(dst != null && src != null){
				SimpleAudioSystem.mixer_info = mixer.getMixerInfo();
				break;
			}
		}
	}

	public static void initMixerAndLines(float sample_rate, int channels){
		AudioFormat base_format=getBaseAudioFormat(sample_rate,channels);
		SimpleAudioSystem.setMixer(base_format);

		if (DEBUG) {
			AudioFormat.Encoding[] codecs=AudioSystem.getTargetEncodings(base_format);
			String codec_list="";
			for (int i=0; i<codecs.length; i++) codec_list+=" "+codecs[i].toString();
			debug("Input: Supported codecs:"+codec_list);
		}
	}

	/** Initializes the static system audio input line.
	 * This method creates a the input line (TargetDataLine) with a base audio format.
	 * @param sample_rate sample rate
	 * @param channels number of audio channels (1 for mono, 2 for stereo) */
	public static void initAudioInputLine(float sample_rate, int channels) {
		AudioFormat base_format=getBaseAudioFormat(sample_rate,channels);
		if(mixer_info == null){
			initMixerAndLines(sample_rate, channels);
		}
		// check if the input line with the selected format is supported
		DataLine.Info lineInfo=new DataLine.Info(TargetDataLine.class,base_format,INTERNAL_BUFFER_SIZE);
		if (!AudioSystem.isLineSupported(lineInfo)) {
			error("ERROR: AudioLine not supported by this system.");
		}
		// create the input line (TargetDataLine) and open it
		try {
			if(mixer_info != null){
				target_line=AudioSystem.getTargetDataLine(base_format, mixer_info);
				if (DEBUG) debug("Input: TargetDataLine: "+target_line.getFormat());
				target_line.open(base_format,INTERNAL_BUFFER_SIZE);
			}else{
				error("ERROR: Empty mixer");
			}
		}
		catch (Exception e) {
			error("ERROR: Exception when trying to init audio input: "+e.getMessage());
			e.printStackTrace();
		}
	}

	private static Mixer.Info mixer_info;

	public static Mixer.Info getMixer_info() {
		if(mixer_info == null){
			setMixer(getBaseAudioFormat(48000, 1));
		}
		return mixer_info;
	}

	/** Gets the TargetDataLine from a given Mixer.
	 * @throws LineUnavailableException */
	private static TargetDataLine selectTargetDataLine(Line.Info line_info, int line_index) throws LineUnavailableException {
		Mixer.Info[] mixer_infos=AudioSystem.getMixerInfo();
		for (int i=1; i<mixer_infos.length; i++) {
			Mixer.Info mi=mixer_infos[i];
			Mixer m=AudioSystem.getMixer(mi);
			Line.Info[] line_infos=m.getTargetLineInfo();
			if(line_infos.length>=1 && line_infos[0].getLineClass().equals(TargetDataLine.class)) {
				System.out.println(SimpleAudioSystem.class.getSimpleName()+": Input: --- TargetDataLine["+i+"]: "+mi.getName());
                Mixer m1=AudioSystem.getMixer(mixer_infos[i]);
                for (Line.Info info : m1.getTargetLineInfo()) {
                    TargetDataLine target_line1 = (TargetDataLine)m1.getLine(info);
                    System.out.println(SimpleAudioSystem.class.getSimpleName() + ": ALL TargetDataLines: " + target_line1.getFormat());
                }
				mixer_info = mi;
                TargetDataLine target_line=(TargetDataLine)m1.getLine(m1.getTargetLineInfo()[0]);
                System.out.println(SimpleAudioSystem.class.getSimpleName()+": Input: --- Selected TargetDataLine #"+i+": "+mixer_infos[i].getName());
                return target_line;
			}
		}
		if (line_index>=0) {
			Mixer m=AudioSystem.getMixer(mixer_infos[line_index]);
			TargetDataLine target_line=(TargetDataLine)m.getLine(m.getTargetLineInfo()[0]);
			System.out.println(SimpleAudioSystem.class.getSimpleName()+": Input: --- Selected TargetDataLine #"+line_index+": "+mixer_infos[line_index].getName());
			return target_line;
		}
		else {
			System.out.println(SimpleAudioSystem.class.getSimpleName()+": Input: --- Using the default TargetDataLine");
			return (TargetDataLine)AudioSystem.getLine(line_info);			
		}
	}


	/** Closes the static system audio input line */
	static public void closeAudioInputLine() {
		target_line.close();
	}


	/** Initializes the static system audio output line.
	 * This method creates a the output line (SourceDataLine) with a base audio format.
	 * @param sample_rate sample rate 
	 * @param channels number of audio channels (1 for mono, 2 for stereo) */
	public static void initAudioOutputLine(float sample_rate, int channels) {
		AudioFormat base_format=getBaseAudioFormat(sample_rate,channels);
		if(mixer_info == null){
			initMixerAndLines(sample_rate, channels);
		}

		DataLine.Info lineInfo=new DataLine.Info(SourceDataLine.class, base_format, INTERNAL_BUFFER_SIZE);

		if (!AudioSystem.isLineSupported(lineInfo)) {
			error("ERROR: AudioLine not supported by this System.");
		}
		if (DEBUG) debug("Output: lineInfo: "+lineInfo.toString());

		try {
			//target_line=(TargetDataLine)AudioSystem.getLine(lineInfo);
			if(mixer_info != null){
				source_line=AudioSystem.getSourceDataLine(base_format, mixer_info);
				if (DEBUG) debug("Input: TargetDataLine: "+source_line.getFormat());
				source_line.open(base_format,INTERNAL_BUFFER_SIZE);
			}else{
				error("ERROR: Empty mixer");
			}
		}
		catch (Exception e) {
			error("ERROR: Exception when trying to init audio output: "+e.getMessage());
			e.printStackTrace();
		}
	}


	/** Closes the static system audio output line. */
	static public void closeAudioOutputLine() {
		source_line.close();
	}


	/** Starts capturing system audio. */
	public static void startAudioInputLine() {
		if (target_line==null) initAudioInputLine(DEFAULT_AUDIO_FORMAT.getSampleRate(),DEFAULT_AUDIO_FORMAT.getChannels()); 
		if (target_line.isOpen()) target_line.start();
		else {
			error("WARNING: Audio play error: target line is not open.");
		}
	}


	/** Stops capturing system audio. */
	public static void stopAudioInputLine() {
		if (target_line.isOpen()) target_line.stop();
		else {
			error("WARNING: Audio stop error: target line is not open.");
		}
		//target_line.close();
	}


	/** Starts playing system audio. */
	public static void startAudioOutputLine() {
		if (source_line==null) initAudioOutputLine(DEFAULT_AUDIO_FORMAT.getSampleRate(),DEFAULT_AUDIO_FORMAT.getChannels());
		if (source_line.isOpen()) source_line.start();
		else {
			error("WARNING: Audio play error: source line is not open.");
		}
	}


	/** Stops playing system audio. */
	public static void stopAudioOutputLine() {
		if (source_line.isOpen()) {
			source_line.drain();
			source_line.stop();
		}
		else {
			error("WARNING: Audio stop error: source line is not open.");
		}
		//source_line.close();
	}


	/** Gets the base audio format.
	 * This is the format used for opening the system input and output lines.
	 * <p>
	 * The format is PCM LINEAR SIGNED, 16-bit samples, 2-byte frames, mono, with the given sample rate.
	 * @param sample_rate sample rate
	 * @param channels number of audio channels (1 for mono, 2 for stereo) */
	public static AudioFormat getBaseAudioFormat(float sample_rate, int channels) {
		//return base_format;
		return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,sample_rate,16,channels,channels*2,sample_rate,false);
	}


	/** Gets the base transcoded audio format. It differs from the base audio format just over the encoding type.
	 * @param sample_rate sample rate
	 * @param channels number of audio channels (1 for mono, 2 for stereo)
	 * @param encoding the encoding type */
	public static AudioFormat getBaseTranscodedAudioFormat(float sample_rate, int channels, AudioFormat.Encoding encoding) {
		AudioFormat base_format=getBaseAudioFormat(sample_rate,channels);
		AudioFormat format=new AudioFormat(encoding,base_format.getSampleRate(), base_format.getSampleSizeInBits(),base_format.getChannels(),base_format.getFrameSize(),base_format.getFrameRate(),base_format.isBigEndian());
		return format;
	}


	/** Gets a new system audio input stream.
	 * The audio format is {@link #DEFAULT_AUDIO_FORMAT}. */
	public static AudioInputStream getInputStream() {
		return getInputStream(DEFAULT_AUDIO_FORMAT);
	}


	/** Gets a new system audio input stream.
	 * @param sample_rate audio sample rate
	 * @param channels number of audio channels (1 for mono, 2 for stereo)
	 * @param encoding audio encoding type */
	public static AudioInputStream getInputStream(float sample_rate, int channels, AudioFormat.Encoding encoding) {
		return getInputStream(getBaseTranscodedAudioFormat(sample_rate,channels,encoding));
	}


	/** Gets a new system audio input stream.
	 * @param format audio format */
	public static AudioInputStream getInputStream(AudioFormat format) {
		if (target_line==null) initAudioInputLine(format.getSampleRate(),format.getChannels());
		else
		if (target_line.getFormat().getSampleRate()!=format.getSampleRate()) {
			//throw new RuntimeException("The system audio input line has been already initialized with sample rate "+target_line.getFormat().getSampleRate()+" != "+format.getSampleRate());
			target_line.close();
			initAudioInputLine(format.getSampleRate(),format.getChannels());
		}		
		AudioInputStream audio_input_stream=null;
		if (target_line.isOpen()) {
			// create an AudioInputStream from the target_line
			AudioInputStream base_audio_input_stream=new AudioInputStream(target_line);
			// convert the AudioInputStream to the selected format
			audio_input_stream=AudioSystem.getAudioInputStream(format,base_audio_input_stream);
		}
		else {
			error("WARNING: Audio init error: target line is not open.");
		}

		return audio_input_stream;
	}


	/** Gets a new system audio output stream.
	 * The audio format is {@link #DEFAULT_AUDIO_FORMAT}. */
	public static AudioOutputStream getOutputStream() {
		return getOutputStream(DEFAULT_AUDIO_FORMAT);
	}
	
	
	/** Gets a new system audio output stream.
	 * @param sample_rate audio sample rate
	 * @param channels number of audio channels (1 for mono, 2 for stereo)
	 * @param encoding audio encoding type */
	public static AudioOutputStream getOutputStream(float sample_rate, int channels, AudioFormat.Encoding encoding) {
		return getOutputStream(getBaseTranscodedAudioFormat(sample_rate,channels,encoding));
	}


	/** Gets a new system audio output stream.
	 * @param format audio format */
	public static AudioOutputStream getOutputStream(AudioFormat format) {
		if (source_line==null) {
			//debug("DEBUG: getOutputStream(): audio output line is initialized using the default sample rate "+DEFAULT_AUDIO_FORMAT.getSampleRate());
			//initAudioOutputLine(DEFAULT_AUDIO_FORMAT.getSampleRate());
			debug("DEBUG: getOutputStream(): audio output line is initialized using sample rate "+format.getSampleRate());
			initAudioOutputLine(format.getSampleRate(),format.getChannels());
		}
		else
		if (source_line.getFormat().getSampleRate()!=format.getSampleRate()) {
			//throw new RuntimeException("The system audio output line has been already initialized with sample rate "+source_line.getFormat().getSampleRate()+" != "+format.getSampleRate());
			source_line.close();
			initAudioOutputLine(format.getSampleRate(),format.getChannels());
		}		
		AudioOutputStream audio_output_stream=null;
		if (source_line.isOpen()) {
			// convert the audio stream to the selected format
			try {
				audio_output_stream=new SourceLineAudioOutputStream(format,source_line);
			}
			catch (Exception e) {
				error("WARNING: Audio init error: impossible to get audio output stream of type ["+format+"] from source line of type ["+source_line.getFormat()+"]");
				//e.printStackTrace();
			}
		}
		else {
			error("WARNING: Audio init error: source line is not open.");
		}

		return audio_output_stream;
	} 

	
	/** Gets the audio format corresponding to a given codec.
	 * @param codec the codec
	 * @param sample_rate sample rate */
	public static AudioFormat getAudioFormat(CodecType codec, float sample_rate) {
		AudioFormat.Encoding encoding;
		int bits;
		int channels=1;
		float frame_rate=sample_rate/codec.getSamplesPerFrame();
		if (codec.getName().equalsIgnoreCase(CodecType.G711_ULAW.getName())) {
			encoding=AudioFormat.Encoding.ULAW;
			bits=8;
		}
		else
		if (codec.getName().equalsIgnoreCase(CodecType.G711_ALAW.getName())) {
			encoding=AudioFormat.Encoding.ALAW;
			bits=8;
		}
		else
		if (codec.getName().equalsIgnoreCase(CodecType.PCM_LINEAR.getName())) {
			encoding=AudioFormat.Encoding.PCM_SIGNED;
			bits=16;
		}
		else {
			//encoding=new AudioFormat.Encoding(codec.getName());
			encoding=new AudioFormat.Encoding(codec.getName()){}; // In Java 1.4 the AudioFormat.Encoding constructor has protected access
			bits=16;
		}
		return new AudioFormat(encoding,sample_rate,bits,channels,codec.getFrameSize(),frame_rate,false);
	}


	/** Debug output */
	private static void debug(String str) {
		//System.out.println(SimpleAudioSystem.class.getSimpleName()+": "+str);
		System.out.println(SystemUtils.getClassSimpleName(SimpleAudioSystem.class.getName())+": "+str);
	}

	
	/** Error output */
	private static void error(String str) {
		//System.err.println(SimpleAudioSystem.class.getSimpleName()+": "+str);
		System.err.println(SystemUtils.getClassSimpleName(SimpleAudioSystem.class.getName())+": "+str);
	}

}
