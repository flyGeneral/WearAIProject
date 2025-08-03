package com.fly.cameramodules.utils

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import kotlin.math.abs

class CameraModuleHelper(context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // 获取指定相机的支持分辨率
    fun getSupportedResolutions(cameraId: String, useCase: Int): List<Size> {
        return try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            when (useCase) {
                USE_CASE_PREVIEW -> {
                    // 预览分辨率
                    map?.getOutputSizes(SurfaceTexture::class.java)?.toList() ?: emptyList()
                }
                USE_CASE_CAPTURE -> {
                    // 拍照分辨率
                    map?.getOutputSizes(ImageFormat.JPEG)?.toList() ?: emptyList()
                }
                USE_CASE_ANALYSIS -> {
                    // 图像分析分辨率
                    map?.getOutputSizes(ImageFormat.YUV_420_888)?.toList() ?: emptyList()
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 获取最佳匹配分辨率
    @ExperimentalCamera2Interop
    fun getOptimalResolution(
        cameraInfo: CameraInfo,
        useCase: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Size? {
        val cameraId = Camera2CameraInfo.from(cameraInfo).cameraId
        val supportedSizes = getSupportedResolutions(cameraId, useCase)

        return if (supportedSizes.isEmpty()) {
            Size(targetWidth, targetHeight) // 默认值
        } else {
            // 计算最接近目标的分辨率
            supportedSizes.minByOrNull {
                abs(it.width - targetWidth) + abs(it.height - targetHeight)
            }
        }
    }

    companion object {
        const val USE_CASE_PREVIEW = 0
        const val USE_CASE_CAPTURE = 1
        const val USE_CASE_ANALYSIS = 2
    }
}