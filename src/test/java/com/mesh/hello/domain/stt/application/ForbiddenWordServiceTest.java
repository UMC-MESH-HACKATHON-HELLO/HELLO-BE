package com.mesh.hello.domain.stt.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ForbiddenWordServiceTest {

    private static ForbiddenWordService withWords(String... words) {
        String content = String.join("\n", words);
        return new ForbiddenWordService(new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Nested
    @DisplayName("findHit - 금지어 매칭")
    class FindHitTest {

        @Test
        @DisplayName("문장에 금지어가 그대로 포함되면 해당 단어를 반환한다")
        void returnsMatchedWordWhenPresent() {
            ForbiddenWordService service = withWords("바보", "멍청이");

            Optional<String> hit = service.findHit("너 정말 바보 같다");

            assertThat(hit).contains("바보");
        }

        @Test
        @DisplayName("금지어가 문장 일부(부분 문자열)로만 포함돼도 히트한다")
        void matchesAsSubstring() {
            ForbiddenWordService service = withWords("바보");

            Optional<String> hit = service.findHit("바보같은소리하지마");

            assertThat(hit).contains("바보");
        }

        @Test
        @DisplayName("금지어가 없으면 empty를 반환한다")
        void returnsEmptyWhenNoMatch() {
            ForbiddenWordService service = withWords("바보", "멍청이");

            Optional<String> hit = service.findHit("오늘 날씨가 참 좋네요");

            assertThat(hit).isEmpty();
        }

        @Test
        @DisplayName("빈 문자열은 히트하지 않는다")
        void emptyTextNeverMatches() {
            ForbiddenWordService service = withWords("바보");

            assertThat(service.findHit("")).isEmpty();
        }
    }

    @Nested
    @DisplayName("리스트 파일 파싱")
    class LoadWordsTest {

        @Test
        @DisplayName("빈 줄과 # 주석 줄은 금지어로 취급하지 않는다")
        void ignoresBlankLinesAndComments() {
            String content = "바보\n\n# 이건 주석입니다\n멍청이\n   \n";
            ForbiddenWordService service = new ForbiddenWordService(
                    new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));

            assertThat(service.findHit("주석입니다")).isEmpty();
            assertThat(service.findHit("바보야")).contains("바보");
            assertThat(service.findHit("멍청이야")).contains("멍청이");
        }

        @Test
        @DisplayName("각 줄 앞뒤 공백은 제거한 뒤 단어로 취급한다")
        void stripsWhitespaceAroundWords() {
            String content = "  바보  \n\t멍청이\t\n";
            ForbiddenWordService service = new ForbiddenWordService(
                    new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));

            assertThat(service.findHit("바보 취급하지마")).contains("바보");
        }

        @Test
        @DisplayName("실제 policy/forbidden-words.txt 리소스를 정상적으로 로드한다")
        void loadsActualResourceFile() {
            ForbiddenWordService service = new ForbiddenWordService(
                    new ClassPathResource("policy/forbidden-words.txt"));

            assertThat(service.findHit("아 씨발 진짜")).isPresent();
            assertThat(service.findHit("오늘 점심 뭐 먹지")).isEmpty();
        }

        /**
         * 단음절/어간 단위 금지어("씹", "꺼져", "시끄러" 등)는 "씹어 드세요", "화면이 꺼져 있어요",
         * "여기가 시끄러워요"처럼 무해한 단어에도 부분 문자열로 히트해 정상 통화를 오탐 종료시킬 수 있다.
         * 실제 정책 파일을 대상으로 일상적인 문장(특히 스마트폰/키오스크 도움 도메인)이 오탐하지
         * 않는지 회귀 검증한다.
         */
        @Test
        @DisplayName("일상적인 문장(음식/기기 상태/소음 맥락)은 금지어로 오탐하지 않는다")
        void doesNotFalselyMatchEverydaySentences() {
            ForbiddenWordService service = new ForbiddenWordService(
                    new ClassPathResource("policy/forbidden-words.txt"));

            assertThat(service.findHit("음식을 천천히 씹어 드세요")).isEmpty();
            assertThat(service.findHit("이가 안 좋아서 잘 씹지를 못해요")).isEmpty();
            assertThat(service.findHit("화면이 꺼져 있어요")).isEmpty();
            assertThat(service.findHit("배터리가 다 돼서 꺼졌어요")).isEmpty();
            assertThat(service.findHit("여기가 좀 시끄러워요")).isEmpty();
        }
    }
}
