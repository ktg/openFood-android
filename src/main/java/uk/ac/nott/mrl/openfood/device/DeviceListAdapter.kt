package uk.ac.nott.mrl.openfood.device

import android.bluetooth.le.ScanResult
import android.os.SystemClock
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.list_item_device.view.*
import uk.ac.nott.mrl.openfood.R
import java.util.*

class DeviceListAdapter : RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {
	inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val rootView = view

		fun setDevice(device: Device) {
			rootView.nameText.text = device.name
			rootView.macAddressText.text = device.address
			//Log.i("TIME", "Time: " + (SystemClock.elapsedRealtimeNanos() - scan.timestampNanos))
			if (device.isConnected()) {
				rootView.signalIcon.setImageResource(R.drawable.ic_tap_and_play_black_24dp)
			} else if(device.error) {
				if (device.rssi == Integer.MIN_VALUE) {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_null_black_24dp)
				} else if (SystemClock.elapsedRealtimeNanos() - device.timestamp > 10000000000) {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_connected_no_internet_0_bar_black_24dp)
				} else if (device.rssi < -96) {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_connected_no_internet_1_bar_black_24dp)
				} else if (device.rssi < -80) {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_connected_no_internet_2_bar_black_24dp)
				} else if (device.rssi < -64) {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_connected_no_internet_3_bar_black_24dp)
				} else {
					rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_connected_no_internet_4_bar_black_24dp)
				}
			} else if (device.rssi == Integer.MIN_VALUE) {
				rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_null_black_24dp)
			} else if (SystemClock.elapsedRealtimeNanos() - device.timestamp > 10000000000) {
				rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_0_bar_black_24dp)
			} else if (device.rssi < -96) {
				rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_1_bar_black_24dp)
			} else if (device.rssi < -80) {
				rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_2_bar_black_24dp)
			} else if (device.rssi < -64) {
				rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_3_bar_black_24dp)
			} else {
				rootView.signalIcon.setImageResource(R.drawable.ic_signal_cellular_4_bar_black_24dp)
			}

			rootView.checkBox.isEnabled = true
			rootView.checkBox.isChecked = device.selected
			rootView.setOnClickListener { _ ->
				device.selected = !device.selected
				rootView.checkBox.isChecked = device.selected
				clickListener?.onClick(device)
			}
			rootView.setOnLongClickListener {
				longClickListener?.onClick(device)
				longClickListener != null
			}
		}
	}

	private val deviceMap = TreeMap<String, Device>()
	var clickListener: DeviceClickListener? = null
	var longClickListener: DeviceClickListener? = null
	val devices: Collection<Device>
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

	fun getDevice(address: String): Device? {
		return deviceMap[address]
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

	fun getSelectedDevices(): Collection<Device> {
		val selected = mutableListOf<Device>()
		for ((_,device) in deviceMap) {
			if (device.selected) {
				selected.add(device)
			}
		}
		return selected
	}

	fun setSelected(selected: Set<String>) {
		for (selectedDevice in selected) {
			val device = deviceMap.computeIfAbsent(selectedDevice, { key ->
				Device(key, "Not Detected")
			})
			device.selected = true
		}
	}

	fun updateDevice(scan: ScanResult) {
		val device = deviceMap.computeIfAbsent(scan.device.address, { key ->
			Device(key, scan.device.name)
		})
		device.name = scan.device.name
		device.rssi = scan.rssi
		device.timestamp = scan.timestampNanos
		deviceMap.put(device.address, device)
		notifyDataSetChanged()
	}
}