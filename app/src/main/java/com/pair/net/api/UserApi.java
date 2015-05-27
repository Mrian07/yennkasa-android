package com.pair.net.api;

import com.google.gson.JsonObject;
import com.pair.net.HttpResponse;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.Headers;
import retrofit.http.POST;

/**
 * Created by Null-Pointer on 5/27/2015.
 */
public interface UserApi {

    @POST("/api/v1/users/register")
    @Headers("Authorization:kiiboda+=s3cr3te")
    void registerUser(@Body JsonObject user, Callback<HttpResponse> callback);

}
