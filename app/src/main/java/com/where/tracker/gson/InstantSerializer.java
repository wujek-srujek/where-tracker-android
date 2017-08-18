package com.where.tracker.gson;


import java.lang.reflect.Type;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.where.tracker.helper.InstantSerializationHelper;
import org.threeten.bp.Instant;


public class InstantSerializer implements JsonSerializer<Instant> {

    @Override
    public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(InstantSerializationHelper.toString(src));
    }
}
