package com.monopolyfun.modules.agent.api;

import com.monopolyfun.modules.agent.api.request.AgentTurnRequest;
import com.monopolyfun.modules.agent.service.AgentTurnService;
import com.monopolyfun.modules.agent.service.view.AgentTurnResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentTurnController {
    private final AgentTurnService agentTurnService;

    public AgentTurnController(AgentTurnService agentTurnService) {
        this.agentTurnService = agentTurnService;
    }

    @PostMapping("/turn")
    @Operation(operationId = "turn")
    public AgentTurnResult turn(@Valid @RequestBody AgentTurnRequest request) {
        return agentTurnService.turn(request);
    }
}
