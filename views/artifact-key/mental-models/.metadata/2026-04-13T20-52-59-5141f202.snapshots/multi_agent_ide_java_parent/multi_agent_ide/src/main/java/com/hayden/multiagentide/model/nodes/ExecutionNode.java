package com.hayden.multiagentide.model.nodes;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface ExecutionNode {

    @JsonIgnore
    String agent();

}
