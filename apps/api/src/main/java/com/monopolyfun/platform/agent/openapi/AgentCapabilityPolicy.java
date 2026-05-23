package com.monopolyfun.platform.agent.openapi;

import org.casbin.jcasbin.main.Enforcer;
import org.casbin.jcasbin.model.Model;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class AgentCapabilityPolicy {
    private static final String CONTEXT_SUBJECT = "context";
    private static final String ACTION = "use";
    private static final String CASBIN_MODEL = """
            [request_definition]
            r = sub, obj, act
            
            [policy_definition]
            p = sub, obj, act
            
            [policy_effect]
            e = some(where (p.eft == allow))
            
            [matchers]
            m = r.sub == p.sub && r.obj == p.obj && r.act == p.act
            """;

    public List<AgentCapabilityDecision> decide(List<AgentCapabilityRule> rules, Set<String> facts) {
        Enforcer enforcer = enforcerFor(facts);
        List<AgentCapabilityDecision> decisions = new ArrayList<>();
        for (AgentCapabilityRule rule : rules) {
            FactRequirement missing = rule.requirements().stream()
                    .filter(requirement -> !enforcer.enforce(CONTEXT_SUBJECT, requirement.fact(), ACTION))
                    .findFirst()
                    .orElse(null);
            decisions.add(missing == null
                    ? AgentCapabilityDecision.allowed(rule.capability())
                    : AgentCapabilityDecision.blocked(rule.capability(), missing.blockedReason()));
        }
        return List.copyOf(decisions);
    }

    public List<String> allowedCapabilities(List<AgentCapabilityDecision> decisions) {
        return decisions.stream()
                .filter(AgentCapabilityDecision::allowed)
                .map(AgentCapabilityDecision::capability)
                .toList();
    }

    public List<java.util.Map<String, Object>> blockedCapabilities(List<AgentCapabilityDecision> decisions) {
        return decisions.stream()
                .filter(decision -> !decision.allowed())
                .map(AgentCapabilityDecision::blockedCapability)
                .toList();
    }

    private Enforcer enforcerFor(Set<String> facts) {
        Model model = Model.newModelFromString(CASBIN_MODEL);
        Enforcer enforcer = new Enforcer(model);
        enforcer.enableAutoSave(false);
        enforcer.enableLog(false);
        // 中文注释：agent resource 的动态事实先装入 Casbin，上层只读 allow/blocked 决策，后续替换为外部 policy 文件无需改响应结构。
        for (String fact : facts == null ? Set.<String>of() : facts) {
            enforcer.addPolicy(CONTEXT_SUBJECT, fact, ACTION);
        }
        return enforcer;
    }

    public record AgentCapabilityRule(
            String capability,
            List<FactRequirement> requirements
    ) {
        public AgentCapabilityRule {
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
        }
    }

    public record FactRequirement(
            String fact,
            String blockedReason
    ) {
    }
}
