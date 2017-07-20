package uk.ac.nott.mrl.openfood.logging

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.support.v4.content.ContextCompat.startActivity
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.list_item_log.view.*
import uk.ac.nott.mrl.openfood.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class LogListAdapter(val context: Context) : RecyclerView.Adapter<LogListAdapter.VideoViewHolder>() {
	inner class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val rootView = view

		fun setVideo(logFile: File) {
			rootView.nameText.text = logFile.name
			var timestamp = Date(logFile.lastModified())
			if (logFile.name.startsWith("OpenFoodLog ")) {
				val timestring = logFile.name.substring(12, logFile.name.length - 4)
				timestamp = Date(timestring.toLong())
			}
			rootView.timeText.text = dateFormatter.format(timestamp)
			rootView.setOnClickListener { _ ->
				val intentShareFile = Intent(Intent.ACTION_SEND)
				val fileWithinMyDir = logFile

				if (fileWithinMyDir.exists()) {
					intentShareFile.type = "text/csv"
					intentShareFile.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(logFile))
					intentShareFile.putExtra(Intent.EXTRA_SUBJECT, logFile.name)
					intentShareFile.putExtra(Intent.EXTRA_TEXT, "Sharing Log File...")

					startActivity(context, Intent.createChooser(intentShareFile, "Share Log File"), null)
				}
			}
		}
	}

	private val videoMap = TreeSet<File>()

	companion object {
		private val TAG = LogListAdapter::class.java.simpleName
		private val dateFormatter = SimpleDateFormat("HH:mm E, d MMM", Locale.ENGLISH)
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder? {
		val layoutInflater = LayoutInflater.from(parent.context)
		val root = layoutInflater.inflate(R.layout.list_item_log, parent, false)
		return VideoViewHolder(root)
	}

	override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
		val file = videoMap.toList()[position]
		holder.setVideo(file)
	}

	override fun getItemCount(): Int {
		return videoMap.size
	}

	fun scan(directory: File) {
		Log.i(TAG, directory.absolutePath)
		for (child in directory.listFiles()) {
			if (child.isDirectory) {
				scan(child)
			} else if (isVideo(child)) {
				videoMap.add(child)
			}
		}
	}

	private fun isVideo(file: File): Boolean {
		return file.name.endsWith(".csv")
	}
}