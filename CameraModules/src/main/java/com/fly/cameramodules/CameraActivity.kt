package com.fly.cameramodules

import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fly.cameramodules.utils.CameraModuleHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {
    private var CAM_TAG: String = "CameraModule"
    private val REQUIRED_PERMISSIONS = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null
    private var bitmapImageView: ImageView? = null

    private lateinit var cameraModuleHelper: CameraModuleHelper
    private lateinit var previewSelector: ResolutionSelector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        cameraModuleHelper = CameraModuleHelper(this)

        // initWidget
        bitmapImageView = findViewById<ImageView>(R.id.imagePreview)

        // 1. 初始化视图和线程池
        previewView = findViewById(R.id.cameraContainer) as? PreviewView
        cameraExecutor = Executors.newSingleThreadExecutor()

        // 2. 申请权限
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, 101)
        }

        // 3. 拍照按钮监听
        findViewById<Button>(R.id.captureButton).setOnClickListener {
            takePhoto()
        }
    }

    // 启动相机
    @OptIn(ExperimentalCamera2Interop::class)
    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val cameraInfo = cameraProvider.getCameraInfo(cameraSelector)
            val cameraId = Camera2CameraInfo.from(cameraInfo).cameraId
            val previewSizes = cameraModuleHelper.getSupportedResolutions(cameraId, CameraModuleHelper.USE_CASE_CAPTURE)
            val optimalPreviewSize = cameraModuleHelper.getOptimalResolution(cameraInfo, CameraModuleHelper.USE_CASE_CAPTURE, 500, 400)
            Log.d(CAM_TAG, "支持的预览分辨率: $previewSizes")
            if (optimalPreviewSize != null) {
                Log.d(CAM_TAG, "选择的预览分辨率: ${optimalPreviewSize.width} ${optimalPreviewSize.height}")
                previewSelector = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(optimalPreviewSize.width, optimalPreviewSize.height), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                    .build()
            }

            val preview = Preview.Builder().setResolutionSelector(previewSelector).build().also {
                it.setSurfaceProvider(previewView?.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // 绑定生命周期
            cameraProvider.bindToLifecycle(
                this, cameraSelector,
                preview, imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }

    // 拍照逻辑
    private fun takePhoto() {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraDemo")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        imageCapture?.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@CameraActivity, "保存成功", Toast.LENGTH_SHORT).show()
//                    output.savedUri?.let { showImage(it) }
                    outputFileResults.savedUri?.let { showImage(it) }
                }

                override fun onError(ex: ImageCaptureException) {
                    Log.e(CAM_TAG, "拍照失败: ${ex.message}")
                }
            })
    }

    // 显示图片
    private fun showImage(uri: Uri) {
        bitmapImageView?.setImageURI(uri)
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    // 权限回调
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 101 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }
}