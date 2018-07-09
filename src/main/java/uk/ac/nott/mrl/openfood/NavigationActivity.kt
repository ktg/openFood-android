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
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.fragment.app.transaction
import com.google.android.material.navigation.NavigationView
import com.mbientlab.metawear.android.BtleService
import com.mbientlab.metawear.data.Acceleration
import com.mbientlab.metawear.data.AngularVelocity
import com.mbientlab.metawear.module.*
import com.mbientlab.metawear.module.Timer
import kotlinx.android.synthetic.main.activity_navigation.*
import uk.ac.nott.mrl.openfood.logging.DeviceLogger
import uk.ac.nott.mrl.openfood.logging.LogListFragment
import uk.ac.nott.mrl.openfood.playback.PlaybackCreatorActivity
import uk.ac.nott.mrl.openfood.sensor.Sensor
import uk.ac.nott.mrl.openfood.sensor.SensorListAdapter
import uk.ac.nott.mrl.openfood.sensor.SensorListAdapterHolder
import uk.ac.nott.mrl.openfood.sensor.SensorListFragment
import java.io.File
import java.util.*

class NavigationActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, ServiceConnection, SensorListAdapterHolder {
	companion object {
		const val PREF_ID = "OF-LOG-PREFS"
		const val PREF_LOGGED = "LOGGED_ADDRESSES"
		const val PREF_PLAYBACK = "PLAYBACK_ADDRESSES"
		const val PREF_VIDEO = "PLAYBACK_VIDEO"
		private const val REQUEST_PERMISSION_CODE = 4572
		private const val REQUEST_BLUETOOTH_CODE = 4574
		private const val TEMP_SAMPLE_PERIOD = 100
		private val TAG = NavigationActivity::class.java.simpleName
	}

	private var bluetoothLEService: BtleService.LocalBinder? = null
	private val logger = DeviceLogger()
	private var action = ""
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

		loggingButton.setOnClickListener {
			if (logger.isLogging) {
				stopLogging()
			} else {
				startLogging()
			}
		}
		adapter.clickListener = { sensor ->
			getSharedPreferences(PREF_ID, 0).edit {
				putStringSet(PREF_LOGGED, adapter.getSelected())
			}
			if (logger.isLogging) {
				if (sensor.selected) {
					connectSensor(sensor)
				} else {
					disconnectSensor(sensor)
				}
			}
		}
		adapter.longClickListener = { sensor ->
			if (sensor.board?.isConnected != true) {
				if (sensor.board == null) {
					val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
					val bleDevice = bluetoothManager.adapter.getRemoteDevice(sensor.address)
					sensor.board = bluetoothLEService?.getMetaWearBoard(bleDevice)
				}

				sensor.board?.let {
					it.connectAsync().continueWith { task ->
						if (task.isFaulted) {
							Log.i(TAG, sensor.address + " connection failed: " + task.error.localizedMessage)
						} else {
							Log.i(TAG, sensor.address + " connected")
							val led = it.getModule(Led::class.java)
							led.editPattern(Led.Color.RED, Led.PatternPreset.BLINK).commit()
							led.play()
						}
					}

					val builder = AlertDialog.Builder(this@NavigationActivity)
					val view = layoutInflater.inflate(R.layout.edit_name, null, false)
					val input = view.findViewById<EditText>(R.id.edit_name)
					input.append(sensor.name)
					val dialog = builder.setTitle(getString(R.string.rename_dialog_title, sensor.name))
							.setMessage(getString(R.string.rename_dialog_message, sensor.address))
							.setView(view)
							.setPositiveButton(R.string.rename_dialog_rename) { dialog, _ ->
								it.getModule(Settings::class.java)
										.editBleAdConfig()
										.deviceName(input.text.toString())
										.commit()

								dialog.dismiss()
							}
							.setNegativeButton(R.string.raname_dialog_cancel) { dialog, _ ->
								dialog.cancel()
							}
							.setOnDismissListener {
								disconnectSensor(sensor)
							}
							.create()
					dialog.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
					dialog.show()
				}
			}
		}

		val toggle = ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
		drawer.addDrawerListener(toggle)
		toggle.syncState()

		logger.directory = File(filesDir, "logs")

