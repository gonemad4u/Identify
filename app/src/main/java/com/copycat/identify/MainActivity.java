// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2017 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

package com.copycat.identify;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity
{
    private static final int SELECT_IMAGE = 1;

    private TextView infoResult;
    private ImageView imageView;
    private Bitmap yourSelectedImage = null;
    private List<String> resultLabel = new ArrayList<>();
    private Identify identify = new Identify();

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        boolean ret_init = identify.Init(getAssets());
        if (!ret_init)
        {
            Log.e("MainActivity", "identify Init failed");
        }
        readCacheLabelFromLocalFile();
        infoResult = (TextView) findViewById(R.id.infoResult);
        imageView = (ImageView) findViewById(R.id.imageView);

        Button buttonImage = (Button) findViewById(R.id.buttonImage);
        buttonImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent i = new Intent(Intent.ACTION_PICK);
                i.setType("image/*");
                startActivityForResult(i, SELECT_IMAGE);
            }
        });

        Button buttonDetect = (Button) findViewById(R.id.buttonDetect);
        buttonDetect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (yourSelectedImage == null)
                    return;
                long start = System.currentTimeMillis();
                float[] result = identify.Detect(yourSelectedImage, false);
                long end = System.currentTimeMillis();
                long time = end - start;
                Log.d("DEBUG", result.toString());
                drawResults(result);
                String show_text = "KONOSUBA!" + "\nCPU detection time：" + time + "ms";
                infoResult.setText(show_text);
            }
        });

        Button buttonDetectGPU = (Button) findViewById(R.id.buttonDetectGPU);
        buttonDetectGPU.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (yourSelectedImage == null)
                    return;
                long start = System.currentTimeMillis();
                identify.Detect(yourSelectedImage, true);
                long end = System.currentTimeMillis();
                long time = end - start;
                Log.d("DEBUG", "AFTER");
                float[] result = identify.Detect(yourSelectedImage, true);
                drawResults(result);
                String show_text = "KONOSUBA!" + "\nGPU detection time：" + time + "ms";
                infoResult.setText(show_text);
            }
        });
    }

    // draws results
    private void drawResults(float[] result) {
        try {
            Bitmap rgba = yourSelectedImage.copy(Bitmap.Config.ARGB_8888, true);
            // init paint
            Canvas canvas = new Canvas(rgba);
            Paint paint = new Paint();

            float get_finalresult[][] = backToTwoArry(result);
            int object_num = 0;
            int num = result.length/6;// number of object
            // draw
            for(object_num = 0; object_num < num; object_num++){
                // draw on picture
                paint.setColor(Color.MAGENTA);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(5);
                canvas.drawRect(get_finalresult[object_num][2] * rgba.getWidth(), get_finalresult[object_num][3] * rgba.getHeight(),
                        get_finalresult[object_num][4] * rgba.getWidth(), get_finalresult[object_num][5] * rgba.getHeight(), paint);
                // draw label
                paint.setColor(Color.BLACK);
                paint.setStyle(Paint.Style.FILL);
                paint.setStrokeWidth(1);
                Log.d("HEIGHT", String.valueOf(get_finalresult[object_num][3]*rgba.getHeight() + 100));
                canvas.drawText(resultLabel.get((int) get_finalresult[object_num][0]),
                        get_finalresult[object_num][2]*rgba.getWidth(),get_finalresult[object_num][3]*rgba.getHeight()+10,paint);
                canvas.drawText( String.valueOf(get_finalresult[object_num][1]),
                        get_finalresult[object_num][2]*rgba.getWidth(),get_finalresult[object_num][3]*rgba.getHeight()+20,paint);
            }
            imageView.setImageBitmap(rgba);
        }
        catch (ArrayIndexOutOfBoundsException e){
            infoResult.setText("detect failed");
            Log.e("Array", e.getMessage());
        }
    }

    // load label's name
    private void readCacheLabelFromLocalFile() {
        try {
            AssetManager assetManager = getApplicationContext().getAssets();
            BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open("words.txt")));
            String readLine = null;
            while ((readLine = reader.readLine()) != null) {
                resultLabel.add(readLine);
            }
            reader.close();
            Log.e("labelCache", "good");
        } catch (Exception e) {
            Log.e("labelCache", "error " + e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        // after selecting picture
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();

            try
            {
                if (requestCode == SELECT_IMAGE) {
                    Bitmap bitmap = decodeUri(selectedImage);

                    Bitmap rgba = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                    // resize to 300*300 cause fitting training parameter
                    yourSelectedImage = Bitmap.createScaledBitmap(rgba, 300, 300, false);

                    rgba.recycle();

                    imageView.setImageBitmap(yourSelectedImage);
                }
            }
            catch (FileNotFoundException e)
            {
                Log.e("MainActivity", "FileNotFoundException" + e.getMessage());
            }
        }
    }

    private Bitmap decodeUri(Uri selectedImage) throws FileNotFoundException
    {
        // Decode image size
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage), null, o);

        // The new size we want to scale to
        final int REQUIRED_SIZE = 500;

        // Find the correct scale value. It should be the power of 2.
        int width_tmp = o.outWidth, height_tmp = o.outHeight;
        int scale = 1;
        while (true) {
            if (width_tmp / 2 < REQUIRED_SIZE
                    || height_tmp / 2 < REQUIRED_SIZE) {
                break;
            }
            width_tmp /= 2;
            height_tmp /= 2;
            scale *= 2;
        }

        // Decode with inSampleSize
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = scale;
        return BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage), null, o2);
    }

    // restore the one dimensional array back to two dimensional array
    public static float[][] backToTwoArry(float[] inputfloat){
        int num = inputfloat.length/6;

        float[][] outputfloat = new float[num][6];
        int k = 0;
        for(int i = 0; i < num ; i++)
        {
            int j = 0;

            while(j<6)
            {
                outputfloat[i][j] =  inputfloat[k];
                k++;
                j++;
            }
        }
        return outputfloat;
    }
}
