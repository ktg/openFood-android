package uk.ac.nott.mrl.openfood.logging

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.*
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.mbientlab.metawear.MetaWearBoard
import com.mbientlab.metawear.android.BtleService
import com.mbientlab.metawear.data.Acceleration
import com.mbientlab.metawear.data.AngularVelocity
import com.mbientlab.metawear.module.Accelerometer
import com.mbientlab.metawear.module.GyroBmi160
import com.mbientlab.metawear.module.Led
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.board_item.view.*
import uk.ac.nott.mrl.openfood.R
import java.io.File
import java.io.FileWriter
import java.util.*

class MainActivity : AppCompatActivity(), ServiceConnection {
	inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val rootView = view

		fun setBoard(scan: ScanResult) {
			rootView.nameText.text = scan.device.name
			rootView.macAddressText.text = scan.device.address
			//Log.i("TIME", "Time: " + (SystemClock.elapsedRealtimeNanos() - scan.timestampNanos))
			if (boards.containsKey(scan.device.address)) {
				rootView.signalIcon.setImageResource(R.drawable.ic_router_black_24dp)
			} else {
				if (SystemClock.elapsedRealtimeNanos() - scan.timestampNanos > 10000000000) {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_0_bar_black_24dp)
				} else if (scan.rssi < -96) {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_1_bar_black_24dp)
				} else if (scan.rssi < -80) {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_2_bar_black_24dp)
				} else if (scan.rssi < -64) {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_3_bar_black_24dp)
				} else {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_4_bar_black_24dp)
				}
			}
			rootView.checkBox.isEnabled = true
			rootView.checkBox.isChecked = addresses.contains(scan.device.address)
			rootView.setOnClickListener { view ->
				val address = view.macAddressText.text.toString()
				if (addresses.contains(address)) {
					addresses.remove(address)
					boards[address]?.let { disconnectFromBoard(it) }
					if(addresses.isEmpty()) {
						stopLogging()
					}
				} else {
					addresses.add(address)
					if (logging) {
						startLogging()
					}
				}
				getSharedPreferences(preferences, 0).edit().putStringSet("addresses", addresses).commit()
				view.checkBox.isChecked = addresses.contains(address)
			}
		}
	}

	companion object {
		val preferences = "OF-LOG-PREFS"
		val boards = mutableMapOf<String, MetaWearBoard>()
		val addresses = mutableSetOf<String>()
		var logging = false
		var writer: FileWriter? = null
	}

	private val PERMISSION_CODE = 4572
	private val TAG = MainActivity::class.java.simpleName
	private val adapter = object : RecyclerView.Adapter<ViewHolder>() {
		private val scans = TreeMap<String, ScanResult>()

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder? {
			val layoutInflater = LayoutInflater.from(parent.context)
			val root = layoutInflater.inflate(R.layout.board_item, parent, false)
			return ViewHolder(root)
		}

		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			val key = scans.keys.toTypedArray()[position]
			val scan = scans[key]
			if (scan != null) {
				holder.setBoard(scan)
			}
		}

		override fun getItemCount(): Int {
			return scans.size
		}

		fun addScan(scan: ScanResult) {
			scans.put(scan.device.address, scan)
			notifyDataSetChanged()
		}
	}
	private lateinit var btleService: BtleService.LocalBinder
	private var bound = false


	fun isLogging(device: String): Boolean {
		return logging && addresses.contains(device)
	}

	override fun onServiceConnected(className: ComponentName, service: IBinder) {
		btleService = service as BtleService.LocalBinder
		bound = true
		loggingButton.isEnabled = true
	}

	override fun onServiceDisconnected(arg0: ComponentName) {
		stopLogging()
		loggingButton.isEnabled = false
		bound = false
	}

	override fun onStart() {
		super.onStart()
		bindService(Intent(this, BtleService::class.java), this, Context.BIND_AUTO_CREATE)
		addresses.addAll(getSharedPreferences(preferences, 0).getStringSet("addresses", mutableSetOf()))
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			Log.i(TAG, "Request permission")
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), PERMISSION_CODE)
		} else {
			startBLEScan()
		}
	}

	override fun onStop() {
		super.onStop()
		if (bound) {
			unbindService(this)
		}
	}

	private fun disconnectFromBoard(board: MetaWearBoard) {
		Log.i(TAG, "Disconnecting from " + board.macAddress)
		board.tearDown()
		board.getModule(Led::class.java).stop(true)
		board.disconnectAsync()
		boards.remove(board.macAddress)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

		setSupportActionBar(toolbar)

		sensorList.layoutManager = LinearLayoutManager(this)
		sensorList.adapter = adapter

		loggingButton.isEnabled = false
		loggingButton.setOnClickListener {
			if (logging) {
				stopLogging()
			} else {
				startLogging()
			}
		}
	}

	fun startLogging() {
		val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
		if(!addresses.isEmpty()) {
			Log.i(TAG, "Starting logging")
			if (writer == null) {

				val dir = File(getExternalFilesDir(null), "logs")
				if(!dir.exists()) {
					Log.i(TAG, "Doesn't exist " + dir.absolutePath)
					if (!dir.mkdirs()) {
						Log.i(TAG, "Didn't create " + dir.absolutePath)
					}
				}
				val file = File(dir, "OpenFoodLog " + System.currentTimeMillis() + ".csv")
				Log.i(TAG, file.absolutePath)
				writer = FileWriter(file)
			}

			for (device in addresses) {
				if (device !in boards.keys) {
					Log.i(TAG, "Logging " + device)
					val bleDevice = bluetoothManager.adapter.getRemoteDevice(device)
					val board = btleService.getMetaWearBoard(bleDevice)
					Log.i(TAG, "Board: " + board)
					if (board != null) {
						boards.put(board.macAddress, board)
						connectToBoard(board)
					}
				}
			}
			if (!boards.isEmpty()) {
				logging = true
				loggingButton.setImageResource(R.drawable.ic_stop_black_24dp)
				loggingProgress.visibility = View.VISIBLE
			}
		}
		adapter.notifyDataSetChanged()
	}


	fun stopLogging() {
		logging = false
		writer?.flush()
		writer?.close()
		writer = null

		Log.i(TAG, "Stopping logging")

		loggingButton.setImageResource(R.drawable.ic_play_arrow_black_24dp)
		loggingProgress.visibility = View.INVISIBLE

		val boardList = ArrayList(boards.values)
		for (board in boardList) {
			disconnectFromBoard(board)
		}
		adapter.notifyDataSetChanged()
	}

	private fun connectToBoard(board: MetaWearBoard) {
		Log.i(TAG, "Connecting to " + board.macAddress)
		board.connectAsync(1000).continueWith { task ->
			if (task.isFaulted) {
				Log.i(TAG, "Failed to connect to metawear")
				Log.i(TAG, task.error.localizedMessage)
				if (isLogging(board.macAddress)) {
					connectToBoard(board)
				} else {

				}
			} else {
				Log.i(TAG, "Connected to " + board.macAddress)
				val accelerometer = board.getModule(Accelerometer::class.java)
				accelerometer.configure()
						.odr(10f)
						.commit()
				accelerometer.acceleration()
						.addRouteAsync { source ->
							source.stream { data, env ->
								val casted = data.value(Acceleration::class.java)
								writer?.write(String.format(Locale.US, "%s,%s,%s,%.4f,%.4f,%.4f,%s%n",
										data.formattedTimestamp(),
										env[0].toString(),
										"accel",
										casted.x(), casted.y(), casted.z(),
										"g"))
							}
						}
						.continueWith { task1 ->
							task1.result.setEnvironment(0, board.macAddress)
							accelerometer.packedAcceleration().start()
							accelerometer.start()
						}

				val gyro = board.getModule(GyroBmi160::class.java)
				gyro.configure()
						.odr(GyroBmi160.OutputDataRate.ODR_25_HZ)
						.commit()
				gyro.packedAngularVelocity()
						.addRouteAsync { source ->
							source.stream { data, env ->
								val casted = data.value(AngularVelocity::class.java)
								writer?.write(String.format(Locale.US, "%s,%s,%s,%.4f,%.4f,%.4f,%s%n",
										data.formattedTimestamp(),
										env[0].toString(),
										"gyro",
										casted.x(), casted.y(), casted.z(),
										"\u00B0/s"))
							}
						}
						.continueWith { task1 ->
							task1.result.setEnvironment(0, board.macAddress)
							gyro.packedAngularVelocity().start()
							gyro.start()
						}

				val led = board.getModule(Led::class.java)
				led.editPattern(Led.Color.BLUE, Led.PatternPreset.PULSE).commit()
				led.editPattern(Led.Color.GREEN, Led.PatternPreset.PULSE).commit()
				led.play()
			}
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		when (requestCode) {
			PERMISSION_CODE -> {
				Log.i(TAG, "Permission = " + grantResults[0])
				// If request is cancelled, the result arrays are empty.
				if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					startBLEScan()
				}
				return
			}
		}
	}

	private fun startBLEScan() {
		val scanSettings = ScanSettings.Builder().build()
		val scanFilters = Collections.singletonList(ScanFilter.Builder().setServiceUuid(ParcelUuid(MetaWearBoard.METAWEAR_GATT_SERVICE)).build())
		val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
		bluetoothManager.adapter.bluetoothLeScanner.startScan(scanFilters, scanSettings, object : ScanCallback() {
			override fun onScanResult(callbackType: Int, result: ScanResult) {
				Log.i(TAG, result.device.name + ", " + result.device.address + ": " + result.rssi)
				adapter.addScan(result)
			}
		})
		//bluetoothManager.adapter.bluetoothLeScanner.startScan(scanCallback)
		Log.i(TAG, "Scan started")
	}
}
