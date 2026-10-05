package com.lionel.hub

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Invisible activity: asks for the device PIN / pattern / fingerprint, then opens the locked app. */
class LionelLockActivity : ComponentActivity() {
    private var targetPackage: String = ""

    private val authLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                LionelGate.markUnlocked(targetPackage)
                launchTarget()
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        if (pkg == null) {
            finish()
            return
        }
        targetPackage = pkg
        if (savedInstanceState != null) return // re-created while the prompt is showing

        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isDeviceSecure) {
            LionelGate.toast(this, "Set a screen lock to use app lock")
            launchTarget()
            finish()
            return
        }

        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }

        @Suppress("DEPRECATION")
        val authIntent = km.createConfirmDeviceCredentialIntent("Lionel Launcher", "Unlock $label")
        if (authIntent == null) {
            launchTarget()
            finish()
            return
        }
        authLauncher.launch(authIntent)
    }

    private fun launchTarget() {
        val launch = packageManager.getLaunchIntentForPackage(targetPackage) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        LionelStore.get(this).recordLaunch(targetPackage)
        startActivity(launch)
    }

    companion object {
        const val EXTRA_PACKAGE = "lionel_package"
    }
}
