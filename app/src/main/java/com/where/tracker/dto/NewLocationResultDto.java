package com.where.tracker.dto;


import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
public class NewLocationResultDto implements Parcelable {

    private boolean uploadSuccess;

    private CharSequence uploadMessage;

    private boolean saveSuccess;

    private CharSequence saveMessage;

    private LocationDto locationDto;

    private NewLocationResultDto(Parcel in) {
        uploadSuccess = in.readInt() != 0;
        uploadMessage = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        saveSuccess = in.readInt() != 0;
        saveMessage = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        locationDto = in.readTypedObject(LocationDto.CREATOR);
    }

    public static final Parcelable.Creator<NewLocationResultDto> CREATOR =
            new Parcelable.Creator<NewLocationResultDto>() {

                @Override
                public NewLocationResultDto createFromParcel(Parcel in) {
                    return new NewLocationResultDto(in);
                }

                @Override
                public NewLocationResultDto[] newArray(int size) {
                    return new NewLocationResultDto[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(uploadSuccess ? 1 : 0);
        TextUtils.writeToParcel(uploadMessage, parcel, flags);
        parcel.writeInt(saveSuccess ? 1 : 0);
        TextUtils.writeToParcel(saveMessage, parcel, flags);
        parcel.writeTypedObject(locationDto, flags);
    }
}
