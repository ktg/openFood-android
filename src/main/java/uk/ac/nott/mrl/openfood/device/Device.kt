package uk.ac.nott.mrl.openfood.logging

import com.mbientlab.metawear.MetaWearBoard
import com.mbientlab.metawear.module.Led

class Device(address: String, name: String) {
	val address = address
	val name = name
	var rssi = Integer.MIN_VALUE
	var board: MetaWearBoard? = null
	private var _selected = false
	var selected: Boolean
		get() = _selected
		set(value) {
			_selected = value
			if(!value) {
				disconnect()
			}
		}
	var timestamp = 0L

	fun isConnected() : Boolean {
		return board != null && board!!.isConnected
	}

	fun disconnect() {
		board?.tearDown()
		board?.getModule(Led::class.java)?.stop(true)
		board?.disconnectAsync()
		board = null
	}
}