package uk.ac.nott.mrl.openfood.sensor

import com.mbientlab.metawear.MetaWearBoard
import com.mbientlab.metawear.module.Led

class Sensor(address: String, name: String) {
	companion object {
	    val TIMEOUT = 2000
	}

	val address = address
	var name = name
	var rssi = Integer.MIN_VALUE
	var board: MetaWearBoard? = null
	var connecting = false
	var selected: Boolean = false
	var timestamp = 0L

	fun isConnected() : Boolean {
		return board?.isConnected == true
	}

	fun hasTimedOut(now: Long): Boolean {
		return timestamp + TIMEOUT < now
	}
}