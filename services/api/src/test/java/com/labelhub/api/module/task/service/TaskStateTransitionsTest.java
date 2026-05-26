package com.labelhub.api.module.task.service;

import com.labelhub.api.generated.model.TaskStatus;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class TaskStateTransitionsTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @MethodSource("legalTransitions")
    void allowed_transition_matrix_matches_baseline(TaskStatus from, TaskStatus to) {
        assertThat(TaskStateTransitions.isAllowed(from, to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @MethodSource("illegalTransitions")
    void illegal_transition_matrix_rejects_every_non_baseline_edge(TaskStatus from, TaskStatus to) {
        assertThat(TaskStateTransitions.isAllowed(from, to)).isFalse();
    }

    @Test
    void ended_is_terminal() {
        for (TaskStatus target : TaskStatus.values()) {
            assertThat(TaskStateTransitions.isAllowed(TaskStatus.ENDED, target)).isFalse();
        }
    }

    static Stream<Arguments> legalTransitions() {
        return Stream.of(
            Arguments.of(TaskStatus.DRAFT, TaskStatus.PUBLISHED),
            Arguments.of(TaskStatus.PUBLISHED, TaskStatus.PAUSED),
            Arguments.of(TaskStatus.PUBLISHED, TaskStatus.ENDED),
            Arguments.of(TaskStatus.PAUSED, TaskStatus.PUBLISHED),
            Arguments.of(TaskStatus.PAUSED, TaskStatus.ENDED)
        );
    }

    static Stream<Arguments> illegalTransitions() {
        Set<String> legal = Set.of(
            "DRAFT:PUBLISHED",
            "PUBLISHED:PAUSED",
            "PUBLISHED:ENDED",
            "PAUSED:PUBLISHED",
            "PAUSED:ENDED"
        );
        return Stream.of(TaskStatus.values())
            .flatMap(from -> Stream.of(TaskStatus.values())
                .filter(to -> !legal.contains(from.name() + ":" + to.name()))
                .map(to -> Arguments.of(from, to)));
    }
}
