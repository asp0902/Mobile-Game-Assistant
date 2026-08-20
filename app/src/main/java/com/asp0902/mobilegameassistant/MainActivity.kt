package com.asp0902.mobilegameassistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asp0902.mobilegameassistant.capture.MediaProjectionService
import com.asp0902.mobilegameassistant.tracking.TrackingScreen
import com.asp0902.mobilegameassistant.tracking.TrackingViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: TrackingViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        requestProjectionConsent()
    }

    private val projectionConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            MediaProjectionService.start(this, result.resultCode, data)
        } else {
            viewModel.cancelRequest()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val frame by viewModel.frame.collectAsStateWithLifecycle()
            val template by viewModel.template.collectAsStateWithLifecycle()
            val analysis by viewModel.analysis.collectAsStateWithLifecycle()
            val detailSlot by viewModel.detailSlot.collectAsStateWithLifecycle()
            TrackingScreen(
                state = state,
                frame = frame,
                template = template,
                analysis = analysis,
                detailSlot = detailSlot,
                onStart = ::startTracking,
                onStop = { MediaProjectionService.stop(this) },
                onTemplateSelected = viewModel::selectFormationTemplate,
                onDetailSlotSelected = viewModel::selectDetailSlot,
            )
        }
    }

    private fun startTracking() {
        viewModel.requestTracking()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestProjectionConsent()
        }
    }

    private fun requestProjectionConsent() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        projectionConsent.launch(manager.createScreenCaptureIntent())
    }
}
