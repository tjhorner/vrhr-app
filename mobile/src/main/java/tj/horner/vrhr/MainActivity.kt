package tj.horner.vrhr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("vrhr_config", Context.MODE_PRIVATE)

        server_url.setText(prefs.getString("server_url", ""))

        save_button.setOnClickListener {
            with(prefs.edit()) {
                putString("server_url", server_url.text.toString())
                apply()
            }

            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if(intent == null) return
                current_hr.text = "HR: ${intent.getIntExtra("heartRate", 0)}, ACC: ${intent.getIntExtra("accuracy", 0)}"
            }
        }, IntentFilter("HeartRateUpdate"))
    }
}
