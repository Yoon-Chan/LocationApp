package com.example.locationapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.locationapp.databinding.ActivityMapBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.kakao.sdk.common.util.Utility

class MapActivity : AppCompatActivity(), OnMapReadyCallback, OnMarkerClickListener {

    private lateinit var fusedLocationClient : FusedLocationProviderClient
    private lateinit var binding : ActivityMapBinding
    private lateinit var googleMap : GoogleMap
    private val markerMap = hashMapOf<String, Marker>()

    private var trackingPersonId : String = ""

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

        setupEmojiAnimationView()



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
    private fun setupEmojiAnimationView(){
        binding.emojiLottieAnimation.setOnClickListener {
            if(trackingPersonId != "") {
                val lastEmoji = mutableMapOf<String, Any>()
                lastEmoji["type"] = "sunglass"
                lastEmoji["lastModifier"] = System.currentTimeMillis()
                Firebase.database.reference.child("Emoji").child(trackingPersonId)
                    .updateChildren(lastEmoji)
            }

            binding.emojiLottieAnimation.playAnimation()
            binding.dummyLottieAnimation.animate()
                .scaleX(3f)
                .scaleY(3f)
                .alpha(0f)
                .withStartAction {
                    binding.dummyLottieAnimation.scaleX = 1f
                    binding.dummyLottieAnimation.scaleY = 1f
                    binding.dummyLottieAnimation.alpha = 1f
                }.withEndAction {
                    binding.dummyLottieAnimation.scaleX = 1f
                    binding.dummyLottieAnimation.scaleY = 1f
                    binding.dummyLottieAnimation.alpha = 1f
                }.start()
        }
        binding.centerLottieAnimationView.speed = 3f
        binding.emojiLottieAnimation.speed = 3f
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

                    if(uid == trackingPersonId){
                        googleMap.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(LatLng(person.latitude ?: 0.0, person.longitude ?: 0.0))
                                    .zoom(16.0f)
                                    .build()
                            )
                        )
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


        Firebase.database.reference.child("Emoji").child(Firebase.auth.currentUser?.uid ?: "")
            .addValueEventListener(object : ValueEventListener{
                override fun onCancelled(error: DatabaseError) {

                }

                override fun onDataChange(snapshot: DataSnapshot) {
                    binding.centerLottieAnimationView.playAnimation()
                    binding.centerLottieAnimationView.animate()
                        .scaleX(7f)
                        .scaleY(7f)
                        .alpha(0.3f)
                        .setDuration(binding.centerLottieAnimationView.duration)
                        .withEndAction{
                            binding.centerLottieAnimationView.scaleX = 0f
                            binding.centerLottieAnimationView.scaleY = 0f
                            binding.centerLottieAnimationView.alpha = 0f
                        }.start()
                }
            })
    }


    private fun makeNewMarker(person : Person, uid : String) : Marker? {
        val marker = googleMap.addMarker(
            MarkerOptions().position(LatLng(person.latitude ?: 0.0, person.longitude ?: 0.0)).title(person.name.orEmpty())
        ) ?: return null


        marker.tag = uid

        //Glide 라이브러리를 이용하여 이미지 프로필 사진을 가져오도록 한다.
        Glide.with(this)
            .asBitmap()
            .load(person.profilePhoto)
            .override(200)
            .transform(RoundedCorners(60))
            .listener(object  : RequestListener<Bitmap>{
                override fun onResourceReady(
                    resource: Bitmap?,
                    model: Any?,
                    target: Target<Bitmap>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    runOnUiThread{
                        resource?.let {
                            marker.setIcon(
                                BitmapDescriptorFactory.fromBitmap(
                                    it
                                )
                            )
                        }
                    }
                    return true
                }

                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap>?,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }

            }).submit()



        return marker
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        //최대를 줌을 땡겼을 때의 레벨을 설정
        googleMap.setMaxZoomPreference(20.0f)
        //최소를 줌을 땡겼을 때의 레벨
        googleMap.setMinZoomPreference(10.0f)


        googleMap.setOnMarkerClickListener(this)
        googleMap.setOnMapClickListener {
            trackingPersonId = ""
        }

        // Add a marker in Sydney and move the camera
//        val sydney = LatLng(-34.0, 151.0)
//        googleMap.addMarker(MarkerOptions()
//            .position(sydney)
//            .title("Marker in Sydney"))
//        googleMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        //custom event
        trackingPersonId = marker.tag as? String ?: ""
        val bottomSheetBehavior = BottomSheetBehavior.from(binding.emojiBottomSheetLayout)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        return false
    }
}