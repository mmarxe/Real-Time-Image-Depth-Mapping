package com.macmaxwell.depthmap;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;


import com.google.common.util.concurrent.ListenableFuture;


import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private Button BTakePicture;
    private Button BRecord;
    PreviewView previewView;
    ImageView depthView;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BTakePicture = findViewById(R.id.image_capture_button);
        BRecord = findViewById(R.id.video_capture_button);
        previewView = findViewById(R.id.viewFinder);
        depthView = findViewById(R.id.grayView);
        depthView.setVisibility(View.GONE);


        try {
            this.prepareModelFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Log.d("ModelLoaded", "Model was successfully loaded into the file system");

        BTakePicture.setOnClickListener(this);
        BRecord.setOnClickListener(this) ;

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener( () -> {
            try{
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                startCameraX(cameraProvider);
            }catch (ExecutionException e){
                e.printStackTrace();
            } catch(InterruptedException e){
                e.printStackTrace();
            }
        }, getExecutor());
    }

    private void startCameraX(ProcessCameraProvider cameraProvider) {
            cameraProvider.unbindAll();

            //Camera selector use case
        CameraSelector cameraSelector= new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        // Preview use case
        Preview preview = new Preview.Builder().build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Image Capture Use Case

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        //Image Analysis Use Case

        imageAnalysis =
                new ImageAnalysis.Builder()
                        // enable the following line if RGBA output is needed.
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
//                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();


        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture, imageAnalysis);
    }

    private Executor getExecutor() {
            return ContextCompat.getMainExecutor(this);
    }

    @Override
    public void onClick(View view){
        if (view.getId() == R.id.image_capture_button){
            capturePhoto();
        } else if(view.getId() == R.id.video_capture_button){
            captureDepth();
            depthView.setVisibility(View.VISIBLE);
        }else{
            System.out.println("Done");
        }
    }

    private void prepareModelFile() throws IOException {
        AssetManager assetManager = getAssets();
        String configDir = getFilesDir().getAbsolutePath();
        InputStream stream = assetManager.open("model.ptl");
        String mTFLiteModelFile = configDir +"/model.ptl";
        OutputStream output = new BufferedOutputStream(new FileOutputStream(mTFLiteModelFile));
        copyFile(stream, output);
    }

    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        in.close();;
        out.close();
    }

    private void captureDepth(){
        imageAnalysis.setAnalyzer(getExecutor(), new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                try {
                    Bitmap processed = processImage(imageProxy);
                    Bitmap finalprocessed = drawDepthMapOverlay(processed);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            depthView.setImageBitmap(finalprocessed);
                        }
                    });

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                imageProxy.close();
            }
        });
    }

    private Bitmap drawDepthMapOverlay(Bitmap bitmap) {
        Bitmap bmpGrayscale = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        canvas.drawBitmap(bitmap,0,0, paint);
        return bmpGrayscale;
    }

    private void capturePhoto() {
        File photoDir = new File("/mnt/sdcard/Pictures/CameraXPhotos");

        if(!photoDir.exists()){
            photoDir.mkdir();
        }

        Date date = new Date();
        String timestamp = String.valueOf(date.getTime());
        String photoFilePath = photoDir.getAbsolutePath() + "/"+ timestamp+".jpg";
        File photoFile = new File(photoFilePath);
        imageCapture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(photoFile).build(),
                getExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Toast.makeText(MainActivity.this, "Photo has been saved", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(MainActivity.this, "Error saving photo"+ exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    public Bitmap processImage(ImageProxy imageProxy) throws IOException {
        ImageProxy.PlaneProxy[] planeProxies = imageProxy.getPlanes();
        ByteBuffer rgba = planeProxies[0].getBuffer();
        byte[] arr = new byte[rgba.remaining()];
        rgba.get(arr);
        
        final long[] shape = new long[]{1, 3, 640, 480};
        float[] intbuffer = bitmapFromRgba(arr);
        Module module = LiteModuleLoader.load(getFilesDir().getAbsolutePath()+"/model.ptl");
        Tensor inputTensor = Tensor.fromBlob(intbuffer, shape);
        float[] outputTensor = module.forward(IValue.from(inputTensor)).toTensor().getDataAsFloatArray();

        
        int[] processed_pixels = new int[outputTensor.length];
        int j = 0;
        for (int i = 0; i < outputTensor.length; i+=3) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

                int R = (int) outputTensor[i];
                int G = (int) outputTensor[i + 1];
                int B = (int) outputTensor[i + 2];

                R = (R << 16) & 0x00FF0000;
                G = (G << 8) & 0x0000FF00;
                B = B & 0x000000FF;

                processed_pixels[j] = Math.abs(0xFF000000 | R | G | B);
            }
            j+=1;
        }
//        for(int i =0; i<10;i++){
//            System.out.println(processed_pixels[i]);
//        }

        int height = 640;
        int width = 480;

        Bitmap bitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888);
        int offset = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
//                bitmap.setPixel(col, row, processed_pixels[offset]);
                offset = offset + 1;
            }
        }
        bitmap.copyPixelsFromBuffer(IntBuffer.wrap(processed_pixels));
//        bitmap.setPixels(processed_pixels, 0, width, 0,0,width, height);
        return bitmap;
    }



    public static float[] bitmapFromRgba(byte[] bytes) {
        float pixels[] = new float[bytes.length - bytes.length/4];
        int j = 0;
        pixels[0] = (float) bytes[0];
        for(int i = 1; i < bytes.length; i++){
           if(i % 3 == 0){
               continue;
           }
           pixels[j++] = (float) (bytes[i] & 0xff);
        }
//        for(int i =0; i<10;i++){
//            System.out.println((float)(bytes[i] & 0xff));
//        }
        return pixels;
    }
}


//        System.out.println(pixels.length);
//        System.out.println(pixels[0]+" "+ pixels[1]+" " + pixels[2]);

// Load the model from the assets directory

//        FileInputStream initialStream = (FileInputStream) getAssets().open("model.tflite");
//        ByteBuffer byteBuffer = ByteBuffer.allocate(initialStream.available());
//        ByteOrder.nativeOrder();
//        while (initialStream.available() > 0) {
//            byteBuffer.put((byte) initialStream.read());
//        }

//    FileInputStream inputStream = new FileInputStream(new File(getFilesDir().getAbsolutePath()+"/model.tflite"));
//    FileChannel fileChannel = inputStream.getChannel();
//    MappedByteBuffer myMappedBuffer =  fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
//
//        Interpreter interpreter = new Interpreter(myMappedBuffer);
//
//        TensorImage inputImage = TensorImage.fromBitmap(imageProxy.toBitmap());
//
//        ImageProcessor processor = new ImageProcessor.Builder()
//                .add(new CastOp(DataType.FLOAT32))
//                .add(new NormalizeOp(100, 100))
//                .build();
//
//        processor.process(inputImage);
//
//        TensorImage outputImage = new TensorImage(DataType.FLOAT32);
//
//        interpreter.run(inputImage.getBuffer(), outputImage);
//
//        // Get the output tensor data as a float array
////        float[] outputData = outputTensor.getDataAsFloatArray();