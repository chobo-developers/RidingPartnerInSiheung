package com.first.ridingpartnerinsiheung.scenarios.main.maps.routeSearchPage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PointF
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.first.ridingpartnerinsiheung.R
import com.first.ridingpartnerinsiheung.api.place.ApiObject
import com.first.ridingpartnerinsiheung.api.place.PlaceDetail
import com.first.ridingpartnerinsiheung.databinding.FragmentRouteSearchBinding
import com.first.ridingpartnerinsiheung.extensions.showToast
import com.first.ridingpartnerinsiheung.scenarios.main.maps.MapActivity
import com.first.ridingpartnerinsiheung.scenarios.main.maps.navigationMap.NavigationFragment
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.*
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.util.FusedLocationSource
import com.naver.maps.map.util.MarkerIcons
import kotlinx.coroutines.flow.collect
import retrofit2.Call
import retrofit2.Response

class RouteSearchFragment : Fragment(), OnMapReadyCallback {

    private lateinit var binding: FragmentRouteSearchBinding
    private val viewModel by viewModels<RouteSearchViewModel>()

    private lateinit var mNaverMap: NaverMap
    private lateinit var locationSource: FusedLocationSource
    private var locationManager: LocationManager? = null

    lateinit var startPlaceData: PlaceDetail.Place;
    lateinit var endPlaceData: PlaceDetail.Place;

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        initBinding()
        initMapView()
        initObserves()
        initMapView()
        return binding.root
    }

    private fun initMapView(){
        locationSource = FusedLocationSource(this, PERMISSION_CODE)

        val fm = childFragmentManager
        val mapFragment = fm.findFragmentById(R.id.routeMapView) as MapFragment?
            ?: MapFragment.newInstance().also {
                fm.beginTransaction().add(R.id.routeMapView, it).commit()
            }
        mapFragment?.getMapAsync(this)

        locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private fun initBinding(
        inflater: LayoutInflater = this.layoutInflater,
        container: ViewGroup? = null
    ) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_route_search, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
    }

    private fun initObserves(){
        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
            viewModel.event.collect(){ event ->
                when(event){
                    RouteSearchViewModel.RouteSearchEvent.SetStartPlace -> setPlace("start")
                    RouteSearchViewModel.RouteSearchEvent.SetEndPlace -> setPlace("end")
                    RouteSearchViewModel.RouteSearchEvent.StartNavigation -> startNavigation()
                }
            }
        }
    }

    override fun onMapReady(naverMap: NaverMap) {
        mNaverMap = naverMap
        mNaverMap.locationSource = locationSource
        mNaverMap.locationTrackingMode = LocationTrackingMode.Follow

        setOverlay()
    }

    private fun setOverlay() {
        mNaverMap.locationTrackingMode = LocationTrackingMode.Follow
        val locationOverlay = mNaverMap.locationOverlay
        locationOverlay.subIcon =
            OverlayImage.fromResource(com.naver.maps.map.R.drawable.navermap_location_overlay_icon)
        locationOverlay.subIconWidth = 40
        locationOverlay.subIconHeight = 40
        locationOverlay.subAnchor = PointF(0.5f, 0.5f)
    }

    // ???????????? ??????
    private fun marker(
        latLng: LatLng,
        title: String
    ): Marker {
        val marker = Marker()
        marker.position = latLng
        marker.map = mNaverMap
        marker.width = 50
        marker.height = 70
        marker.captionText = title
        if(title == "?????????"){
            marker.icon = MarkerIcons.RED
        }
        return marker
    }

    private fun setPlace(type: String) {
        val place = when(type){
            "start" -> binding.startPlace.text.toString()
            "end" -> binding.endPlace.text.toString()
            else -> ""
        }

        val call = ApiObject.retrofitService.getPlaces(
            coords = "37.5055196999994,126.94229594606628",
            query = place
        )

        call.enqueue(object : retrofit2.Callback<PlaceDetail> {
            override fun onResponse(call: Call<PlaceDetail>, response: Response<PlaceDetail>) {
                if (response.isSuccessful) {
                    try {
                        val placeDetail: List<PlaceDetail.Place> = response.body()!!.place
                        Log.d("??????", "????????????")//???????????????????????????

                        setListView(type, placeDetail)

                    }catch (e : NullPointerException){
                        Log.d("??????", e.message.toString())
                    }
                }
                else {
                    Log.d("??????", "??????")
                }
            }
            override fun onFailure(call: Call<PlaceDetail>, t: Throwable) {
                Log.d("??????", t.message.toString())
            }
        })
        Log.d("??????", "????????? ?????? ??????")
    }

    private fun setListView(type: String, placeDetail: List<PlaceDetail.Place>){
        val placeAdapter = PlaceAdapter(requireContext(), placeDetail)
        binding.placeListView.adapter = placeAdapter
        binding.placeListView.visibility = View.VISIBLE

        binding.placeListView.setOnItemClickListener { adapterView, view, i, l ->
            if (type == "start") {
                startPlaceData = placeDetail[i]
                binding.placeListView.visibility = View.GONE
                binding.startPlace.setText(startPlaceData.title)

                val startPlaceLatLng = LatLng(startPlaceData.y.toDouble(), startPlaceData.x.toDouble())
                marker(startPlaceLatLng, "?????????")
                val cameraUpdate = CameraUpdate.scrollAndZoomTo(startPlaceLatLng, 17.0).animate(
                    CameraAnimation.Easing)
                mNaverMap.moveCamera(cameraUpdate)

            } else if(type == "end"){
                endPlaceData = placeDetail[i]
                binding.placeListView.visibility = View.GONE
                binding.endPlace.setText(endPlaceData.title)

                val endPlaceLatLng = LatLng(endPlaceData.y.toDouble(), endPlaceData.x.toDouble())
                marker(endPlaceLatLng, "?????????")
                val cameraUpdate = CameraUpdate.scrollAndZoomTo(endPlaceLatLng, 17.0).animate(
                    CameraAnimation.Easing)
                mNaverMap.moveCamera(cameraUpdate)
            }
            if (binding.startPlace.text.toString().isNotEmpty() && binding.endPlace.text.toString().isNotEmpty()) {
                binding.buttonLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun startNavigation(){
        // ??????????????? ???????????? ??????
        val bundle = Bundle()

        val startLatLng = LatLng(startPlaceData.x.toDouble() , startPlaceData.y.toDouble())
        val startPlaceId = startPlaceData.id
        val startName = startPlaceData.title
        val endLatLng = LatLng(endPlaceData.x.toDouble(), endPlaceData.y.toDouble())
        val endPlaceId = endPlaceData.id
        val endName = endPlaceData.title

        bundle.putString("startParam", "${startPlaceData.x.toDouble()},${startPlaceData.y.toDouble()},placeid=$startPlaceId,name=$startName")
        bundle.putString("destinationParam", "${endPlaceData.x.toDouble()},${endPlaceData.y.toDouble()},placeid=$endPlaceId,name=$endName")
        bundle.putParcelable("startLatLng", startLatLng)
        bundle.putParcelable("endLatLng", endLatLng)

        (activity as MapActivity).setFragment(NavigationFragment(), bundle)
    }

    //  ?????? ??????
    private val PERMISSION_CODE = 100

    private fun requirePermissions(){
        val permissions=arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

        val isAllPermissionsGranted = permissions.all { //  permissions??? ?????? ?????? ??????
            ActivityCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
        if (isAllPermissionsGranted) {    //  ?????? ????????? ???????????? ?????? ??????
            //permissionGranted()
        } else { //  ????????? ?????? ?????? ?????? ??????
            ActivityCompat.requestPermissions(requireActivity(), permissions, PERMISSION_CODE)
        }
    }

    // ?????? ?????? ????????? ??? ????????? ????????? ?????? ????????? ?????? ????????? argument??? ?????? ??? ??????
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == PERMISSION_CODE){
            if(grantResults.isNotEmpty()){
                for(grant in grantResults){
                    if(grant == PackageManager.PERMISSION_GRANTED) {
                        /*no-op*/
                    }else{
                        permissionDenied()
                        requirePermissions()
                    }
                }
            }
        }
    }

    // ????????? ?????? ?????? ??????
    private fun permissionDenied() {
        showToast("?????? ????????? ???????????????")
    }
}