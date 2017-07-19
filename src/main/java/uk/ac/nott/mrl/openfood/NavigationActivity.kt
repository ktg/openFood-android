package uk.ac.nott.mrl.openfood

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.support.design.widget.NavigationView
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import com.mbientlab.metawear.android.BtleService
import com.mbientlab.metawear.data.Acceleration
import com.mbientlab.metawear.data.AngularVelocity
import com.mbientlab.metawear.module.Accelerometer
import com.mbientlab.metawear.module.GyroBmi160
import com.mbientlab.metawear.module.Led
import com.mbientlab.metawear.module.Settings
import kotlinx.android.synthetic.main.activity_navigation.*
import uk.ac.nott.mrl.openfood.sensor.Sensor
import uk.ac.nott.mrl.openfood.sensor.SensorClickListener
import uk.ac.nott.mrl.openfood.sensor.SensorListAdapterHolder
import uk.ac.nott.mrl.openfood.sensor.SensorListAdapter
import uk.ac.nott.mrl.openfood.logging.DeviceListFragment
import uk.ac.nott.mrl.openfood.logging.DeviceLogger
import uk.ac.nott.mrl.openfood.playback.PlaybackCreatorActivity
import java.util.*


class NavigationActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, ServiceConnection, SensorListAdapterHolder {
	companion object {
		val PREF_ID = "OF-LOG-PREFS"
		val PREF_LOGGED = "LOGGED_ADDRESSES"
		val PREF_PLAYBACK = "PLAYBACK_ADDRESSES"
		private val PERMISSION_CODE = 4572
		private val TAG = NavigationActivity::class.java.simpleName
	}

	private var bluetoothLEService: BtleService.LocalBinder? = null
	private val logger = DeviceLogger()
	override val adapter = SensorListAdapter()

