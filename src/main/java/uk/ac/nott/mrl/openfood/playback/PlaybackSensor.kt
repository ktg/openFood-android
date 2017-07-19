package uk.ac.nott.mrl.openfood.playback

import com.mbientlab.metawear.MetaWearBoard
import com.mbientlab.metawear.data.Acceleration

class PlaybackSensor(address: String) {
	companion object {
		val DIFFERENCE = 0.02f
		val TIMEOUT = 3000
		val COUNT_NEEDED = 10
	}

	val address = address
	var board: MetaWearBoard? = null
	private var lastMoved = 0L
	private var x = Float.MIN_VALUE
	private var y = Float.MIN_VALUE
	private var z = Float.MIN_VALUE
	private var count = 0

	fun update(data: Acceleration) {
		val currentTime = System.currentTimeMillis()
		if (x != Float.MIN_VALUE && (data.x() < x - DIFFERENCE || data.x() > x + DIFFERENCE
				|| data.y() < y - DIFFERENCE || data.y() > y + DIFFERENCE
				|| data.z() < z - DIFFERENCE || data.z() > z + DIFFERENCE)) {
			count++
			if (count >= COUNT_NEEDED) {
				lastMoved = currentTime
			}
		} else if (lastMoved + TIMEOUT < currentTime) {
			count = 0
		} else {
			count = Math.max(count - 1, 0)
		}
		x = data.x()
		y = data.y()
		z = data.z()
	}

	fun isMoving(timestamp: Long): Boolean {
		return lastMoved + TIMEOUT >= timestamp
	}

	val isConnected: Boolean
		get() {
			return board?.isConnected == true
		}
}