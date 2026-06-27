package com.mesh.hello.domain.auth.dto;

/**
 * 회원가입 요청(최소 버전). nickname은 선택.
 */
public record SignupRequest(String username, String password, String nickname) {
}
