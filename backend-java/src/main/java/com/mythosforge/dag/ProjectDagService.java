package com.mythosforge.dag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.writer.WriterHttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectDagService {

    private static final Logger log = LoggerFactory.getLogger(ProjectDagService.class);
    private static final String DEFAULT_DAG_RESOURCE = "dag/default_chapter_dag.json";

    private final ProjectRepository projectRepository;
    private final ProjectDagVersionRepository dagVersionRepository;
    private final WriterHttpService writerHttpService;
    private final ObjectMapper objectMapper;

    public ProjectDagService(
            ProjectRepository projectRepository,
            ProjectDagVersionRepository dagVersionRepository,
            WriterHttpService writerHttpService,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.dagVersionRepository = dagVersionRepository;
        this.writerHttpService = writerHttpService;
        this.objectMapper = objectMapper;
    }

    public void requireProject(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在");
        }
    }

    /** 生成时注入 Writer：有 active 用 active，否则 Writer 默认 DAG。 */
    public JsonNode getActiveDagForWriter(String projectId) {
        requireProject(projectId);
        return dagVersionRepository.findByProjectIdAndActiveTrue(projectId)
                .map(ProjectDagVersionEntity::getDagJson)
                .orElse(null);
    }

    public ProjectDagVersionEntity getActiveOrDefault(String projectId) {
        requireProject(projectId);
        return dagVersionRepository.findByProjectIdAndActiveTrue(projectId)
                .orElseGet(() -> {
                    ProjectDagVersionEntity seed = new ProjectDagVersionEntity();
                    seed.setDagJson(fetchWriterDefaultDag());
                    seed.setLabel("系统默认");
                    seed.setVersionNo(0);
                    seed.setActive(false);
                    return seed;
                });
    }

    public List<ProjectDagVersionEntity> listVersions(String projectId) {
        requireProject(projectId);
        return dagVersionRepository.findByProjectIdOrderByVersionNoDesc(projectId);
    }

    @Transactional
    public ProjectDagVersionEntity saveActiveDag(String projectId, JsonNode dagJson, String label) {
        requireProject(projectId);
        if (dagJson == null || !dagJson.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dag 必须为 JSON 对象");
        }
        validateWithWriter(dagJson);

        dagVersionRepository.deactivateAll(projectId);
        int next = dagVersionRepository.findMaxVersionNo(projectId) + 1;

        ProjectDagVersionEntity entity = new ProjectDagVersionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setProjectId(projectId);
        entity.setVersionNo(next);
        entity.setLabel(label != null && !label.isBlank() ? label.trim() : "v" + next);
        entity.setDagJson(dagJson.deepCopy());
        entity.setActive(true);
        return dagVersionRepository.save(entity);
    }

    public JsonNode fetchWriterDefaultDag() {
        try {
            return writerHttpService.getJson("/api/writer/dag/default");
        } catch (Exception ex) {
            log.warn("Writer default DAG unavailable, using classpath fallback: {}", ex.getMessage());
            return loadClasspathDefaultDag();
        }
    }

    JsonNode loadClasspathDefaultDag() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(DEFAULT_DAG_RESOURCE)) {
            if (in == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "内置 default_chapter_dag.json 缺失");
            }
            return objectMapper.readTree(in);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取内置 DAG 失败: " + ex.getMessage());
        }
    }

    public JsonNode fetchWriterNodeTypes() {
        return writerHttpService.getJson("/api/writer/dag/node-types");
    }

    public JsonNode writerScaffoldNode(JsonNode body) {
        return writerHttpService.postJson("/api/writer/dag/scaffold-node", body);
    }

    public ArrayNode validateWithWriter(JsonNode dagJson) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("dag", dagJson);
        JsonNode res = writerHttpService.postJson("/api/writer/dag/validate", body);
        ArrayNode errs = objectMapper.createArrayNode();
        if (res != null && res.has("errors") && res.get("errors").isArray()) {
            errs = (ArrayNode) res.get("errors");
        }
        if (!errs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DAG 校验失败: " + errs);
        }
        return errs;
    }

    public ArrayNode validateWithWriterSoft(JsonNode dagJson) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("dag", dagJson);
        JsonNode res = writerHttpService.postJson("/api/writer/dag/validate", body);
        ArrayNode errs = objectMapper.createArrayNode();
        if (res != null && res.has("errors") && res.get("errors").isArray()) {
            return (ArrayNode) res.get("errors");
        }
        return errs;
    }
}
