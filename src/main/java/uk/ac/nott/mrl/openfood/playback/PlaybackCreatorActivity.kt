package uk.ac.nott.mrl.openfood.playback

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import kotlinx.android.synthetic.main.activity_playback_creator.*
import uk.ac.nott.mrl.openfood.NavigationActivity
import uk.ac.nott.mrl.openfood.R
import uk.ac.nott.mrl.openfood.sensor.*
import java.io.File

class PlaybackCreatorActivity : AppCompatActivity(), SensorListAdapterHolder, VideoSelector {
	inner class SectionsPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {
		private val pages = listOf(VideoListFragment(), SensorListFragment())

		override fun getItem(position: Int): Fragment {
			return pages[position]
		}

		override fun getCount(): Int {
			return pages.size
		}
	}

	private lateinit var pagerAdapter: SectionsPagerAdapter
	override val adapter = SensorListAdapter()
	override var selectedVideo: File? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_playback_creator)

		setSupportActionBar(toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		pagerAdapter = SectionsPagerAdapter(supportFragmentManager)
		pager.adapter = pagerAdapter
		pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
			override fun onPageScrollStateChanged(state: Int) {
			}

			override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
			}

			override fun onPageSelected(position: Int) {
				if (position == 0) {
					backButton.visibility = View.GONE
				} else {
					backButton.visibility = View.VISIBLE
				}
				if (position == (pagerAdapter.count - 1)) {
					nextButton.visibility = View.GONE
					createButton.visibility = View.VISIBLE
				} else {
					nextButton.visibility = View.VISIBLE
					createButton.visibility = View.GONE
				}
				validate()
			}
		})
		adapter.clickListener = {
			validate()
		}
	}

	override fun onStart() {
		super.onStart()
		val sharedPreferences = getSharedPreferences(NavigationActivity.PREF_ID, 0)
		if (sharedPreferences.contains(NavigationActivity.PREF_PLAYBACK)) {
			adapter.setSelected(sharedPreferences.getStringSet(NavigationActivity.PREF_PLAYBACK, emptySet()))
		} else {
			adapter.setSelected(sharedPreferences.getStringSet(NavigationActivity.PREF_LOGGED, emptySet()))
		}
		if (sharedPreferences.contains(NavigationActivity.PREF_VIDEO)) {
			selectedVideo = File(sharedPreferences.getString(NavigationActivity.PREF_VIDEO, ""))
		}
	}

	fun nextPage(view: View) {
		pager.currentItem = pager.currentItem + 1
	}

	fun backPage(view: View) {
		pager.currentItem = Math.max(pager.currentItem - 1, 0)
	}

	fun createPlayback(view: View) {
		startActivity(Intent(this, PlaybackActivity::class.java)
				.putExtra("addresses", adapter.getSelected().toTypedArray())
				.putExtra("video", selectedVideo?.absolutePath))
	}

	override fun validate() {
		getSharedPreferences(NavigationActivity.PREF_ID, 0).edit {
			putStringSet(NavigationActivity.PREF_PLAYBACK, adapter.getSelected())
			putString(NavigationActivity.PREF_VIDEO, selectedVideo?.absolutePath)
		}
		createButton.isEnabled = !adapter.getSelected().isEmpty() && selectedVideo != null
	}
}
