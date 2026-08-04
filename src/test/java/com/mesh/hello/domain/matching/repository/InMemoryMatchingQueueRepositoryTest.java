package com.mesh.hello.domain.matching.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryMatchingQueueRepositoryTest {

    private MatchingQueueRepository queueRepository;

    @BeforeEach
    void setUp() {
        // 인메모리 구현체를 직접 주입하여 단위 테스트 진행
        this.queueRepository = new InMemoryMatchingQueueRepository();
    }

    @Nested
    @DisplayName("도우미(Helper) 큐 단위 테스트")
    class HelperQueueTest {

        @Test
        @DisplayName("도우미를 큐에 넣으면 대기 카운트가 증가하고, 꺼내면 FIFO 순서대로 나와야 한다")
        void pushAndPopHelperSuccess() {
            // given
            String helper1 = "helper-session-1";
            String helper2 = "helper-session-2";

            // when
            queueRepository.pushHelper(helper1);
            queueRepository.pushHelper(helper2);

            // then
            assertThat(queueRepository.getWaitingHelperCount()).isEqualTo(2);

            // 선입선출(FIFO) 검증
            Optional<String> firstPopped = queueRepository.popWaitingHelper();
            assertThat(firstPopped).isPresent().contains(helper1);
            assertThat(queueRepository.getWaitingHelperCount()).isEqualTo(1);

            Optional<String> secondPopped = queueRepository.popWaitingHelper();
            assertThat(secondPopped).isPresent().contains(helper2);
            assertThat(queueRepository.getWaitingHelperCount()).isEqualTo(0);

            // 완전히 비었을 때 빈 Optional 반환 검증
            assertThat(queueRepository.popWaitingHelper()).isEmpty();
        }

        @Test
        @DisplayName("이미 대기 중인 도우미를 중복 push 하면 큐에 추가되지 않아야 한다")
        void pushDuplicateHelperIgnored() {
            // given
            String helper = "duplicate-helper";

            // when
            queueRepository.pushHelper(helper);
            queueRepository.pushHelper(helper);

            // then
            assertThat(queueRepository.getWaitingHelperCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("도우미가 대기를 취소하거나 이탈하면 큐에서 정상적으로 삭제되어야 한다")
        void removeHelperSuccess() {
            // given
            String helper1 = "helper-1";
            String helper2 = "helper-2";
            queueRepository.pushHelper(helper1);
            queueRepository.pushHelper(helper2);

            // when
            queueRepository.removeHelper(helper1);

            // then
            assertThat(queueRepository.getWaitingHelperCount()).isEqualTo(1);
            assertThat(queueRepository.popWaitingHelper()).isPresent().contains(helper2);
        }

        @Test
        @DisplayName("isHelperWaiting은 큐에 있는 도우미만 true를 반환해야 한다")
        void isHelperWaitingReflectsQueueState() {
            // given
            String waitingHelper = "waiting-helper";
            String strangerHelper = "stranger-helper";
            queueRepository.pushHelper(waitingHelper);

            // then
            assertThat(queueRepository.isHelperWaiting(waitingHelper)).isTrue();
            assertThat(queueRepository.isHelperWaiting(strangerHelper)).isFalse();

            // when
            queueRepository.removeHelper(waitingHelper);

            // then
            assertThat(queueRepository.isHelperWaiting(waitingHelper)).isFalse();
        }
    }

    @Nested
    @DisplayName("어르신(Helpee) 큐 단위 테스트")
    class HelpeeQueueTest {

        @Test
        @DisplayName("어르신을 큐에 넣으면 대기 카운트가 증가하고, 꺼내면 FIFO 순서대로 나와야 한다")
        void pushAndPopHelpeeSuccess() {
            // given
            String helpee1 = "helpee-session-1";
            String helpee2 = "helpee-session-2";

            // when
            queueRepository.pushHelpee(helpee1);
            queueRepository.pushHelpee(helpee2);

            // then
            assertThat(queueRepository.getWaitingHelpeeCount()).isEqualTo(2);

            Optional<String> firstPopped = queueRepository.popWaitingHelpee();
            assertThat(firstPopped).isPresent().contains(helpee1);

            Optional<String> secondPopped = queueRepository.popWaitingHelpee();
            assertThat(secondPopped).isPresent().contains(helpee2);

            assertThat(queueRepository.getWaitingHelpeeCount()).isEqualTo(0);
            assertThat(queueRepository.popWaitingHelpee()).isEmpty();
        }

        @Test
        @DisplayName("이미 대기 중인 어르신을 중복 push 하면 큐에 추가되지 않아야 한다")
        void pushDuplicateHelpeeIgnored() {
            // given
            String helpee = "duplicate-helpee";

            // when
            queueRepository.pushHelpee(helpee);
            queueRepository.pushHelpee(helpee);

            // then
            assertThat(queueRepository.getWaitingHelpeeCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("어르신이 대기를 취소하면 큐에서 정상적으로 삭제되어야 한다")
        void removeHelpeeSuccess() {
            // given
            String helpee1 = "helpee-1";
            String helpee2 = "helpee-2";
            queueRepository.pushHelpee(helpee1);
            queueRepository.pushHelpee(helpee2);

            // when
            queueRepository.removeHelpee(helpee1);

            // then
            assertThat(queueRepository.getWaitingHelpeeCount()).isEqualTo(1);
            assertThat(queueRepository.popWaitingHelpee()).isPresent().contains(helpee2);
        }
    }

    @Nested
    @DisplayName("가상 스레드 환경 대비 동시성(Concurrency) 검증")
    class ConcurrencyTest {

        @Test
        @DisplayName("동시에 100개의 pop 요청이 쏟아져도 동일한 사용자가 중복 매칭되지 않고 정확히 1명씩 안전하게 분리되어야 한다")
        void concurrentPopSafe() throws InterruptedException {
            // given
            int threadCount = 100;
            for (int i = 0; i < threadCount; i++) {
                queueRepository.pushHelper("helper-" + i);
            }

            // 대량의 멀티스레드 동시 요청 환경 세팅
            ExecutorService executorService = Executors.newFixedThreadPool(32);
            CountDownLatch latch = new CountDownLatch(threadCount);
            java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

            // when
            for (int i = 0; i < threadCount; i++) {
                executorService.execute(() -> {
                    try {
                        Optional<String> helper = queueRepository.popWaitingHelper();
                        if (helper.isPresent()) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(queueRepository.getWaitingHelperCount()).isEqualTo(0);
        }
    }
}