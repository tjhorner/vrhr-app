package tj.horner.vrhr

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.kittinunf.fuel.Fuel
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.nio.charset.Charset
import kotlin.concurrent.thread

class HeartRateListenerService : WearableListenerService() {
    override fun onMessageReceived(message: MessageEvent?) {
        val prefs = getSharedPreferences("vrhr_config", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "")

        Log.d("HeartRateListenerService", "Recv'd msg: ${message!!.data.toString(Charsets.UTF_8)}")
        val unpacked = unpackMessage(message.data)
        Log.d("HeartRateListenerService", "Unpacked message: HR: ${unpacked[0]}, ACC: ${unpacked[1]}")

        val intent = Intent("HeartRateUpdate")
        intent.putExtra("heartRate", unpacked[0])
        intent.putExtra("accuracy", unpacked[1])
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

        if(serverUrl == "") {
            Log.d("HeartRateListenerService", "Received msg, but server URL is blank so not going to do anything.")
            return
        }

        Log.d("HeartRateListenerService", "Sending message to configured server: $serverUrl")

        thread {
            Fuel.put(serverUrl!!, listOf(
                "heartRate" to unpacked[0],
                "accuracy" to unpacked[1]
            )).responseString { request, response, result ->
                Log.d("HeartRateListenerService", "Server response (${response.statusCode}: $result")
            }
        }
    }

    private fun unpackMessage(msg: ByteArray): List<Int> =msg.toString(Charsets.UTF_8).split(",").map { it.toInt() }
}