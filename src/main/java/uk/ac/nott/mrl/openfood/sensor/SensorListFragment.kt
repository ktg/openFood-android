package uk.ac.nott.mrl.openfood.sensor

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.mbientlab.metawear.MetaWearBoard
import kotlinx.android.synthetic.main.fragment_list.view.*
import uk.ac.nott.mrl.openfood.R
import java.util.*


class SensorListFragment : Fragment() {
	private val scanCallback = object : ScanCallback() {
		override fun onScanResult(callbackType: Int, result: ScanResult) {
			//Log.i(TAG, result.device.name + ", " + result.device.address + ": " + result.rssi)
			if (context is SensorListAdapterHolder) {
				(context as SensorListAdapterHolder).adapter.updateDevice(result)
			}
		}
	}

	companion object {
		val TAG = SensorListFragment::class.java.simpleName!!
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val view = inflater.inflate(R.layout.fragment_list, container, false)

		view.deviceList.layoutManager = LinearLayoutManager(context)
		if (context is SensorListAdapterHolder) {
			view.deviceList?.adapter = (context as SensorListAdapterHolder).adapter
			Log.i(TAG, "Attached adapter " + view?.deviceList?.adapter)
		}

		return view
	}

	override fun onAttach(context: Context?) {
		super.onAttach(context)
		if (context is SensorListAdapterHolder) {
			view?.deviceList?.adapter = context.adapter
			Log.i(TAG, "Attached adapter " + view?.deviceList?.adapter)
		}
	}

	override fun onDetach() {
		super.onDetach()
		view?.deviceList?.adapter = null
	}

	override fun onStart() {
		super.onStart()
		val scanSettings = ScanSettings.Builder().build()
		val scanFilters = Collections.singletonList(ScanFilter.Builder().setServiceUuid(ParcelUuid(MetaWearBoard.METAWEAR_GATT_SERVICE)).build())
		val bluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
		bluetoothManager?.adapter?.bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
		Log.i(TAG, "Scan started")
	}

	override fun onStop() {
		super.onStop()
		val bluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
		bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
	}
}
