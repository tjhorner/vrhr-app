package tj.horner.vrhr

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.PowerManager
import android.support.wearable.activity.WearableActivity
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.ambient.AmbientModeSupport
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.concurrent.thread

class MainActivity : WearableActivity(), SensorEventListener, CapabilityClient.OnCapabilityChangedListener {
    private val PERMISSION_REQUEST_VITALS = 6969 // ha ha :-)

    private var currentAccuracy = SensorManager.SENSOR_STATUS_NO_CONTACT
    private var dimMode = false
    private var sensorManager: SensorManager? = null

    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var availableNodes: Set<Node>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setAmbientEnabled()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VRHR::RunningWakeLock").apply {
                acquire()
            }
        }

        dim_button.setOnClickListener {
            blackout.visibility = View.VISIBLE
            dimMode = true
            Toast.makeText(this, R.string.tap_hint, Toast.LENGTH_LONG).show()
        }

        blackout.setOnClickListener {
            blackout.visibility = View.GONE
            dimMode = false
        }

        exit_button.setOnClickListener {
            cleanUp() // This is probably unnecessary since it's in onStop, but whatever
            finishAffinity()
        }

        if(checkPermissions()) registerHeartRateListener()
        updateNodes()
    }

    private fun registerHeartRateListener() {
        sensorManager = getSystemService(Service.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        sensorManager?.registerListener(this, sensor, 1000000)
    }

    private fun cleanUp() {
        wakeLock.release()
        sensorManager?.unregisterListener(this)
    }

    override fun onStop() {
        cleanUp()
        super.onStop()
    }

    private fun updateNodes() {
        thread {
            val capabilityInfo = Tasks.await(
                Wearable.getCapabilityClient(this).getCapability(Constants.VITALS_TRACKING_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            )

            capabilityInfo.nodes.forEach {
                Log.d("CapabilityInfo", "${it.id} - ${it.displayName} (nearby: ${it.isNearby})")
            }

            runOnUiThread {
                availableNodes = capabilityInfo.nodes
            }
        }

        Wearable.getCapabilityClient(this).addListener(this, Constants.VITALS_TRACKING_CAPABILITY)
    }

    private fun sendMessageToNodes(hr: Int, acc: Int) {
        val msg = "$hr,$acc".toByteArray()

        availableNodes.forEach { node ->
            thread {
                Wearable.getMessageClient(this).sendMessage(
                    node.id,
                    Constants.VITALS_TRACKING_CAPABILITY_PATH,
                    msg
                ).apply {
                    addOnSuccessListener {
                        Log.d("MessageSend", "Sent message to ${node.displayName}")
                    }

                    addOnFailureListener {
                        Log.w("MessageSend", "Failed to send message to ${node.displayName}")
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode != PERMISSION_REQUEST_VITALS) return

        if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            registerHeartRateListener()
        } else {
            Toast.makeText(this, R.string.permissions_bad, Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPermissions(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BODY_SENSORS)) {
                text.text = getString(R.string.permissions_bad)
                dim_button.visibility = View.GONE
                false
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BODY_SENSORS), PERMISSION_REQUEST_VITALS)
                false
            }
        } else {
            true
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type != Sensor.TYPE_HEART_RATE) return
        currentAccuracy = accuracy
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event!!.sensor.type != Sensor.TYPE_HEART_RATE) return
        Log.d("HeartRate", "${event.values[0]}, accuracy: $currentAccuracy")

        sendMessageToNodes(event.values[0].toInt(), currentAccuracy)
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        Log.d("CapabilityInfo", "Capability changed. There are now ${capabilityInfo.nodes.count()} nodes available.")
        availableNodes = capabilityInfo.nodes
    }

    override fun onEnterAmbient(ambientDetails: Bundle?) {
        super.onEnterAmbient(ambientDetails)
        blackout.visibility = View.VISIBLE
    }

    override fun onExitAmbient() {
        super.onExitAmbient()
        if(!dimMode) blackout.visibility = View.GONE
    }

    override fun onUpdateAmbient() {
        super.onUpdateAmbient()
        if(isAmbient) blackout.visibility = View.VISIBLE
    }
}
