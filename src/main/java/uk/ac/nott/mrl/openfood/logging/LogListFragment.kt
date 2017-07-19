package uk.ac.nott.mrl.openfood.logging

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import uk.ac.nott.mrl.openfood.R

class LogListFragment : Fragment() {
	interface OnFragmentInteractionListener {
		fun onFragmentInteraction(uri: Uri)
	}

	private var mListener: OnFragmentInteractionListener? = null

	override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
	                          savedInstanceState: Bundle?): View? {
		val textView = TextView(activity)
		textView.setText(R.string.hello_blank_fragment)
		return textView
	}

	fun onButtonPressed(uri: Uri) {
		if (mListener != null) {
			mListener?.onFragmentInteraction(uri)
		}
	}

	override fun onAttach(context: Context?) {
		super.onAttach(context)
		if (context is OnFragmentInteractionListener) {
			mListener = context
		}
	}

	override fun onDetach() {
		super.onDetach()
		mListener = null
	}
}