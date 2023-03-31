package com.example.locationapp

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.locationapp.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.kakao.sdk.user.model.User

class LoginActivity : AppCompatActivity() {

    private lateinit var emailLoginResult : ActivityResultLauncher<Intent>
    private lateinit var pendingUser : User

    private val callback: (OAuthToken?, Throwable?) -> Unit = { oAuthToken, error ->
        if (error != null) {
            //로그인 실패
            showErrorToast()
            error.printStackTrace()
        } else if (oAuthToken != null) {
            //로그인 성공
            Log.e("loginActivity", "login in with kakao account : token : ${oAuthToken.toString()}")
            getKakaoAccountInfo()
        }
    }


    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kakao SDK 초기화
        KakaoSdk.init(this, getString(R.string.native_app_key))

        emailLoginResult = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ){
            if(it.resultCode == RESULT_OK){
                val email = it.data?.getStringExtra("email")
                if(email == null){
                    showErrorToast()
                    return@registerForActivityResult
                }else{
                    signInFirebase(pendingUser, email)
                }
            }
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.kakaoTalkLoginButton.setOnClickListener {
            if (UserApiClient.instance.isKakaoTalkLoginAvailable(this)) {
                //카카오톡 로그인
                UserApiClient.instance.loginWithKakaoTalk(this) { token, error ->
                    if (error != null) {
                        //카카오톡 로그인 실패

                        if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                            return@loginWithKakaoTalk
                        }
                        UserApiClient.instance.loginWithKakaoAccount(this, callback = callback)
                    } else if (token != null) {

                        if (Firebase.auth.currentUser == null) {
                            //카카오톡에서 정보를 가져와서 파이어베이스 로그인
                            getKakaoAccountInfo()
                        } else {
                            //정보가 있는 경우
                            navigateToMapActivity()
                        }
                        Log.e("loginActivity", "token == $token")
                        //로그인 성공
                    }
                }
            } else {
                //카카오계정 로그인
                UserApiClient.instance.loginWithKakaoAccount(this, callback = callback)

            }
        }


    }

    private fun navigateToMapActivity() {
        startActivity(Intent(this, MapActivity::class.java))
    }

    private fun getKakaoAccountInfo() {
        UserApiClient.instance.me { user, error ->
            if (error != null) {
                showErrorToast()
                Log.e("LoginActivity", "getKakaoAccountInfo :: fail $error")

            } else if (user != null) {
                //사용자 정보 요청 성공
                Log.e(
                    "LoginActivity",
                    "user : 회원 번호 : ${user.id} /이메일 : ${user.kakaoAccount?.email} / 닉네임 : ${user.kakaoAccount?.profile?.nickname} / 프로필 사진 : ${user.kakaoAccount?.profile?.thumbnailImageUrl}"
                )


                checkKakaoUserData(user)
            }

        }
    }

    private fun checkKakaoUserData(user: User) {
        val kakaoEmail = user.kakaoAccount?.email.orEmpty()

        if (kakaoEmail.isEmpty()) {
            //추가로 이메일을 받는 작업
            emailLoginResult.launch(Intent(this, EmailLoginActivity::class.java))
            return
        }

        signInFirebase(user, kakaoEmail)
    }

    private fun signInFirebase(user: User, email: String) {
        val uId = user.id.toString()
        pendingUser = user
        Firebase.auth.createUserWithEmailAndPassword(email, uId).addOnCompleteListener {
            if(it.isSuccessful){
                //다음 과정
                updateFirebaseDatabase(user)
            }else
                showErrorToast()

        }.addOnFailureListener {
            //이미 가입된 계정
            if(it is FirebaseAuthUserCollisionException) {
                Firebase.auth.signInWithEmailAndPassword(email, uId).addOnCompleteListener {
                    if(it.isSuccessful){
                        //다음 과정
                        updateFirebaseDatabase(user)
                    }else
                        showErrorToast()

                }.addOnFailureListener { error ->
                    error.printStackTrace()
                    showErrorToast()
                }
            }else
                showErrorToast()
        }
    }

    private fun updateFirebaseDatabase(user : User){
        val uid = Firebase.auth.currentUser?.uid.orEmpty()
        val personMap = mutableMapOf<String, Any>()
        personMap["uid"] = uid
        personMap["name"] = user.kakaoAccount?.profile?.nickname.orEmpty()
        personMap["profilePhoto"] = user.kakaoAccount?.profile?.thumbnailImageUrl.orEmpty()


        Firebase.database.reference.child("Person").child(uid).updateChildren(personMap)
    }


    private fun showErrorToast() {
        Toast.makeText(this, "사용자 로그인에 실패했습니다.", Toast.LENGTH_SHORT).show()
    }
}