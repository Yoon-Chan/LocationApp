package com.example.locationapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.example.locationapp.databinding.ActivityMapBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.kakao.sdk.common.util.Utility

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var fusedLocationClient : FusedLocationProviderClient
    private lateinit var binding : ActivityMapBinding
    private lateinit var googleMap : GoogleMap
    private val markerMap = hashMapOf<String, Marker>()

    private val locationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            //새로 요청된 위치 정보
            for(location in locationResult.locations){

                //새로 요청된 위치의 위경도
                Log.e("MapActivity", "onLocationResult : ${location.latitude}, ${location.longitude}")

                //파이어베이스에 내 위치 업로드
                val uid = Firebase.auth.currentUser?.uid.orEmpty()

                if(uid.isEmpty()){

                }
                val locationMap = mutableMapOf<String, Any>()
                locationMap["latitude"] = location.latitude
                locationMap["longitude"] = location.longitude
                Firebase.database.reference.child("Person").child(uid).updateChildren(locationMap)


                //지도에 마커 움직이기

            }
        }
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){permissions ->
        when{
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                //fine location 권한이 있다.
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                //coarse location 권한이 있다.
                getCurrentLocation()
            }
            else -> {
                //TODO 설정으로 보내기 or 교육용 팝을 띄워서 다시 권한 요청하기
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //권한 요청
        requestLocationPermission()

        setupFirebaseDatabase()


    }

    override fun onResume() {
        super.onResume()

        getCurrentLocation()
    }

    override fun onPause() {
        super.onPause()

        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun getCurrentLocation() {
        val locationRequest = LocationRequest
            .Builder(Priority.PRIORITY_HIGH_ACCURACY, 5 * 1000).build()

        //권한 확인
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermission()

            return
        }

        //권한이 있는 상태
        fusedLocationClient.requestLocationUpdates(locationRequest,locationCallback, Looper.getMainLooper())

        //마지막 위치 지도 이동
        fusedLocationClient.lastLocation.addOnSuccessListener {
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16.0f)
            )
        }
    }

    //권한 요청
    private fun requestLocationPermission(){
        locationPermissionRequest.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun setupFirebaseDatabase() {
        Firebase.database.reference.child("Person")
            .addChildEventListener(object : ChildEventListener{
                override fun onCancelled(error: DatabaseError) {

                }

                override fun onChildRemoved(snapshot: DataSnapshot) {

                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    val person = snapshot.getValue(Person::class.java) ?: return
                    val uid = person.uid ?: return




                    if(markerMap[uid] == null) {
                        markerMap[uid] = makeNewMarker(person, uid) ?: return
                    }else {
                        markerMap[uid]?.position = LatLng(person.latitude ?: 0.0, person.longitude ?: 0.0)
                    }
                }

                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {

                }

                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val person = snapshot.getValue(Person::class.java) ?: return
                    val uid = person.uid ?: return


                    if(markerMap[uid] == null) {
                        markerMap[uid] = makeNewMarker(person,uid) ?: return
                    }
                }
            })
    }


    private fun makeNewMarker(person : Person, uid : String) : Marker? {
        val marker = googleMap.addMarker(
            MarkerOptions().position(LatLng(person.latitude ?: 0.0, person.longitude ?: 0.0)).title(person.name.orEmpty())
        ) ?: return null

        return marker
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        //최대를 줌을 땡겼을 때의 레벨을 설정
        googleMap.setMaxZoomPreference(20.0f)
        //최소를 줌을 땡겼을 때의 레벨
        googleMap.setMinZoomPreference(10.0f)


        // Add a marker in Sydney and move the camera
//        val sydney = LatLng(-34.0, 151.0)
//        googleMap.addMarker(MarkerOptions()
//            .position(sydney)
//            .title("Marker in Sydney"))
//        googleMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
    }
}