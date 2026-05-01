package io.sketch.mochaagents.hub;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hugging Face Hub REST API 客户端，封装仓库管理、文件上传下载等 HTTP 细节。
 *
 * <p>不与 Agent 类型耦合，仅处理 file-level 操作。
 * Agent 层负责 {@code save}/ {@code fromFolder} 的序列化/反序列化编排。</p>
 */
public final class HubClient {

    private static final String HF_API_BASE = "https://huggingface.co/api";
    private static final String HF_RAW_BASE = "https://huggingface.co";
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final String token;

    public HubClient(String token) {
        this.token = token;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(60))
            .build();
        this.mapper = new ObjectMapper();
    }

    // ── repository ──────────────────────────────────────────────

    /** 确保仓库存在，不存在则创建（type=space）。 */
    public void ensureRepo(String repoId) throws IOException {
        if (repoExists(repoId)) {
            return;
        }
        createRepo(repoId);
    }

    private boolean repoExists(String repoId) throws IOException {
        Request req = new Request.Builder()
            .url(HF_API_BASE + "/repos/" + repoId)
            .header("Authorization", "Bearer " + token)
            .head()
            .build();
        try (Response resp = client.newCall(req).execute()) {
            return resp.code() == 200;
        }
    }

    private void createRepo(String repoId) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", extractRepoName(repoId));
        body.put("type", "space");
        body.put("private", false);
        int slash = repoId.indexOf('/');
        if (slash >= 0) {
            body.put("namespace", repoId.substring(0, slash));
        }

        RequestBody reqBody = RequestBody.create(mapper.writeValueAsString(body), JSON);
        Request req = new Request.Builder()
            .url(HF_API_BASE + "/repos/create")
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .post(reqBody)
            .build();
        try (Response resp = client.newCall(req).execute()) {
            if (resp.code() != 200 && resp.code() != 201) {
                String err = resp.body() != null ? resp.body().string() : "";
                throw new IOException("Failed to create HF repo: HTTP " + resp.code() + " - " + err);
            }
        }
    }

    // ── upload ──────────────────────────────────────────────────

    /** 将本地目录的所有文件上传到指定仓库分支（递归）。 */
    public void uploadDirectory(String repoId, String revision, Path dir) throws IOException {
        List<Path> files = Files.walk(dir)
            .filter(Files::isRegularFile)
            .toList();
        for (Path file : files) {
            String relativePath = dir.relativize(file).toString().replace('\\', '/');
            uploadFile(repoId, revision, relativePath, Files.readAllBytes(file));
        }
    }

    private void uploadFile(String repoId, String revision, String path, byte[] content) throws IOException {
        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20");
        String url = HF_API_BASE + "/repos/" + repoId + "/upload/" + revision + "/" + encodedPath;

        Request req = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + token)
            .put(RequestBody.create(content, OCTET_STREAM))
            .build();
        try (Response resp = client.newCall(req).execute()) {
            if (resp.code() != 200 && resp.code() != 201 && resp.code() != 204) {
                String err = resp.body() != null ? resp.body().string() : "";
                throw new IOException("Failed to upload '" + path + "': HTTP " + resp.code() + " - " + err);
            }
        }
    }

    // ── download ────────────────────────────────────────────────

    /** 列出仓库中的所有文件（排除隐藏文件）。 */
    public List<String> listFiles(String repoId) throws IOException {
        Request req = new Request.Builder()
            .url(HF_API_BASE + "/repos/" + repoId)
            .header("Authorization", "Bearer " + token)
            .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Failed to list repo files: HTTP " + resp.code());
            }
            String json = resp.body() != null ? resp.body().string() : "{}";
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> siblings = (List<Map<String, Object>>) data.get("siblings");
            if (siblings == null) return List.of();
            List<String> result = new ArrayList<>();
            for (Map<String, Object> sib : siblings) {
                String rfilename = (String) sib.get("rfilename");
                if (rfilename != null && !rfilename.startsWith(".")) {
                    result.add(rfilename);
                }
            }
            return result;
        }
    }

    /** 下载单个文件内容；失败返回 null。 */
    public byte[] downloadFile(String repoId, String revision, String path) throws IOException {
        String encodedPath = path.replace(" ", "%20");
        String url = HF_RAW_BASE + "/" + repoId + "/raw/" + revision + "/" + encodedPath;
        Request req = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + token)
            .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                return null;
            }
            return resp.body() != null ? resp.body().bytes() : null;
        }
    }

    /** 下载仓库全部文件到本地目录（保留相对路径结构）。 */
    public void downloadAll(String repoId, String revision, Path destDir) throws IOException {
        for (String filePath : listFiles(repoId)) {
            byte[] content = downloadFile(repoId, revision, filePath);
            if (content != null) {
                Path dest = destDir.resolve(filePath);
                Files.createDirectories(dest.getParent());
                Files.write(dest, content);
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────

    static String extractRepoName(String repoId) {
        int slash = repoId.indexOf('/');
        return slash >= 0 ? repoId.substring(slash + 1) : repoId;
    }
}
