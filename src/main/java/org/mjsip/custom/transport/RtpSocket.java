/*
 * Copyright (C) 2011-2013 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of Spydroid (http://code.google.com/p/spydroid-ipcamera/)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
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
 */

package org.mjsip.custom.transport;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


/**
 * A basic implementation of an RTP socket.
 * It implements a buffering mechanism, relying on a FIFO of buffers and a Thread.
 * That way, if a packetizer tries to send many packets too quickly, the FIFO will
 * grow and packets will be sent one by one smoothly.
 */
public class RtpSocket implements Runnable {

	public static final String TAG = "RtpSocket";

	public static final int RTP_HEADER_LENGTH = 12;
	public static final int MTU = 1500;

	private MulticastSocket mSocket;
	private DatagramPacket[] mPackets;
	private byte[][] mBuffers;
	private long[] mTimestamps;

	private Semaphore mBufferRequested, mBufferCommitted;
	private Thread mThread;

	private long mCacheSize;
	private long mClock = 0;
	private long mOldTimestamp = 0;
	private long mTime = 0, mOldTime = 0;
	private long mBitRate = 0, mOctetCount = 0;
	private int mSsrc, mSeq = 0, mPort = -1;
	private int mBufferCount, mBufferIn, mBufferOut;

	/**
	 * This RTP socket implements a buffering mechanism relying on a FIFO of buffers and a Thread.
	 * @throws IOException
	 */
	public RtpSocket() throws IOException {

		mCacheSize = 400;
		mBufferCount = 300; // TODO: reajust that when the FIFO is full 
		mBufferIn = 0;
		mBufferOut = 0;
		mBuffers = new byte[mBufferCount][];
		mPackets = new DatagramPacket[mBufferCount];
		mTimestamps = new long[mBufferCount];
		mBufferRequested = new Semaphore(mBufferCount);
		mBufferCommitted = new Semaphore(0);

		for (int i=0; i<mBufferCount; i++) {

			mBuffers[i] = new byte[MTU];
			mPackets[i] = new DatagramPacket(mBuffers[i], 1);

			/*							     Version(2)  Padding(0)					 					*/
			/*									 ^		  ^			Extension(0)						*/
			/*									 |		  |				^								*/
			/*									 | --------				|								*/
			/*									 | |---------------------								*/
			/*									 | ||  -----------------------> Source Identifier(0)	*/
			/*									 | ||  |												*/
			mBuffers[i][0] = (byte) Integer.parseInt("10000000",2);

			/* Payload Type */
			mBuffers[i][1] = (byte) 96;

			/* Byte 2,3        ->  Sequence Number                   */
			/* Byte 4,5,6,7    ->  Timestamp                         */
			/* Byte 8,9,10,11  ->  Sync Source Identifier            */

		}

		mSocket = new MulticastSocket();
		mTime = mOldTime = System.currentTimeMillis();

	}

	/** Closes the underlying socket. */
	public void close() {
		mSocket.close();
	}

	/** Sets the SSRC of the stream. */
	public void setSSRC(int ssrc) {
		this.mSsrc = ssrc;
		for (int i=0;i<mBufferCount;i++) {
			setLong(mBuffers[i], ssrc,8,12);
		}
	}

	/** Returns the SSRC of the stream. */
	public int getSSRC() {
		return mSsrc;
	}

	/** Sets the clock frquency of the stream in Hz. */
	public void setClockFrequency(long clock) {
		mClock = clock;
	}

	/** Sets the size of the FIFO in ms. */
	public void setCacheSize(long cacheSize) {
		mCacheSize = cacheSize;
	}
	
	/** Sets the Time To Live of the UDP packets. */
	public void setTimeToLive(int ttl) throws IOException {
		mSocket.setTimeToLive(ttl);
	}

	/** Sets the destination address and to which the packets will be sent. */
	public void setDestination(InetAddress dest, int dport) {
		mPort = dport;
		for (int i=0;i<mBufferCount;i++) {
			mPackets[i].setPort(dport);
			mPackets[i].setAddress(dest);
		}
	}

	public int getPort() {
		return mPort;
	}

	public int getLocalPort() {
		return mSocket.getLocalPort();
	}

	/** 
	 * Returns an available buffer from the FIFO, it can then directly be modified. 
	 * Call {@link #commitBuffer(int)} to send it over the network. 
	 * @throws InterruptedException 
	 **/
	public byte[] requestBuffer() throws InterruptedException {
		mBufferRequested.acquire();
		mBuffers[mBufferIn][1] &= 0x7F;
		return mBuffers[mBufferIn];
	}

