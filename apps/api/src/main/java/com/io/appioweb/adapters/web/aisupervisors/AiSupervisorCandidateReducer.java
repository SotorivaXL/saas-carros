package com.io.appioweb.adapters.web.aisupervisors;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class AiSupervisorCandidateReducer {

    public List<CandidateRef> reduce(
            List<CandidateRef> candidates,
            String leadText,
            int reductionThreshold,
            int maxCandidates
    ) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (candidates.size() <= reductionThreshold) return List.copyOf(candidates);

        String normalizedLead = AiSupervisorSupport.normalize(leadText);
        List<String> leadTokens = AiSupervisorSupport.tokenize(leadText);
        List<ScoredCandidate> scored = new ArrayList<>();
        for (CandidateRef candidate : candidates) {
            String searchable = AiSupervisorSupport.normalize(candidate.agentName() + " " + candidate.triageText());
            double score = 0.0d;
            if (!normalizedLead.isBlank() && searchable.contains(normalizedLead)) {
                score += 1.25d;
            }
            int tokenHits = 0;
            for (String token : leadTokens) {
                if (searchable.contains(token)) tokenHits++;
            }
            if (!leadTokens.isEmpty()) {
                score += ((double) tokenHits / (double) leadTokens.size());
            }
            scored.add(new ScoredCandidate(candidate, score));
        }

        List<ScoredCandidate> ordered = scored.stream()
                .sorted(Comparator
                        .comparingDouble(ScoredCandidate::score).reversed()
                        .thenComparing(item -> item.candidate().agentId()))
                .toList();
        if (!ordered.isEmpty() && ordered.get(0).score() > 0.0d) {
            return ordered.stream()
                    .map(ScoredCandidate::candidate)
                    .limit(Math.max(1, maxCandidates))
                    .toList();
        }

        return candidates.stream()
                .sorted(Comparator.comparing(CandidateRef::agentId))
                .limit(Math.max(1, maxCandidates))
                .toList();
    }

    public record CandidateRef(
            String agentId,
            String agentName,
            String triageText
    ) {
    }

    private record ScoredCandidate(CandidateRef candidate, double score) {
    }
}
