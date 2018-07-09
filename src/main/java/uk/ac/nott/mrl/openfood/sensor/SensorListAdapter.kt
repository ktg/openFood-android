package uk.ac.nott.mrl.openfood.sensor

import android.bluetooth.le.ScanResult
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.list_item_device.view.*
import uk.ac.nott.mrl.openfood.R
import java.util.*

class SensorListAdapter : RecyclerView.Adapter<SensorListAdapter.DeviceViewHolder>() {
	inner class DeviceViewHolder(private val rootView: View) : RecyclerView.ViewHolder(rootView) {
		fun setDevice(sensor: Sensor) {
			rootView.nameText.text = sensor.name
			rootView.macAddressText.text = sensor.address
			rootView.signalIcon.setColorFilter(ContextCompat.getColor(rootView.context, android.R.color.black))
			//Log.i("TIME", "Time: " + (SystemClock.elapsedRealtimeNanos() - scan.timestampNanos))
			if (sensor.isConnected()) {
				if (sensor.hasTimedOut(System.currentTimeMillis())) {
					rootView.signalIcon.setColorFilter(ContextCompat.getColor(rootView.context, android.R.color.holo_red_dark))
					rootView.signalIcon.setImageResource(R.drawable.ic_phonelink_erase_black_24dp)
				} else {
					rootView.signalIcon.setImageResource(R.drawable.ic_tap_and_play_black_24dp)
				}
			} else if (sensor.connecting) {
				rootView.signalIcon.setColorFilter(ContextCompat.getColor(rootView.context, android.R.color.darker_gray))
				rootView.signalIcon.setImageResource(R.drawable.ic_tap_and_play_black_24dp)
			} else if (sensor.rssi == Integer.MIN_VALUE) {
				rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_null_black_24dp)
			} else if (SystemClock.elapsedRealtimeNanos() - sensor.timestamp > 10000000000) {
				rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_0_bar_black_24dp)
			} else if (sensor.rssi < -96) {
				rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_1_bar_black_24dp)
			} else if (sensor.rssi < -80) {
				rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_2_bar_black_24dp)
			} else if (sensor.rssi < -64) {
				rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_3_bar_black_24dp)
			} else {
				rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_4_bar_black_24dp)
			}

			rootView.checkBox.isEnabled = true
			rootView.checkBox.isChecked = sensor.selected
			rootView.setOnClickListener {
				sensor.selected = !sensor.selected
				rootView.checkBox.isChecked = sensor.selected
				clickListener?.invoke(sensor)
			}
			rootView.setOnLongClickListener {
				longClickListener?.invoke(sensor)
				longClickListener != null
			}
		}
	}

	private val sensorMap = TreeMap<String, Sensor>()
	var clickListener: ((Sensor) -> Unit)? = null
	var longClickListener: ((Sensor) -> Unit)? = null
	val sensors: Collection<Sensor>
		get() = sensorMap.values

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
		val layoutInflater = LayoutInflater.from(parent.context)
		val root = layoutInflater.inflate(R.layout.list_item_device, parent, false)
		return DeviceViewHolder(root)
	}

	override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
		val key = sensorMap.keys.toList()[position]
		val device = sensorMap[key]
		device?.let { holder.setDevice(it) }
	}

	override fun getItemCount(): Int {
		return sensorMap.size
	}

	fun getSelected(): Set<String> {
		return getSelectedDevices().map { it.address }.toSet()
	}

	fun getSelectedDevices(): Collection<Sensor> {
		return sensorMap.values.filter { it.selected }
	}

	fun setSelected(selected: Set<String>) {
		selected
				.map {
					sensorMap.getOrPut(it) {
						Sensor(it, "Not Detected Yet")
					}
				}
				.forEach { it.selected = true }
		notifyDataSetChanged()
	}

	fun updateSensor(sensor: Sensor) {
		val index = sensorMap.keys.toList().indexOf(sensor.address)
		if (index > -1) {
			notifyItemChanged(index)
		}
	}

	fun updateDevice(scan: ScanResult) {
		var created = false
		val sensor = sensorMap.getOrElse(scan.device.address) {
			created = true
			Sensor(scan.device.address, scan.device.name)
		}
		sensor.name = scan.device.name
		sensor.rssi = scan.rssi
		sensor.timestamp = scan.timestampNanos
		sensorMap[sensor.address] = sensor
		val index = sensorMap.keys.toList().indexOf(sensor.address)
		if (index > -1) {
			if (created) {
				notifyItemInserted(index)
			} else {
				notifyItemChanged(index)
			}
		}
	}
}