	override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
		bluetoothLEService = service as BtleService.LocalBinder
		loggingButton.isEnabled = true
	}

	override fun onServiceDisconnected(name: ComponentName?) {
		bluetoothLEService = null
		loggingButton.isEnabled = false
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_navigation)
		setSupportActionBar(toolbar)

		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

		loggingButton.setOnClickListener { _ ->
			if (logger.isLogging) {
				stopLogging()
			} else {
				startLogging()
			}
		}
		adapter.clickListener = object : SensorClickListener {
			override fun onClick(sensor: Sensor) {
				getSharedPreferences(PREF_ID, 0).edit().putStringSet(PREF_LOGGED, adapter.getSelected()).apply()
			}
		}
		adapter.longClickListener = object : SensorClickListener {
			override fun onClick(sensor: Sensor) {
				val builder = AlertDialog.Builder(this@NavigationActivity)
				val input = EditText(this@NavigationActivity)
				input.setText(sensor.name)
				builder.setTitle("Rename " + sensor.name)
						.setView(input)
						.setPositiveButton("Rename", { dialog, _ ->
							if (sensor.board == null) {
								val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
								val bleDevice = bluetoothManager.adapter.getRemoteDevice(sensor.address)
								sensor.board = bluetoothLEService?.getMetaWearBoard(bleDevice)
							}

							sensor.board?.getModule(Settings::class.java)
									?.editBleAdConfig()
									?.deviceName(input.text.toString())
									?.commit()

							dialog.dismiss()
						})
						.setNegativeButton("Cancel", { dialog, _ ->
							dialog.cancel()
						})

						.show()
			}
		}

		val toggle = ActionBarDrawerToggle(this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
		drawer_layout.addDrawerListener(toggle)
		toggle.syncState()

		logger.directory = getExternalFilesDir("logs")

		nav_view.setNavigationItemSelectedListener(this)
	}

	override fun onBackPressed() {
		if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
			drawer_layout.closeDrawer(GravityCompat.START)
		} else {
			super.onBackPressed()
		}
	}

	override fun onStart() {
		super.onStart()
		bindService(Intent(this, BtleService::class.java), this, Context.BIND_AUTO_CREATE)
		adapter.setSelected(getSharedPreferences(PREF_ID, 0).getStringSet(PREF_LOGGED, mutableSetOf()))
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			Log.i(TAG, "Request permission")
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), PERMISSION_CODE)
		} else {
			navigateTo(R.id.nav_logging)
		}
	}

	override fun onStop() {
		super.onStop()
		if (bluetoothLEService != null) {
			unbindService(this)
		}
	}

	override fun onNavigationItemSelected(item: MenuItem): Boolean {
		navigateTo(item.itemId)
		drawer_layout.closeDrawer(GravityCompat.START)
		return true
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		when (requestCode) {
			PERMISSION_CODE -> {
				Log.i(TAG, "Permission = " + grantResults[0])
				// If request is cancelled, the result arrays are empty.
				if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					navigateTo(R.id.nav_logging)
				}
				return
			}
		}
	}

	private fun startLogging() {
		val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
		val selected = adapter.getSelectedDevices()
		if (!selected.isEmpty()) {
			Log.i(TAG, "Starting logging")
			for (device in selected) {
				if (device.board == null) {
					Log.i(TAG, "Logging " + device)
					val bleDevice = bluetoothManager.adapter.getRemoteDevice(device.address)
					device.board = bluetoothLEService?.getMetaWearBoard(bleDevice)
					Log.i(TAG, "Board: " + device.board)
					connectSensor(device)
				}
			}

			val found = selected.any { it.board != null }
			if (found) {
				logger.start()
				loggingButton.setImageResource(R.drawable.ic_stop_black_24dp)
				loggingProgress.visibility = View.VISIBLE
			}
		}
		adapter.notifyDataSetChanged()
	}

	private fun stopLogging() {
		Log.i(TAG, "Stopping logging")
		logger.stop()
		loggingButton.setImageResource(R.drawable.ic_play_arrow_black_24dp)
		loggingProgress.visibility = View.INVISIBLE

		for (device in adapter.sensors) {
			disconnect(device)
		}
		adapter.notifyDataSetChanged()
	}

	private fun connectSensor(sensor: Sensor) {
		Log.i(TAG, "Connecting to " + sensor.address)
		sensor.board?.let {
			it.connectAsync(1000).continueWith { task ->
				if (task.isFaulted) {
					Log.i(TAG, "Failed to connect to metawear")
					Log.i(TAG, task.error.localizedMessage)
					sensor.error = true
					if (sensor.selected) {
						connectSensor(sensor)
					}
				} else {
					sensor.error = false
					Log.i(TAG, "Connected to " + sensor.address)
					val accelerometer = it.getModule(Accelerometer::class.java)
					accelerometer.configure()
							.odr(10f)
							.commit()
					accelerometer.acceleration()
							.addRouteAsync { source ->
								source.stream { data, env ->
									val casted = data.value(Acceleration::class.java)
									logger.log(String.format(Locale.US, "%s,%s,%s,%.4f,%.4f,%.4f,%s%n",
											data.formattedTimestamp(),
											env[0].toString(),
											"accel",
											casted.x(), casted.y(), casted.z(),
											"g"))
								}
							}
							.continueWith { task1 ->
								task1.result.setEnvironment(0, sensor.address)
								accelerometer.packedAcceleration().start()
								accelerometer.start()
							}

					val gyro = it.getModule(GyroBmi160::class.java)
					gyro.configure()
							.odr(GyroBmi160.OutputDataRate.ODR_25_HZ)
							.commit()
					gyro.packedAngularVelocity()
							.addRouteAsync { source ->
								source.stream { data, env ->
									val casted = data.value(AngularVelocity::class.java)
									logger.log(String.format(Locale.US, "%s,%s,%s,%.4f,%.4f,%.4f,%s%n",
											data.formattedTimestamp(),
											env[0].toString(),
											"gyro",
											casted.x(), casted.y(), casted.z(),
											"\u00B0/s"))
								}
							}
							.continueWith { task1 ->
								task1.result.setEnvironment(0, sensor.address)
								gyro.packedAngularVelocity().start()
								gyro.start()
							}

					val led = it.getModule(Led::class.java)
					led.editPattern(Led.Color.BLUE, Led.PatternPreset.PULSE).commit()
					led.editPattern(Led.Color.GREEN, Led.PatternPreset.PULSE).commit()
					led.play()
				}
			}
		}
	}

	private fun disconnect(sensor: Sensor) {
		if (sensor.board?.isConnected == true) {
			Log.i(TAG, "Disconnecting from " + sensor.address)
			sensor.board?.tearDown()
			sensor.board?.getModule(Led::class.java)?.stop(true)
			sensor.board?.disconnectAsync()
			sensor.board = null
		}
	}

	private fun navigateTo(id: Int) {
		when (id) {
			R.id.nav_logging -> {
				supportFragmentManager
						.beginTransaction()
						.replace(R.id.content, DeviceListFragment())
						.commit()
			}
			R.id.nav_playback -> {
				if (logger.isLogging) {
					val builder = AlertDialog.Builder(this)
					builder.setTitle("Currently Logging")
							.setMessage("Leaving now will stop the logging.")
							.setPositiveButton("Leave", { dialog, _ ->
								stopLogging()
								startActivity(Intent(this, PlaybackCreatorActivity::class.java))
								dialog.dismiss()
							})
							.setNegativeButton("Cancel", { dialog, _ ->
								dialog.cancel()
							})
							.show()
				} else {
					startActivity(Intent(this, PlaybackCreatorActivity::class.java))
				}
			}
		}
	}
}
