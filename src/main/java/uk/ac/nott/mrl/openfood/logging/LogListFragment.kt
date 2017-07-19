package uk.ac.nott.mrl.openfood.logging

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import uk.ac.nott.mrl.openfood.R

class LogListFragment : Fragment() {
	override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
	                          savedInstanceState: Bundle?): View? {
		val textView = TextView(activity)
		textView.setText(R.string.hello_blank_fragment)
		return textView
	}
}