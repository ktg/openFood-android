package uk.ac.nott.mrl.openfood.logging

import com.mbientlab.metawear.MetaWearBoard

class Device(address: String, name: String) {
	val address = address
	val name = name
	var rssi = -128
	var board: MetaWearBoard? = null
	var logging = false
	var timestamp = 0
}