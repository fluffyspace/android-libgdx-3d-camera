package com.mygdx.game.activities

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.mygdx.game.BuildConfig
import com.mygdx.game.network.OsmAuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OsmOAuthActivity : ComponentActivity() {

    companion object {
        private const val TAG = "OsmOAuthActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        if (uri == null) {
            Log.e(TAG, "No URI in intent")
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val code = uri.getQueryParameter("code")
        if (code == null) {
            val error = uri.getQueryParameter("error")
            Log.e(TAG, "No code in callback, error: $error")
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val authManager = OsmAuthManager(this, BuildConfig.OSM_CLIENT_ID)

        CoroutineScope(Dispatchers.Main).launch {
            val success = authManager.exchangeCodeForToken(code)
            if (success) {
                Log.d(TAG, "OAuth token exchange successful")
                setResult(RESULT_OK)
            } else {
                Log.e(TAG, "OAuth token exchange failed")
                setResult(RESULT_CANCELED)
            }
            finish()
        }
    }
}
