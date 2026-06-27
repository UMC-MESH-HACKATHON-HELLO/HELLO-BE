package com.mesh.hello.global.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * 모든 응답을 감싸는 공통 래퍼.
 *
 * <p>형식: {@code { "code": 200, "message": "...", "result": { ... } }}</p>
 *
 * <ul>
 *   <li>{@code code}    : 비즈니스/상태 코드. 이벤트별 고유 코드가 있으면 그 값, 없으면 200.</li>
 *   <li>{@code message} : 사람이 읽는 설명 문구.</li>
 *   <li>{@code result}  : 각 API가 실제로 반환하는 데이터(DTO). 에러 시 null.</li>
 * </ul>
 *
 * <p>기존 데이터 구조는 그대로 {@code result}에 담고 바깥만 감싼다.</p>
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private final int code;
    private final String message;
    private final T result;

    private ApiResponse(int code, String message, T result) {
        this.code = code;
        this.message = message;
        this.result = result;
    }

    /** 성공 - 기본 코드 200. */
    public static <T> ApiResponse<T> ok(String message, T result) {
        return new ApiResponse<>(200, message, result);
    }

    /** 성공/상태 - 이벤트별 고유 코드 지정. */
    public static <T> ApiResponse<T> of(int code, String message, T result) {
        return new ApiResponse<>(code, message, result);
    }

    /** 에러 - result는 항상 null. */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
