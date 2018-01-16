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
public class LocalLocationResultDto implements Parcelable {

    private int ordinal;

    private boolean uploadSuccess;

    private CharSequence uploadMessage;

    private boolean markSuccess;

    private CharSequence markMessage;

    private LocalLocationResultDto(Parcel in) {
        uploadSuccess = in.readInt() != 0;
        uploadMessage = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        markSuccess = in.readInt() != 0;
        markMessage = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
    }

    public static final Creator<LocalLocationResultDto> CREATOR =
            new Creator<LocalLocationResultDto>() {

                @Override
                public LocalLocationResultDto createFromParcel(Parcel in) {
                    return new LocalLocationResultDto(in);
                }

                @Override
                public LocalLocationResultDto[] newArray(int size) {
                    return new LocalLocationResultDto[size];
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
        parcel.writeInt(markSuccess ? 1 : 0);
        TextUtils.writeToParcel(markMessage, parcel, flags);
    }
}
