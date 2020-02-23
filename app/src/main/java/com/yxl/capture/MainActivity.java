package com.yxl.capture;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.ImageView;

import com.yxl.capture.codec.VideoCodecUtil;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    Handler mMainThreadHandler=new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            capture();
            mMainThreadHandler.sendEmptyMessageDelayed(0,30);
            return true;
        }
    });
    Handler mSendMessageThreadHandler;

    VideoCodecUtil mVideoCodecUtil;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WebView webView= findViewById(R.id.web_view);
        webView.loadUrl("https://www.qq.com/");
        EditText editText=findViewById(R.id.edit_text_http);
        editText.setText("https://www.qq.com/");

        mMainThreadHandler.sendEmptyMessage(0);
        //VideoCodecUtil video=new VideoCodecUtil();


        View contentView= findViewById(android.R.id.content);
        contentView.setDrawingCacheEnabled(true);

        ((EditText)findViewById(R.id.edit_text_service_ip)).setText("192.168.0.11");
        ((EditText)findViewById(R.id.edit_text_service_port)).setText("9999");

        findViewById(R.id.button_ok).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                String ip=((EditText)findViewById(R.id.edit_text_service_ip)).getText().toString();
                String port=((EditText)findViewById(R.id.edit_text_service_port)).getText().toString();
                if(ip.length()==0)
                {
                    new AlertDialog.Builder(MainActivity.this).setMessage("没有输入IP").setOnCancelListener(null).create().show();
                    return;
                }
                if(port.length()==0)
                {
                    new AlertDialog.Builder(MainActivity.this).setMessage("没有输入端口").setOnCancelListener(null).create().show();
                    return;
                }
                HashMap<String,String> obj=new HashMap<>();
                obj.put("ip",ip);
                obj.put("port",port);
                mSendMessageThreadHandler.sendMessage(mSendMessageThreadHandler.obtainMessage(0,obj));
            }
        });

        findViewById(R.id.button_go).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                EditText editText=findViewById(R.id.edit_text_http);
                WebView webView= findViewById(R.id.web_view);
                webView.loadUrl(editText.getText().toString());
            }
        });

        initializeNetwork();

       /* mVideoCodecUtil=new VideoCodecUtil();
        mVideoCodecUtil.setAsyncEncodeListener(new VideoCodecUtil.AsyncEncodeListener()
        {
            @Override
            public void asyncEncodeBuffer(byte[] buffer, int length)
            {
                byte []data=new byte[length];
                System.arraycopy(data,length,buffer,length,length);
            //   mSendMessageThreadHandler.sendMessage(mSendMessageThreadHandler.obtainMessage(1,data));
                System.out.println(data);
            }
        });*/
    }

    void initializeNetwork()
    {
        final Object syncObject=new Object();

        new Thread(new Runnable()
        {
            Socket mSocket;
            OutputStream mOutputStream;
            @Override
            public void run()
            {
                Looper.prepare();
                mSendMessageThreadHandler=new Handler(new Handler.Callback()
                {
                    @Override
                    public boolean handleMessage(Message message)
                    {
                        if(message.what==0)
                        {
                            try
                            {
                                if(mSocket!=null)
                                {
                                    mSocket.close();
                                    mSocket=null;
                                    mOutputStream=null;
                                }
                                HashMap<String,String> msg= (HashMap<String, String>) message.obj;
                                String ip=msg.get("ip");
                                int port=Integer.parseInt(msg.get("port"));

                                mSocket=new Socket(ip,port);
                                mOutputStream=mSocket.getOutputStream();
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }
                        }
                        if(message.what==1)
                        {
                            if(mSocket==null||mOutputStream==null)
                            {
                                return true;
                            }

                            try
                            {
                                byte[]data= (byte[]) message.obj;
                                ByteBuffer buffer=ByteBuffer.allocate(4);
                                buffer.order(ByteOrder.LITTLE_ENDIAN);
                                buffer.putInt(data.length);
                                byte[] lengthByte= buffer.array();
                                mOutputStream.write(lengthByte);
                                mOutputStream.write(data);
                            }
                            catch (Exception e)
                            {
                                try
                                {
                                    mSocket.close();
                                    mSocket=null;
                                    mOutputStream=null;
                                }
                                catch (Exception ex)
                                {
                                    ex.printStackTrace();
                                }
                            }
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

    void capture()
    {
        View contentView= findViewById(android.R.id.content);
        Bitmap bmp= contentView.getDrawingCache();
        if(bmp==null)
        {
            return;
        }
        ImageView iv= findViewById(R.id.image_view);
        iv.setImageBitmap(bmp);
        ByteArrayOutputStream bos=new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG,50,bos);
      //  mVideoCodecUtil.encodeImageData(bos.toByteArray());
        mSendMessageThreadHandler.sendMessage(mSendMessageThreadHandler.obtainMessage(1,bos.toByteArray()));
    }


}
