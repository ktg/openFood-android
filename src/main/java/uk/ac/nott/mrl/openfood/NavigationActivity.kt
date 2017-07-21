package uk.ac.nott.mrl.openfood

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import uk.ac.nott.mrl.openfood.logging.DeviceLogger
import uk.ac.nott.mrl.openfood.logging.LogListFragment
import uk.ac.nott.mrl.openfood.playback.PlaybackCreatorActivity
import uk.ac.nott.mrl.openfood.sensor.*
import java.util.*


class NavigationActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, ServiceConnection, SensorListAdapterHolder {
	companion object {
		val PREF_ID = "OF-LOG-PREFS"
		val PREF_LOGGED = "LOGGED_ADDRESSES"
		val PREF_PLAYBACK = "PLAYBACK_ADDRESSES"
		val PREF_VIDEO = "PLAYBACK_VIDEO"
		private val REQUEST_PERMISSION_CODE = 4572
		private val REQUEST_BLUETOOTH_CODE = 4574
		private val TAG = NavigationActivity::class.java.simpleName
	}

	private var bluetoothLEService: BtleService.LocalBinder? = null
	private val logger = DeviceLogger()
	override val adapter = SensorListAdapter()
	private val connectedHanlder = Handler(Looper.getMainLooper())
	private val connectedRunnable = Runnable {
		checkConnected()
	}

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
				if(logger.isLogging){
					if(sensor.selected) {
						connectSensor(sensor)
					} else {
						disconnectSensor(sensor)
					}
				}
			}
		}
		adapter.longClickListener = object : SensorClickListener {
			override fun onClick(sensor: Sensor) {
				if (sensor.board?.isConnected == true) {
					return
				}
				val builder = AlertDialog.Builder(this@NavigationActivity)
				val input = EditText(this@NavigationActivity)
				input.setText(sensor.name)
				if (sensor.board == null) {
					val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
					val bleDevice = bluetoothManager.adapter.getRemoteDevice(sensor.address)
					sensor.board = bluetoothLEService?.getMetaWearBoard(bleDevice)
				}

				sensor.board?.let {
					it.connectAsync(1000).continueWith { task ->
						if (task.isFaulted) {
							Log.i(TAG, sensor.address + " connection failed: " + task.error.localizedMessage)
						} else {
							Log.i(TAG, sensor.address + " connected")
							val led = it.getModule(Led::class.java)
							led.editPattern(Led.Color.RED, Led.PatternPreset.BLINK).commit()
							led.play()
						}
					}

					builder.setTitle("Rename " + sensor.name)
							.setView(input)
							.setPositiveButton("Rename", { dialog, _ ->
								if (sensor.board == null) {

								}

								it.getModule(Settings::class.java)
										.editBleAdConfig()
										.deviceName(input.text.toString())
										.commit()

								dialog.dismiss()
							})
							.setNegativeButton("Cancel", { dialog, _ ->
								dialog.cancel()
							})
							.setOnDismissListener {
								disconnectSensor(sensor)
							}
							.show()
				}
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
		permissionRequests()
	}

	private fun permissionRequests() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
				|| ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			Log.i(TAG, "Request permission")
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_PERMISSION_CODE)
		} else {
			val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
			if (!bluetoothManager.adapter.isEnabled) {
				val intentBtEnabled = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
				startActivityForResult(intentBtEnabled, REQUEST_BLUETOOTH_CODE)
			} else {
				navigateTo(R.id.nav_logging)
			}
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
		permissionRequests()
	}

	private fun startLogging() {
		val selected = adapter.getSelectedDevices()
		if (!selected.isEmpty()) {
			Log.i(TAG, "Starting logging")
			for (sensor in selected) {
				connectSensor(sensor)
			}

			val found = selected.any { it.board != null }
			if (found) {
				logger.start()
				loggingButton.setImageResource(R.drawable.ic_stop_black_24dp)
				loggingProgress.visibility = View.VISIBLE
			}
		}
	}

	private fun stopLogging() {
		Log.i(TAG, "Stopping logging")
		logger.stop()
		loggingButton.setImageResource(R.drawable.ic_play_arrow_black_24dp)
		loggingProgress.visibility = View.INVISIBLE

		for (device in adapter.sensors) {
			disconnectSensor(device)
		}
		connectedHanlder.removeCallbacks(connectedRunnable)
	}

	private fun updateSensor(sensor: Sensor) {
		runOnUiThread {
			adapter.updateSensor(sensor)
		}
	}

	private fun connectSensor(sensor: Sensor) {
		Log.i(TAG, "Connecting to " + sensor.address)
		if(sensor.isConnected()) {
			return
		}
		if(sensor.board == null) {
			val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
			val bleDevice = bluetoothManager.adapter.getRemoteDevice(sensor.address)
			sensor.board = bluetoothLEService?.getMetaWearBoard(bleDevice)
		}

		sensor.board?.let {
			sensor.connecting = true
			updateSensor(sensor)
			it.connectAsync(1000).continueWith { task ->
				if (task.isFaulted) {
					Log.i(TAG, "Failed to connect to metawear")
					Log.i(TAG, task.error.localizedMessage)
					if (sensor.selected) {
						connectSensor(sensor)
					} else {
						sensor.connecting = false
						updateSensor(sensor)
					}
				} else {
					sensor.connecting = false
					updateSensor(sensor)
					checkConnected()
					Log.i(TAG, "Connected to " + sensor.address)
					val accelerometer = it.getModule(Accelerometer::class.java)
					accelerometer.configure()
							.odr(10f)
							.commit()
					accelerometer.acceleration()
							.addRouteAsync { source ->
								source.stream { data, env ->
									val casted = data.value(Acceleration::class.java)
									sensor.timestamp = System.currentTimeMillis()
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
									sensor.timestamp = System.currentTimeMillis()
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

	private fun disconnectSensor(sensor: Sensor) {
		if (sensor.board?.isConnected == true) {
			Log.i(TAG, "Disconnecting from " + sensor.address)
			sensor.board?.tearDown()
			sensor.board?.getModule(Led::class.java)?.stop(true)
			sensor.board?.disconnectAsync()?.continueWith { _ ->
				updateSensor(sensor)
			}
		}
		sensor.connecting = false
		updateSensor(sensor)
	}

	private fun checkConnected() {
		for (sensor in adapter.sensors) {
			val now = System.currentTimeMillis()
			if(sensor.board?.isConnected == true) {
				if(sensor.timestamp + SensorListAdapter.TIMEOUT < now) {
					updateSensor(sensor)
				}
			} else if(sensor.selected) {
				connectSensor(sensor)
			}
		}
		connectedHanlder.postDelayed(connectedRunnable, 1000)
	}

	private fun navigateTo(id: Int) {
		when (id) {
			R.id.nav_logging -> {
				supportFragmentManager
						.beginTransaction()
						.replace(R.id.content, SensorListFragment())
						.commit()
			}
			R.id.nav_logs -> {
				supportFragmentManager
						.beginTransaction()
						.replace(R.id.content, LogListFragment())
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
