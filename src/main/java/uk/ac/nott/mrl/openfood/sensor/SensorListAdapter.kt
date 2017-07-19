package uk.ac.nott.mrl.openfood.sensor

import android.bluetooth.le.ScanResult
import android.os.SystemClock
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.list_item_device.view.*
import uk.ac.nott.mrl.openfood.R
import java.util.*

class SensorListAdapter : RecyclerView.Adapter<SensorListAdapter.DeviceViewHolder>() {
	inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val rootView = view

		fun setDevice(sensor: Sensor) {
			rootView.nameText.text = sensor.name
			rootView.macAddressText.text = sensor.address
			//Log.i("TIME", "Time: " + (SystemClock.elapsedRealtimeNanos() - scan.timestampNanos))
			if (sensor.isConnected()) {
				rootView.signalIcon.setImageResource(R.drawable.ic_tap_and_play_black_24dp)
			} else if(sensor.error) {
				if (sensor.rssi == Integer.MIN_VALUE) {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_null_black_24dp)
				} else if (SystemClock.elapsedRealtimeNanos() - sensor.timestamp > 10000000000) {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_connected_no_internet_0_bar_black_24dp)
				} else if (sensor.rssi < -96) {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_connected_no_internet_1_bar_black_24dp)
				} else if (sensor.rssi < -80) {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_connected_no_internet_2_bar_black_24dp)
				} else if (sensor.rssi < -64) {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_connected_no_internet_3_bar_black_24dp)
				} else {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_connected_no_internet_4_bar_black_24dp)
				}
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

	private val deviceMap = TreeMap<String, Sensor>()
	var clickListener: SensorClickListener? = null
	var longClickListener: SensorClickListener? = null
	val sensors: Collection<Sensor>
		get() = deviceMap.values

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder? {
		val layoutInflater = LayoutInflater.from(parent.context)
		val root = layoutInflater.inflate(R.layout.list_item_device, parent, false)
		return DeviceViewHolder(root)
	}

	override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
		val key = deviceMap.keys.toList()[position]
		val device = deviceMap[key]
		device?.let { holder.setDevice(device) }
	}

	override fun getItemCount(): Int {
		return deviceMap.size
	}

	fun getSelected(): Set<String> {
		val selected = mutableSetOf<String>()
		for ((address, device) in deviceMap) {
			if (device.selected) {
				selected.add(address)
			}
		}
		return selected
	}

	fun getSelectedDevices(): Collection<Sensor> {
		val selected = mutableListOf<Sensor>()
		for ((_,device) in deviceMap) {
			if (device.selected) {
				selected.add(device)
			}
		}
		return selected
	}

	fun setSelected(selected: Set<String>) {
		selected
				.map {
					deviceMap.computeIfAbsent(it, { key ->
						Sensor(key, "Not Detected")
					})
				}
				.forEach { it.selected = true }
	}

	fun updateDevice(scan: ScanResult) {
		val device = deviceMap.computeIfAbsent(scan.device.address, { key ->
			Sensor(key, scan.device.name)
		})
		device.name = scan.device.name
		device.rssi = scan.rssi
		device.timestamp = scan.timestampNanos
		deviceMap.put(device.address, device)
		notifyDataSetChanged()
	}
}