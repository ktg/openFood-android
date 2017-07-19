package uk.ac.nott.mrl.openfood.playback

import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.support.v4.app.NavUtils
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import com.mbientlab.metawear.android.BtleService
import com.mbientlab.metawear.data.Acceleration
import com.mbientlab.metawear.module.Accelerometer
import com.mbientlab.metawear.module.Led
import kotlinx.android.synthetic.main.activity_playback.*
import uk.ac.nott.mrl.openfood.R
import java.io.File

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class PlaybackActivity : AppCompatActivity(), ServiceConnection {
	companion object {
		private val TAG = PlaybackActivity::class.java.simpleName
		/**
		 * Whether or not the system UI should be auto-hidden after
		 * [AUTO_HIDE_DELAY_MILLIS] milliseconds.
		 */
		private val AUTO_HIDE = true

		/**
		 * If [AUTO_HIDE] is set, the number of milliseconds to wait after
		 * user interaction before hiding the system UI.
		 */
		private val AUTO_HIDE_DELAY_MILLIS = 3000

		/**
		 * Some older devices needs a small delay between UI widget updates
		 * and a change of the status and navigation bar.
		 */
		private val UI_ANIMATION_DELAY = 300
	}

	private val devices = mutableListOf<PlaybackSensor>()
	private val hideHandler = Handler()
	private val mHidePart2Runnable = Runnable {
		// Delayed removal of status and navigation bar

		// Note that some of these constants are new as of API 16 (Jelly Bean)
		// and API 19 (KitKat). It is safe to use them, as they are inlined
		// at compile-time and do nothing on earlier devices.
		videoView.systemUiVisibility =
				View.SYSTEM_UI_FLAG_LOW_PROFILE or
						View.SYSTEM_UI_FLAG_FULLSCREEN or
						View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
						View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
						View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
						View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
	}
	private val mShowPart2Runnable = Runnable {
		// Delayed display of UI elements
		supportActionBar?.show()
	}
	private var visibleUI: Boolean = false
	private val hideRunnable = Runnable { hide() }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_playback)
		setSupportActionBar(toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)

		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

		visibleUI = true

		background.setOnClickListener {
			Log.i(TAG, "Toggle " + visibleUI)
			if (visibleUI) {
				hide()
			} else {
				show()
			}
		}
		val uri = Uri.fromFile(File(intent.getStringExtra("video")))
		Log.i(TAG, uri.toString())
		videoView.setOnErrorListener { _, what, extra ->
			Log.e(TAG, "VideoView error: $what, extra: $extra")
			videoView.stopPlayback()
			true
		}

		videoView.setVideoURI(uri)
		intent.getStringArrayExtra("addresses").mapTo(devices) { PlaybackSensor(it) }
	}

	private var bluetoothLEService: BtleService.LocalBinder? = null

	override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
		bluetoothLEService = service as BtleService.LocalBinder
		val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

		for (device in devices) {
			val bleDevice = bluetoothManager.adapter.getRemoteDevice(device.address)
			device.board = bluetoothLEService?.getMetaWearBoard(bleDevice)
			connect(device)
		}
	}

	override fun onServiceDisconnected(name: ComponentName?) {
		bluetoothLEService = null
		for (device in devices) {
			disconnect(device)
		}
	}

	override fun onStart() {
		super.onStart()
		videoView.start()
		bindService(Intent(this, BtleService::class.java), this, Context.BIND_AUTO_CREATE)
	}

	override fun onStop() {
		super.onStop()
		if (bluetoothLEService != null) {
			unbindService(this)
		}
	}

	private fun connect(playbackSensor: PlaybackSensor) {
		Log.i(TAG, playbackSensor.address + " connecting")
		playbackSensor.board?.let {
			it.connectAsync(1000)?.continueWith { task ->
				if (task.isFaulted) {
					Log.i(TAG, playbackSensor.address + " connection failed: " + task.error.localizedMessage)
					if (bluetoothLEService != null) {
						connect(playbackSensor)
					} else {

					}
				} else {
					Log.i(TAG, playbackSensor.address + " connected")
					val accelerometer = it.getModule(Accelerometer::class.java)
					accelerometer.configure()
							.odr(10f)
							.commit()
					accelerometer.acceleration()
							.addRouteAsync { source ->
								source.stream { data, _ ->
									val casted = data.value(Acceleration::class.java)
									playbackSensor.update(casted)
									var moving = false
									for (adevice in devices) {
										val timestamp = System.currentTimeMillis()
										moving = moving || adevice.isMoving(timestamp)
									}
									if (moving) {
										if (!videoView.isPlaying) {
											videoView.start()
										}
									} else {
										videoView.pause()
									}
								}
							}
							.continueWith { _ ->
								accelerometer.packedAcceleration().start()
								accelerometer.start()
							}
				}
			}
		}
	}

	private fun disconnect(playbackSensor: PlaybackSensor) {
		if (playbackSensor.isConnected) {
			Log.i(TAG, "Disconnecting from " + playbackSensor.address)
			playbackSensor.board?.tearDown()
			playbackSensor.board?.getModule(Led::class.java)?.stop(true)
			playbackSensor.board?.disconnectAsync()
			playbackSensor.board = null
		}
	}

	override fun onPostCreate(savedInstanceState: Bundle?) {
		super.onPostCreate(savedInstanceState)

		// Trigger the initial hide() shortly after the activity has been
		// created, to briefly hint to the user that UI controls
		// are available.
		delayedHide(100)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		val id = item.itemId
		if (id == android.R.id.home) {
			// This ID represents the Home or Up button.
			NavUtils.navigateUpFromSameTask(this)
			return true
		}
		return super.onOptionsItemSelected(item)
	}

	private fun hide() {
		// Hide UI first
		supportActionBar?.hide()
		visibleUI = false

		// Schedule a runnable to remove the status and navigation bar after a delay
		hideHandler.removeCallbacks(mShowPart2Runnable)
		hideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY.toLong())
	}

	private fun show() {
		// Show the system bar
		videoView.systemUiVisibility =
				View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
						View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
		visibleUI = true

		// Schedule a runnable to display UI elements after a delay
		hideHandler.removeCallbacks(mHidePart2Runnable)
		hideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY.toLong())
	}

	/**
	 * Schedules a call to hide() in [delayMillis], canceling any
	 * previously scheduled calls.
	 */
	private fun delayedHide(delayMillis: Int) {
		hideHandler.removeCallbacks(hideRunnable)
		hideHandler.postDelayed(hideRunnable, delayMillis.toLong())
	}
}
