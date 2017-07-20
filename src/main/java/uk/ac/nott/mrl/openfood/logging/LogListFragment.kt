package uk.ac.nott.mrl.openfood.logging

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_list.view.*
import uk.ac.nott.mrl.openfood.R

class LogListFragment : Fragment() {
	override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
	                          savedInstanceState: Bundle?): View? {
		val view = inflater!!.inflate(R.layout.fragment_list, container, false)

		view.deviceList.layoutManager = LinearLayoutManager(context)
		val adapter = LogListAdapter(context)
		view.deviceList.adapter = adapter
		adapter.scan(context.getExternalFilesDir("logs"))
		return view
	}
}