package uk.ac.nott.mrl.openfood.playback

import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import kotlinx.android.synthetic.main.list_item_video.view.*
import uk.ac.nott.mrl.openfood.R
import java.io.File
import java.util.*

class VideoListAdapter : RecyclerView.Adapter<VideoListAdapter.VideoViewHolder>() {
	inner class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val rootView = view

		fun setVideo(video: File) {
			rootView.nameText.text = video.name
			Glide.with(rootView)
					.load(video)
					.apply(RequestOptions().placeholder(R.drawable.ic_movie_black_24dp))
					.into(rootView.videoImage)
			rootView.checkBox.isChecked = videoSelector?.selectedVideo == video
			rootView.setOnClickListener { _ ->
				val original = videoSelector?.selectedVideo
				videoSelector?.selectedVideo = video
				videoSelector?.validate()
				rootView.checkBox.isChecked = videoSelector?.selectedVideo == video
				if(original != null) {
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

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder? {
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
		for(child in directory.listFiles()) {
			if(child.isDirectory) {
				scan(child)
			} else if(isVideo(child)) {
				videoMap.add(child)
			}
		}
	}

	private fun isVideo(file: File): Boolean {
		return EXTENSIONS.any { file.name.endsWith(it) }
	}
}