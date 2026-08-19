package com.mesh.hello.domain.stt.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 금지어 리스트(classpath:policy/forbidden-words.txt)를 기동 시 한 번 읽어 보관하고,
 * 문장에 금지어가 포함돼 있는지 단순 포함 매칭으로 검사한다.
 *
 * <p>문맥은 고려하지 않는 순수 단어 매칭이므로 오탐(예: "발냄새")이 있을 수 있음을 감안한다.</p>
 */
@Slf4j
@Service
public class ForbiddenWordService {

    private final Set<String> forbiddenWords;

    public ForbiddenWordService(@Value("classpath:policy/forbidden-words.txt") Resource resource) {
        this.forbiddenWords = loadWords(resource);
        log.info("금지어 리스트 로드 완료: {}개", forbiddenWords.size());
    }

    private Set<String> loadWords(Resource resource) {
        Set<String> words = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.strip();
                if (!word.isEmpty() && !word.startsWith("#")) {
                    words.add(word);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("금지어 리스트를 불러오지 못했습니다.", e);
        }
        return words;
    }

    /** 텍스트에 포함된 첫 번째 금지어를 반환한다. 없으면 empty. */
    public Optional<String> findHit(String text) {
        return forbiddenWords.stream().filter(text::contains).findFirst();
    }
}
