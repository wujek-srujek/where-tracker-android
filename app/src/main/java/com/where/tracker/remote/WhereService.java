package com.where.tracker.remote;


import com.where.tracker.dto.LocationListDto;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;


public interface WhereService {

    @POST("where")
    @Headers("Content-Type: application/json;charset=UTF-8")
    Call<Void> saveLocations(@Body LocationListDto locationListDto);
}
