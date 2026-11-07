package com.labelhub.api.module.agentaccess.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** LH-3 / conventions §4: agent_trajectory_steps is append-only. */
class AgentTrajectoryStepMapperContractTest {

    @Test
    void mapper_has_no_parent_interfaces_so_no_inherited_update_or_delete() {
        assertThat(AgentTrajectoryStepMapper.class.getInterfaces()).isEmpty();
    }

    @Test
    void mapper_exposes_only_insert_and_select_methods() {
        for (Method method : AgentTrajectoryStepMapper.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(name.startsWith("insert") || name.startsWith("select"))
                .as("Method " + name + " must be insert/select only")
                .isTrue();
        }
    }

    @Test
    void token_mapper_only_updates_by_revoking_once() {
        for (Method method : McpAccessTokenMapper.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(name.startsWith("insert") || name.startsWith("select") || name.equals("revoke"))
                .as("Method " + name).isTrue();
        }
    }
}
