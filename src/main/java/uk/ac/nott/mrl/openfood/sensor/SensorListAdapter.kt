package uk.ac.nott.mrl.openfood.sensor

import android.bluetooth.le.ScanResult
import android.os.SystemClock
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.list_item_device.view.*
import uk.ac.nott.mrl.openfood.R
import java.util.*

class SensorListAdapter : RecyclerView.Adapter<SensorListAdapter.DeviceViewHolder>() {
	inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val rootView = view

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
			rootView.setOnClickListener { _ ->
				sensor.selected = !sensor.selected
				rootView.checkBox.isChecked = sensor.selected
				clickListener?.onClick(sensor)
			}
			rootView.setOnLongClickListener {
				longClickListener?.onClick(sensor)
				longClickListener != null
			}
		}
	}

	private val sensorMap = TreeMap<String, Sensor>()
	var clickListener: SensorClickListener? = null
	var longClickListener: SensorClickListener? = null
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
		device?.let { holder.setDevice(device) }
	}

	override fun getItemCount(): Int {
		return sensorMap.size
	}

	fun getSelected(): Set<String> {
		val selected = mutableSetOf<String>()
		for ((address, sensor) in sensorMap) {
			if (sensor.selected) {
				selected.add(address)
			}
		}
		return selected
	}

	fun getSelectedDevices(): Collection<Sensor> {
		val selected = mutableListOf<Sensor>()
		for ((_, sensor) in sensorMap) {
			if (sensor.selected) {
				selected.add(sensor)
			}
		}
		return selected
	}

	fun setSelected(selected: Set<String>) {
		selected
				.map {
					sensorMap.computeIfAbsent(it, { key ->
						Sensor(key, "Not Detected Yet")
					})
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
		val sensor = sensorMap.computeIfAbsent(scan.device.address, { key ->
			created = true
			Sensor(key, scan.device.name)
		})
		sensor.name = scan.device.name
		sensor.rssi = scan.rssi
		sensor.timestamp = scan.timestampNanos
		sensorMap.put(sensor.address, sensor)
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