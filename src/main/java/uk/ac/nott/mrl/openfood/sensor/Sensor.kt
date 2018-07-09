package uk.ac.nott.mrl.openfood.sensor

import com.mbientlab.metawear.MetaWearBoard

class Sensor(val address: String, var name: String) {
	companion object {
		const val TIMEOUT = 2000
	}

	var rssi = Integer.MIN_VALUE
	var board: MetaWearBoard? = null
	var connecting = false
	var selected: Boolean = false
	var timestamp = 0L

	fun isConnected(): Boolean {
		return board?.isConnected == true
	}

	fun hasTimedOut(now: Long): Boolean {
		return timestamp + TIMEOUT < now
	}
}