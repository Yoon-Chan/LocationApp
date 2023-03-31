package com.example.locationapp

import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.locationapp.databinding.ActivityLoginBinding
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient

class LoginActivity : AppCompatActivity() {

    private val callback : (OAuthToken?, Throwable?) -> Unit = {oAuthToken, error ->
        if(error != null){
            //로그인 실패
        }else if (oAuthToken != null){
            //로그인 성공
            Log.e("loginActivity", "login in with kakao account : token : ${oAuthToken.toString()}")
        }
    }


    private lateinit var binding : ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kakao SDK 초기화
        KakaoSdk.init(this, getString(R.string.native_app_key))

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.kakaoTalkLoginButton.setOnClickListener {
            if(UserApiClient.instance.isKakaoTalkLoginAvailable(this)){
                //카카오톡 로그인
                UserApiClient.instance.loginWithKakaoTalk(this){token, error ->
                    if(error != null){
                        //카카오톡 로그인 실패

                        if(error is ClientError && error.reason == ClientErrorCause.Cancelled){
                            return@loginWithKakaoTalk
                        }
                        UserApiClient.instance.loginWithKakaoAccount(this, callback = callback )
                    }else if(token!= null){
                        Log.e("loginActivity","token == $token" )
                        //로그인 성공
                    }
                }
            }else{
                //카카오계정 로그인
                UserApiClient.instance.loginWithKakaoAccount(this, callback = callback )

            }
        }


    }
}