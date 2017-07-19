package uk.ac.nott.mrl.openfood.playback

import android.util.Log
import com.mbientlab.metawear.MetaWearBoard
import com.mbientlab.metawear.data.Acceleration

class PlaybackSensor(address: String) {
	companion object {
		val TAG = PlaybackSensor::class.java.simpleName
		val DIFFERENCE = 0.1f
		val TIMEOUT = 3000
	}

	val address = address
	var board: MetaWearBoard? = null
	private var lastMoved = 0L
	private var x = Float.MIN_VALUE
	private var y = Float.MIN_VALUE
	private var z = Float.MIN_VALUE

	fun update(data: Acceleration) {
		val currentTime = System.currentTimeMillis()
		if (x != Float.MIN_VALUE && (data.x() < x - DIFFERENCE || data.x() > x + DIFFERENCE
				|| data.y() < y - DIFFERENCE || data.y() > y + DIFFERENCE
				|| data.z() < z - DIFFERENCE || data.z() > z + DIFFERENCE)) {
			lastMoved = currentTime
		}
		x = data.x()
		y = data.y()
		z = data.z()
	}

	fun isMoving(timestamp: Long) : Boolean {
		return lastMoved + TIMEOUT >= timestamp
	}

	val isConnected: Boolean
		get() {
			return board?.isConnected == true
		}
}