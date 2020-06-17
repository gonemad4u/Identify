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
import java.util.Arrays;
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

                try {
                    float[] result = identify.Detect(yourSelectedImage, false);
                    float[] r = get_max_result(result);
                    String show_text = "result：" + Arrays.toString(r) + "\nname：" + resultLabel.get((int) r[0]) + "\nprobability：" + r[1];
                    infoResult.setText(show_text);
                    Bitmap rgba = yourSelectedImage.copy(Bitmap.Config.ARGB_8888, true);
                    Canvas canvas = new Canvas(rgba);
                    //图像上画矩形
                    Paint paint = new Paint();
                    paint.setColor(Color.RED);
                    paint.setStyle(Paint.Style.STROKE);//不填充
                    paint.setStrokeWidth(10); //线的宽度
                    canvas.drawRect(r[2]*rgba.getWidth(), r[3]*rgba.getHeight(), r[4]*rgba.getWidth(), r[5]*rgba.getHeight(), paint);
                    imageView.setImageBitmap(rgba);
                }
                catch (ArrayIndexOutOfBoundsException e){
                    infoResult.setText("detect failed");
                }
            }
        });

        Button buttonDetectGPU = (Button) findViewById(R.id.buttonDetectGPU);
        buttonDetectGPU.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (yourSelectedImage == null)
                    return;
                try {
                    float[] result = identify.Detect(yourSelectedImage, true);
                    Bitmap rgba = yourSelectedImage.copy(Bitmap.Config.ARGB_8888, true);
                    float[] r = get_max_result(result);
                    String show_text = "result：" + Arrays.toString(r) + "\nname：" + resultLabel.get((int) r[0]) + "\nprobability：" + r[1];
                    infoResult.setText(show_text);

                    Canvas canvas = new Canvas(rgba);
                    //图像上画矩形
                    Paint paint = new Paint();
                    paint.setColor(Color.RED);
                    paint.setStyle(Paint.Style.STROKE);//不填充
                    paint.setStrokeWidth(10); //线的宽度
                    canvas.drawRect(r[2]*rgba.getWidth(), r[3]*rgba.getHeight(), r[4]*rgba.getWidth(), r[5]*rgba.getHeight(), paint);
                    imageView.setImageBitmap(rgba);

                }
                catch (ArrayIndexOutOfBoundsException e){
                    infoResult.setText("detect failed");
                }


                {

                    //infoResult.setText(result);
                }
            }
        });
    }

    // load label's name
    private void readCacheLabelFromLocalFile() {
        try {
            AssetManager assetManager = getApplicationContext().getAssets();
            BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open("words.txt")));//这里是label的文件
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
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();

            try
            {
                if (requestCode == SELECT_IMAGE) {
                    Bitmap bitmap = decodeUri(selectedImage);

                    Bitmap rgba = bitmap.copy(Bitmap.Config.ARGB_8888, true);

                    // resize to 227x227
                    yourSelectedImage = Bitmap.createScaledBitmap(rgba, 300, 300, false);

                    rgba.recycle();

                    //imageView.setImageBitmap(bitmap);
                }
            }
            catch (FileNotFoundException e)
            {
                Log.e("MainActivity", "FileNotFoundException");
                return;
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
        final int REQUIRED_SIZE = 400;

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

    // get max probability label
    private float[] get_max_result(float[] result) {
        Log.e("get_max_result", "ENTERED");
        float[] ret = {0,0,0,0,0,0};
        if (result.length == 0) {
            return ret;
        }
        int num_rs = result.length / 6;
        float maxProp = result[1];
        int maxI = 0;
        for(int i = 1; i<num_rs;i++){
            if(maxProp<result[i*6+1]){
                maxProp = result[i*6+1];
                maxI = i;
            }
        }

        for(int j=0;j<6;j++){
            ret[j] = result[maxI*6 + j];
        }
        return ret;
    }
}
