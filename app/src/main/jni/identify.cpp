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

#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>

// ncnn
#include "net.h"
#include "benchmark.h"

#include "konosuba.id.h"

static ncnn::UnlockedPoolAllocator g_blob_pool_allocator;
static ncnn::PoolAllocator g_workspace_pool_allocator;
static ncnn::Net konosuba;

extern "C" {

    JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
    {
        __android_log_print(ANDROID_LOG_DEBUG, "Identify", "JNI_OnLoad");

        ncnn::create_gpu_instance();

        return JNI_VERSION_1_4;
    }

    JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
    {
        __android_log_print(ANDROID_LOG_DEBUG, "Identify", "JNI_OnUnload");

        ncnn::destroy_gpu_instance();
    }

    // public native boolean Init(AssetManager mgr);
    JNIEXPORT jboolean JNICALL Java_com_copycat_identify_Identify_Init(JNIEnv* env, jobject thiz, jobject assetManager)
    {
        ncnn::Option opt;
        opt.lightmode = true;
        opt.num_threads = 4;
        opt.blob_allocator = &g_blob_pool_allocator;
        opt.workspace_allocator = &g_workspace_pool_allocator;

        // use vulkan compute
        if (ncnn::get_gpu_count() != 0)
            opt.use_vulkan_compute = true;

        AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

        konosuba.opt = opt;

        // init param
        {
            int ret = konosuba.load_param_bin(mgr, "konosuba.param.bin");
            if (ret != 0)
            {
                __android_log_print(ANDROID_LOG_DEBUG, "Identify", "load_param_bin failed");
                return JNI_FALSE;
            }
        }

        // init bin
        {
            int ret = konosuba.load_model(mgr, "konosuba.bin");
            if (ret != 0)
            {
                __android_log_print(ANDROID_LOG_DEBUG, "Identify", "load_model failed");
                return JNI_FALSE;
            }
        }
        return JNI_TRUE;
    }

    // public native String Detect(Bitmap bitmap, boolean use_gpu);
    JNIEXPORT jfloatArray JNICALL Java_com_copycat_identify_Identify_Detect(JNIEnv* env, jobject thiz, jobject bitmap, jboolean use_gpu)
    {
        if (use_gpu == JNI_TRUE && ncnn::get_gpu_count() == 0)
        {
            return nullptr;
            //return env->NewStringUTF("no vulkan capable gpu");
        }

        AndroidBitmapInfo info;
        AndroidBitmap_getInfo(env, bitmap, &info);
        int width = info.width;
        int height = info.height;
        // transform size in transform_param in training prototxt
        if (width != 300 || height != 300)
            return nullptr;
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
            return nullptr;

        // ncnn from bitmap
        ncnn::Mat in = ncnn::Mat::from_android_bitmap(env, bitmap, ncnn::Mat::PIXEL_BGR);

        // ncnn calculation
        std::vector<float> cls_scores;
        {
            // equals parameters in transform_param in training prototxt, mean values and scale
            const float mean_vals[3] = {127.f, 127.f, 127.f};
            const float scale[3] = {0.007843f, 0.007843f, 0.007843f};
            in.substract_mean_normalize(mean_vals, scale);

            ncnn::Extractor ex = konosuba.create_extractor();

            ex.set_vulkan_compute(use_gpu);

            ex.input(konosuba_param_id::BLOB_data, in);

            ncnn::Mat out;
            ex.extract(konosuba_param_id::BLOB_detection_out, out);

            // width represents score, height represents index, channel is just channel,
            // TODO not sure what determines channel number, maybe just because it's pictures so it's one channel
            int output_wsize = out.w;
            int output_hsize = out.h;
            int output_csize = out.c;
            __android_log_print(ANDROID_LOG_DEBUG, "Identify", "%s", (std::to_string (output_wsize)).c_str());
            __android_log_print(ANDROID_LOG_DEBUG, "Identify", "%s", (std::to_string (output_hsize)).c_str());
            __android_log_print(ANDROID_LOG_DEBUG, "Identify", "%s", (std::to_string (output_csize)).c_str());
            __android_log_print(ANDROID_LOG_DEBUG, "Identify", "1111111");

            // since there are multiple outputs, output needs to be resized to one-dimensional
            // maybe directly outputting two-dimensional is possible
            cls_scores.resize(out.w);

            jfloat *output[output_wsize * output_hsize];
            for(int i = 0; i< out.w; i++) {
                output[i] = &out[i];
            }

            jfloatArray jOutputData = env->NewFloatArray(output_wsize * output_hsize);
            if (jOutputData == nullptr) return nullptr;
            env->SetFloatArrayRegion(jOutputData, 0,  output_wsize * output_hsize,
                                     reinterpret_cast<const jfloat *>(*output));
            __android_log_print(ANDROID_LOG_DEBUG, "Identify", "2222222222");
            return jOutputData;

        }
    }
}
