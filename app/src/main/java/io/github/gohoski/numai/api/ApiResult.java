package io.github.gohoski.numai.api;

import java.io.InputStream;

public class ApiResult {
    private final String model;
    private final InputStream result;

    public ApiResult(String model, InputStream result) {
        this.model = model;
        this.result = result;
    }

    public String getModel() {
        return model;
    }

    public InputStream getResult() {
        return result;
    }
}
