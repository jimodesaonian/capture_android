package com.yxl.capture.codec;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.RequiresApi;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.ListIterator;

public class VideoCodecUtil {
    MediaCodec.BufferInfo mBufferInfo;
    MediaCodec mMediaCodec;

    Handler mEncodeThreadHandler;
    AsyncEncodeListener listener;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public VideoCodecUtil()
    {
        initializeEncodeThread();
    }

    public void encodeImageData(byte[]data)
    {
        mEncodeThreadHandler.sendMessage(mEncodeThreadHandler.obtainMessage(0,data));
    }

    public void setAsyncEncodeListener(AsyncEncodeListener listener)
    {
        this.listener=listener;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void initCodec() {
        try {
            mBufferInfo = new MediaCodec.BufferInfo();
            mMediaCodec = MediaCodec.createEncoderByType("video/avc");
        } catch (Exception e) {
            e.printStackTrace();
        }
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc",
                1920,
                1080);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 125000);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
        mMediaCodec.configure(mediaFormat,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();

    }

    void initializeEncodeThread()
    {
        final Object syncObject=new Object();

        new Thread(new Runnable()
        {
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void run()
            {
                Looper.prepare();
                initCodec();
                mEncodeThreadHandler=new Handler(new Handler.Callback()
                {
                    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
                    @Override
                    public boolean handleMessage(Message message)
                    {
                        byte[]data= (byte[]) message.obj;
                        try
                        {
                            encode(data);
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }

                        return true;
                    }
                });
                synchronized (syncObject)
                {
                    syncObject.notify();
                }
                Looper.loop();
            }
        }).start();

        synchronized (syncObject)
        {
            try
            {
                syncObject.wait();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void encode(byte[] data) {
        ByteBuffer[] inputBuffers;
        ByteBuffer[] outputBuffers;
        inputBuffers = mMediaCodec.getInputBuffers();// here changes
        outputBuffers = mMediaCodec.getOutputBuffers();

        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(data);
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, data.length, 0, 0);
        } else {
            return;
        }

        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 10000);
        do {
            if (outputBufferIndex >= 0) {
                ByteBuffer outBuffer = outputBuffers[outputBufferIndex];
                System.out.println("buffer info-->" + mBufferInfo.offset + "--"
                        + mBufferInfo.size + "--" + mBufferInfo.flags + "--"
                        + mBufferInfo.presentationTimeUs);
                byte[] outData = new byte[mBufferInfo.size];
                outBuffer.get(outData);
                if (mBufferInfo.offset != 0) {
                    byte[] offsettedData;
                    offsettedData = Arrays.copyOfRange(outData, mBufferInfo.offset, outData.length - 1);
                    pushFrame(offsettedData, offsettedData.length);
                } else {
                    pushFrame(outData, outData.length);
                }
                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo,
                        0);

            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mMediaCodec.getOutputBuffers();
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = mMediaCodec.getOutputFormat();
                System.out.println(format);
            }
        } while (outputBufferIndex >= 0);
    }

    private void pushFrame(byte[] buffer, int length)
    {
        if(listener!=null)
        {
            listener.asyncEncodeBuffer(buffer,length);
        }
    }


    public interface AsyncEncodeListener
    {
        void asyncEncodeBuffer(byte[]buffer,int length);
    }

}
