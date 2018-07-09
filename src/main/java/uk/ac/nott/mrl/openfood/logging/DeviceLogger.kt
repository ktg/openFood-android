package uk.ac.nott.mrl.openfood.logging

import android.util.Log
import java.io.File
import java.io.FileWriter

class DeviceLogger {
	companion object {
		private val TAG = DeviceLogger::class.java.simpleName
	}
	private var writer: FileWriter? = null
	private var _logging: Boolean = false
	var directory: File? = null
	val isLogging: Boolean
		get() = _logging

	fun log(line: String) {
		_logging = true
		writer?.write(line)
	}

	fun start() {
		if (writer == null) {
			if(directory?.exists() == false) {
				Log.i(TAG, "Doesn't exist " + directory?.absolutePath)
				if (directory?.mkdirs() == false) {
					Log.i(TAG, "Didn't create " + directory?.absolutePath)
				}
			}
			val file = File(directory, "OpenFoodLog " + System.currentTimeMillis() + ".csv")
			Log.i(TAG, file.absolutePath)
			writer = FileWriter(file)
		}
		_logging = true
	}

	fun stop() {
		_logging = false
		writer?.flush()
		writer?.close()
		writer = null
	}
}