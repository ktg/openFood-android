package uk.ac.nott.mrl.openfood.playback

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.list_item_video.view.*
import uk.ac.nott.mrl.openfood.R
import java.io.File
import java.util.*

class VideoListAdapter : RecyclerView.Adapter<VideoListAdapter.VideoViewHolder>() {
	inner class VideoViewHolder(private val rootView: View) : RecyclerView.ViewHolder(rootView) {
		fun setVideo(video: File) {
			rootView.nameText.text = video.name
			Picasso.get()
					.load(video)
					.placeholder(R.drawable.ic_movie_black_24dp)
					.into(rootView.videoImage)
			rootView.checkBox.isChecked = videoSelector?.selectedVideo == video
			rootView.setOnClickListener { _ ->
				val original = videoSelector?.selectedVideo
				videoSelector?.selectedVideo = video
				videoSelector?.validate()
				rootView.checkBox.isChecked = videoSelector?.selectedVideo == video
				if (original != null) {
					val index = videoMap.toList().indexOf(original)
					notifyItemChanged(index)
				}
			}
		}
	}

	private val videoMap = TreeSet<File>()
	var videoSelector: VideoSelector? = null

	companion object {
		private val EXTENSIONS = arrayOf(".mp4", ".webm", ".mkv", ".3gp")
		private val TAG = VideoListAdapter::class.java.simpleName
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
		val layoutInflater = LayoutInflater.from(parent.context)
		val root = layoutInflater.inflate(R.layout.list_item_video, parent, false)
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
		val children = directory.listFiles()
		if(children != null) {
			for (child in children) {
				if (child.isDirectory) {
					scan(child)
				} else if (isVideo(child)) {
					videoMap.add(child)
				}
			}
		}
	}

	private fun isVideo(file: File): Boolean {
		return EXTENSIONS.any { file.name.endsWith(it) }
	}
}