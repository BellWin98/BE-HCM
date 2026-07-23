package com.behcm.global.config.swagger;

import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Component
public class MultipartJacksonHttpMessageConverter extends JacksonJsonHttpMessageConverter {

    public MultipartJacksonHttpMessageConverter(JsonMapper jsonMapper) {
        super(jsonMapper);
        setSupportedMediaTypes(List.of(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return false;
    }

    @Override
    public boolean canWrite(ResolvableType targetType, Class<?> valueClass, MediaType mediaType) {
        return false;
    }

    @Override
    protected boolean canWrite(MediaType mediaType) {
        return false;
    }
}
