package uk.ac.nott.mrl.openfood.playback

import java.io.File

interface VideoSelector {
	var selectedVideo: File?

	fun validate()
}