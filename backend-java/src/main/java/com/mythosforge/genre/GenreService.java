package com.mythosforge.genre;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.genre.dto.GenreRecommendRequest;
import com.mythosforge.genre.dto.GenreRecommendResponse;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.writer.WriterEngineClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class GenreService {

    private final ProjectRepository projectRepository;
    private final GenreDecisionContractRepository genreDecisionContractRepository;
    private final WriterEngineClient writerEngineClient;
    private final ObjectMapper objectMapper;

    public GenreService(
            ProjectRepository projectRepository,
            GenreDecisionContractRepository genreDecisionContractRepository,
            WriterEngineClient writerEngineClient,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.genreDecisionContractRepository = genreDecisionContractRepository;
        this.writerEngineClient = writerEngineClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GenreRecommendResponse recommend(String projectId, GenreRecommendRequest req) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        ObjectNode body = objectMapper.valueToTree(req);
        body.put("projectId", projectId);

        JsonNode contract;
        try {
            contract = writerEngineClient.postGenreRecommend(body);
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "writer-python genre/recommend HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    e
            );
        }

        JsonNode selected = pick(contract, "selectedDirection", "selected_direction");
        JsonNode rankings = pick(contract, "candidateRankings", "candidate_rankings");
        JsonNode risks = optionalArray(contract, "riskNotes", "risk_notes");

        if (selected == null || selected.isNull() || selected.isMissingNode()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Writer contract missing selectedDirection / selected_direction"
            );
        }
        if (rankings == null || rankings.isNull() || rankings.isMissingNode() || !rankings.isArray()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Writer contract missing candidateRankings / candidate_rankings array"
            );
        }

        GenreDecisionContract row = new GenreDecisionContract();
        row.setId(UUID.randomUUID().toString().replace("-", ""));
        row.setProjectId(projectId);
        row.setSelectedDirection(selected);
        row.setCandidateRankings(rankings);
        row.setRiskNotes(risks);
        row.setRawJson(contract);

        genreDecisionContractRepository.save(row);

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.set("selectedDirection", selected);
        normalized.set("candidateRankings", rankings);
        normalized.set("riskNotes", risks);
        normalized.set(
                "recommendedCoreHook",
                pickText(contract, "recommendedCoreHook", "recommended_core_hook")
        );
        return new GenreRecommendResponse(row.getId(), normalized);
    }

    private JsonNode pick(JsonNode root, String camel, String snake) {
        if (root != null && root.has(camel)) {
            JsonNode n = root.get(camel);
            if (!n.isNull()) {
                return n;
            }
        }
        if (root != null && root.has(snake)) {
            JsonNode n = root.get(snake);
            if (!n.isNull()) {
                return n;
            }
        }
        return null;
    }

    private JsonNode optionalArray(JsonNode root, String camel, String snake) {
        JsonNode n = pick(root, camel, snake);
        if (n == null || n.isMissingNode() || n.isNull()) {
            return objectMapper.createArrayNode();
        }
        return n;
    }

    private JsonNode pickText(JsonNode root, String camel, String snake) {
        JsonNode n = pick(root, camel, snake);
        if (n == null || n.isMissingNode() || n.isNull()) {
            return objectMapper.nullNode();
        }
        return n;
    }
}
