package uk.ac.nott.mrl.openfood.logging

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_list.view.*
import uk.ac.nott.mrl.openfood.R
import java.io.File

class LogListFragment : Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val view = inflater.inflate(R.layout.fragment_list, container, false)

		context?.let {
			view.deviceList.layoutManager = LinearLayoutManager(it)
			val adapter = LogListAdapter(it)
			view.deviceList.adapter = adapter
			adapter.scan(File(it.filesDir,"logs"))
		}
		return view
	}
}