package uk.ac.nott.mrl.openfood.playback

import android.os.Bundle
import android.os.Environment
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_list.view.*
import uk.ac.nott.mrl.openfood.R

class VideoListFragment : Fragment() {
	companion object {
		val TAG = VideoListFragment::class.java.simpleName
	}

	private val adapter = VideoListAdapter()

	override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val view = inflater!!.inflate(R.layout.fragment_list, container, false)

		view.deviceList.layoutManager = LinearLayoutManager(context)
		view.deviceList.adapter = adapter
		adapter.scan(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES))
		if(context is VideoSelector) {
			adapter.videoSelector = context as VideoSelector
		}
		return view
	}
}