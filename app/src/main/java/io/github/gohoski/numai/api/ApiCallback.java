package io.github.gohoski.numai.api;

public interface ApiCallback<T> {
    void onSuccess(T result);
    void onError(ApiError error);
}
