package com.example.admin.lottieviewexportvideo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.widget.ImageView;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.OnCompositionLoadedListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * 友情提示，这个方式好像导出带有图片的lottieView还是有一些问题，比如闪烁什么的
 * 蛋疼疼
 * 希望有大佬可以给提示一下
 */

public class MainActivity extends AppCompatActivity {

    private LottieAnimationView lottieAnimationView;
    private ImageView imageView;
    private ConstraintLayout constraintLayout;
    private LottieDrawable lottieDrawable = new LottieDrawable();
    private final String PATH = Environment.getExternalStorageDirectory().getAbsolutePath();
    private static final boolean VERBOSE = true;
    private static final String TAG = "MainActivity";
    private static final String MIME_TYPE = "video/avc";
    private int WIDTH = 1280;
    private int HEIGHT = 720;
    private static final int BIT_RATE = 4000000;
    private static final int FRAMES_PER_SECOND = 25;
    private static final int IFRAME_INTERVAL = 2;
    private MediaCodec.BufferInfo mBufferInfo;
    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    private Surface mInputSurface;
    private int mTrackIndex;
    private boolean mMuxerStarted;
    private long mFakePts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //  Camera camera = Camera.open();
        //  Camera.Parameters parameters = camera.getParameters();
       /* List<Integer> list  = parameters.getSupportedPreviewFormats();
        for(int i = 0 ; i < list.size() ; i++){
            Log.i("MDL","formats:" + list.get(i));
        }*/

        imageView = findViewById(R.id.imageView);
        constraintLayout = findViewById(R.id.all);
        lottieAnimationView = findViewById(R.id.lottieAnimationView);
        lottieDrawable.setImagesAssetsFolder("images/");
//        lottieAnimationView.setImageAssetsFolder("images/");
        LottieComposition.Factory.fromAssetFileName(this, "xiaoYa.json", new OnCompositionLoadedListener() {
            @Override
            public void onCompositionLoaded(final LottieComposition composition) {
                /*lottieAnimationView.setComposition(composition);
                lottieAnimationView.playAnimation();*/

                new Thread() {
                    @Override
                    public void run() {
                        super.run();
                        lottieDrawable.setComposition(composition);

                        WIDTH = lottieDrawable.getIntrinsicWidth();
                        HEIGHT = lottieDrawable.getIntrinsicHeight();
                        Log.i("MDL", "time1:" + System.currentTimeMillis());
                        try {
                            generateMovie(new File(PATH + "/soft-input-surface2.mp4"));//
                        } catch (Exception ex) {
                        }
                        Log.i("MDL", "time2:" + System.currentTimeMillis());
                    }
                }.start();

            }

        });

    }


    private void generateMovie(File outputFile) {
        try {
            prepareEncoder(outputFile);
            for (int i = 0; i < lottieDrawable.getMaxFrame(); i++) {
                drainEncoder(false);
                lottieDrawable.setFrame(i);
                generateFrame(lottieDrawable);
            }
            drainEncoder(true);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        } finally {
            releaseEncoder();
        }
    }

    public void generateFrame(final Drawable lottieDrawable) {
        // drainEncoder(false);
        Canvas canvas = mInputSurface.lockCanvas(null);
        //获得了canvas的时候一定要先清空一下canvas的内容，否则可能会出现前一帧图片重复显示而不消失的情况
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        try {
            lottieDrawable.draw(canvas);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //这个imageView其实是测试使用的，但是不知道为什么不加这个就会黑屏，可能setImageDrawable里面实现了什么方法
                    imageView.setImageDrawable(lottieDrawable);
                }
            });
        } finally {
            mInputSurface.unlockCanvasAndPost(canvas);
        }
    }

    Bitmap drawable2Bitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        } else if (drawable instanceof NinePatchDrawable) {
            Bitmap bitmap = Bitmap
                    .createBitmap(
                            drawable.getIntrinsicWidth(),
                            drawable.getIntrinsicHeight(),
                            drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
                                    : Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight());
            drawable.draw(canvas);
            return bitmap;
        } else {
            return null;
        }
    }

    private void drainEncoder(boolean endOfStream) {
        final int TIMEOUT_USEC = 10000;
        if (VERBOSE) Log.d(TAG, "drainEncoder(" + endOfStream + ")");

        if (endOfStream) {
            if (VERBOSE) Log.d(TAG, "sending EOS to encoder");
            mEncoder.signalEndOfInputStream();
        }
        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        while (true) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break;      // out of while
                } else {
                    if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS");
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                MediaFormat newFormat = mEncoder.getOutputFormat();
                Log.d(TAG, "encoder output format changed: " + newFormat);

                // now that we have the Magic Goodies, start the muxer
                mTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();
                mMuxerStarted = true;
            } else if (encoderStatus < 0) {
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);
                // let's ignore it
            } else {
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        throw new RuntimeException("muxer hasn't started");
                    }

                    // adjust the ByteBuffer values to match BufferInfo
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                    mBufferInfo.presentationTimeUs = mFakePts;
                    mFakePts += 1000000L / FRAMES_PER_SECOND;

                    mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    if (VERBOSE) Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer");
                }

                mEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                    } else {
                        if (VERBOSE) Log.d(TAG, "end of stream reached");
                    }
                    break;      // out of while
                }
            }
        }
    }

    private void releaseEncoder() {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects");
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    private void prepareEncoder(File outputFile) throws IOException {
        mBufferInfo = new MediaCodec.BufferInfo();
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, WIDTH, HEIGHT);

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAMES_PER_SECOND);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        if (VERBOSE) Log.d(TAG, "format: " + format);

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();

        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
        if (VERBOSE) Log.d(TAG, "output will go to " + outputFile);
        mMuxer = new MediaMuxer(outputFile.toString(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        mTrackIndex = -1;
        mMuxerStarted = false;
    }
}