	/** Sends the RTP packet over the network. */
	public void commitBuffer(int length) throws IOException {

		updateSequence();
		mPackets[mBufferIn].setLength(length);

		mOctetCount += length;
		mTime = System.currentTimeMillis();
		if (mTime - mOldTime > 1500) {
			mBitRate = mOctetCount*8000/(mTime-mOldTime);
			mOctetCount = 0;
			mOldTime = mTime;
		}

		mBufferCommitted.release();
		if (++mBufferIn>=mBufferCount) mBufferIn = 0;

		if (mThread == null) {
			mThread = new Thread(this);
			mThread.start();
		}

	}

	/** Returns an approximation of the bitrate of the RTP stream in bit per seconde. */
	public long getBitrate() {
		return mBitRate;
	}

	/** Increments the sequence number. */
	private void updateSequence() {
		setLong(mBuffers[mBufferIn], ++mSeq, 2, 4);
	}

	/** 
	 * Overwrites the timestamp in the packet.
	 * @param timestamp The new timestamp in ns.
	 **/
	public void updateTimestamp(long timestamp) {
		mTimestamps[mBufferIn] = timestamp;
		setLong(mBuffers[mBufferIn], (timestamp/100L)*(mClock/1000L)/10000L, 4, 8);
	}

	/** Sets the marker in the RTP packet. */
	public void markNextPacket() {
		mBuffers[mBufferIn][1] |= 0x80;
	}

	/** The Thread sends the packets in the FIFO one by one at a constant rate. */
	@Override
	public void run() {
		Statistics stats = new Statistics(50,3000);
		try {
			// Caches mCacheSize milliseconds of the stream in the FIFO.
			Thread.sleep(mCacheSize);
			long delta = 0;
			while (mBufferCommitted.tryAcquire(4,TimeUnit.SECONDS)) {
				if (mOldTimestamp != 0) {
					// We use our knowledge of the clock rate of the stream and the difference between two timestamp to
					// compute the temporal length of the packet.
					if ((mTimestamps[mBufferOut]-mOldTimestamp)>0) {
						stats.push(mTimestamps[mBufferOut]-mOldTimestamp);
						long d = stats.average()/1000000;
						//Log.d(TAG,"delay: "+d+" d: "+(mTimestamps[mBufferOut]-mOldTimestamp)/1000000);
						// We ensure that packets are sent at a constant and suitable rate no matter how the RtpSocket is used.
						Thread.sleep(d);
					}
					delta += mTimestamps[mBufferOut]-mOldTimestamp;
					if (delta>500000000 || delta<0) {
						//Log.d(TAG,"permits: "+mBufferCommitted.availablePermits());
						delta = 0;
					}
				}
				mOldTimestamp = mTimestamps[mBufferOut];
				mSocket.send(mPackets[mBufferOut]);
				if (++mBufferOut>=mBufferCount) mBufferOut = 0;
				mBufferRequested.release();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		mThread = null;
	}

	private void setLong(byte[] buffer, long n, int begin, int end) {
		for (end--; end >= begin; end--) {
			buffer[end] = (byte) (n % 256);
			n >>= 8;
		}
	}
	
	/** Computes the proper rate at which packets are sent. */
	protected static class Statistics {

		public final static String TAG = "Statistics";
		
		private int count=500, c = 0;
		private float m = 0, q = 0;
		private long elapsed = 0;
		private long start = 0;
		private long duration = 0;
		private long period = 6000000000L;
		private boolean initoffset = false;

		public Statistics(int count, long period) {
			this.count = count;
			this.period = period*1000000L; 
		}
		
		public void push(long value) {
			duration += value;
			elapsed += value;
			if (elapsed>period) {
				elapsed = 0;
				long now = System.nanoTime();
				if (!initoffset || (now - start < 0)) {
					start = now;
					duration = 0;
					initoffset = true;
				}
				value -= (now - start) - duration;
				//Log.d(TAG, "sum1: "+duration/1000000+" sum2: "+(now-start)/1000000+" drift: "+((now-start)-duration)/1000000+" v: "+value/1000000);
			}
			if (c<40) {
				// We ignore the first 20 measured values because they may not be accurate
				c++;
				m = value;
			} else {
				m = (m*q+value)/(q+1);
				if (q<count) q++;
			}
		}
		
		public long average() {
			long l = (long)m-2000000;
			return l>0 ? l : 0;
		}

	}

}
