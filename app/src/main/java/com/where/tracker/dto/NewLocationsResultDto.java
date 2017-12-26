package com.where.tracker.dto;


import java.util.ArrayList;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class NewLocationsResultDto implements Parcelable {

    private boolean success;

    private CharSequence uploadMessage;

    private List<NewLocationResultDto> newLocationResultDtos;

    public NewLocationsResultDto(int size) {
        newLocationResultDtos = new ArrayList<>(size);
    }

    private NewLocationsResultDto(Parcel in) {
        success = in.readInt() != 0;
        uploadMessage = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        newLocationResultDtos = in.createTypedArrayList(NewLocationResultDto.CREATOR);
    }

    public static final Parcelable.Creator<NewLocationsResultDto> CREATOR =
            new Parcelable.Creator<NewLocationsResultDto>() {

                @Override
                public NewLocationsResultDto createFromParcel(Parcel in) {
                    return new NewLocationsResultDto(in);
                }

                @Override
                public NewLocationsResultDto[] newArray(int size) {
                    return new NewLocationsResultDto[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(success ? 1 : 0);
        TextUtils.writeToParcel(uploadMessage, parcel, flags);
        parcel.writeTypedList(newLocationResultDtos);
    }
}