		navigation.setNavigationItemSelectedListener(this)
	}

	override fun onBackPressed() {
		if (drawer.isDrawerOpen(GravityCompat.START)) {
			drawer.closeDrawer(GravityCompat.START)
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
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			Log.i(TAG, "Request permission")
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_PERMISSION_CODE)
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
		item.isEnabled = true
		when (item.itemId) {
			R.id.nav_action_none -> action = ""
			R.id.nav_action_drink -> action = "drink"
			R.id.nav_action_held -> action = "held"
			R.id.nav_action_rest -> action = "rest"
			else -> navigateTo(item.itemId)
		}

		return true
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		permissionRequests()
	}

	private fun startLogging() {
		val selected = adapter.getSelectedDevices()
		if (!selected.isEmpty()) {
			Log.i(TAG, "Starting logging")
			window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
			logger.start()
			loggingButton.setImageResource(R.drawable.ic_stop_black_24dp)
			loggingProgress.visibility = View.VISIBLE

			for (sensor in selected) {
				connectSensor(sensor)
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
		window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
	}

	private fun updateSensor(sensor: Sensor) {
		runOnUiThread {
			adapter.updateSensor(sensor)
		}
	}

	private fun connectSensor(sensor: Sensor) {
		Log.i(TAG, "Connecting to " + sensor.address)
		if (!logger.isLogging || sensor.isConnected()) {
			return
		}
		if (sensor.board == null) {
			val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
			val bleDevice = bluetoothManager.adapter.getRemoteDevice(sensor.address)
			sensor.board = bluetoothLEService?.getMetaWearBoard(bleDevice)
		}

		sensor.board?.let {
			sensor.connecting = true
			updateSensor(sensor)
			it.connectAsync().continueWith { task ->
				if (!logger.isLogging || !sensor.selected) {
					disconnectSensor(sensor)
				} else if (task.isFaulted) {
					Log.i(TAG, "Failed to connect to metawear")
					Log.i(TAG, task.error.localizedMessage)
					connectSensor(sensor)
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
							.addRouteAsync {
								it.stream { data, env ->
									val casted = data.value(Acceleration::class.java)
									val now = System.currentTimeMillis()
									val timedout = sensor.hasTimedOut(now)
									sensor.timestamp = now
									logger.log(String.format(Locale.US, "%s,%s,%s,%s,%.4f,%.4f,%.4f,%s%n",
											data.formattedTimestamp(),
											env[0].toString(),
											action,
											"accel",
											casted.x(), casted.y(), casted.z(),
											"g"))
									if (timedout) {
										updateSensor(sensor)
									}
								}
							}
							.continueWith {
								it.result.setEnvironment(0, sensor.address)
								accelerometer.packedAcceleration().start()
								accelerometer.start()
							}

					val gyro = it.getModule(GyroBmi160::class.java)
					gyro.configure()
							.odr(GyroBmi160.OutputDataRate.ODR_25_HZ)
							.commit()
					gyro.packedAngularVelocity()
							.addRouteAsync {
								it.stream { data, env ->
									val casted = data.value(AngularVelocity::class.java)
									val now = System.currentTimeMillis()
									val timedout = sensor.hasTimedOut(now)
									sensor.timestamp = now
									logger.log(String.format(Locale.US, "%s,%s,%s,%s,%.4f,%.4f,%.4f,%s%n",
											data.formattedTimestamp(),
											env[0].toString(),
											action,
											"gyro",
											casted.x(), casted.y(), casted.z(),
											"\u00B0/s"))
									if (timedout) {
										updateSensor(sensor)
									}
								}
							}
							.continueWith {
								it.result.setEnvironment(0, sensor.address)
								gyro.packedAngularVelocity().start()
								gyro.start()
							}

					val timer = it.getModule(Timer::class.java)
					val temp = it.getModule(Temperature::class.java)
					val tempSensor = temp.findSensors(Temperature.SensorType.PRESET_THERMISTOR)?.get(0)
					tempSensor
							?.addRouteAsync {
								it.stream { data, env ->
									val now = System.currentTimeMillis()
									val timedout = sensor.hasTimedOut(now)
									sensor.timestamp = now
									logger.log(String.format(Locale.US, "%s,%s,%s,%s,%.4f,,,%s%n",
											data.formattedTimestamp(),
											env[0].toString(),
											action,
											"temp",
											data.value(Float::class.javaObjectType),
											"\u00B0C"))
									if (timedout) {
										updateSensor(sensor)
									}
								}
							}
							?.continueWith {
								it.result.setEnvironment(0, sensor.address)
								timer.scheduleAsync(TEMP_SAMPLE_PERIOD, false, tempSensor::read).continueWith {
									it.result.start()
								}
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
			sensor.board?.disconnectAsync()?.continueWith {
				updateSensor(sensor)
			}
			sensor.board = null
		}
		sensor.connecting = false
		updateSensor(sensor)
	}

	private fun checkConnected() {
		for (sensor in adapter.sensors) {
			val now = System.currentTimeMillis()
			if (sensor.board?.isConnected == true) {
				if (sensor.hasTimedOut(now)) {
					updateSensor(sensor)
				}
			} else if (sensor.selected && !sensor.connecting) {
				connectSensor(sensor)
			}
		}
		connectedHanlder.postDelayed(connectedRunnable, 1000)
	}

	private fun navigateTo(id: Int) {
		when (id) {
			R.id.nav_logging -> {
				supportFragmentManager.transaction {
					replace(R.id.content, SensorListFragment())
				}
			}
			R.id.nav_logs -> {
				supportFragmentManager.transaction {
					replace(R.id.content, LogListFragment())
				}
			}
			R.id.nav_playback -> {
				if (logger.isLogging) {
					val builder = AlertDialog.Builder(this)
					builder.setTitle(R.string.log_warn_dialog_title)
							.setMessage(R.string.log_warn_dialog_message)
							.setPositiveButton(R.string.log_warn_dialog_leave) { dialog, _ ->
								stopLogging()
								startActivity(Intent(this, PlaybackCreatorActivity::class.java))
								dialog.dismiss()
							}
							.setNegativeButton(R.string.log_warn_dialog_cancel) { dialog, _ ->
								dialog.cancel()
							}
							.show()
				} else {
					startActivity(Intent(this, PlaybackCreatorActivity::class.java))
				}
			}
		}
		drawer.closeDrawer(GravityCompat.START)
	}
}
