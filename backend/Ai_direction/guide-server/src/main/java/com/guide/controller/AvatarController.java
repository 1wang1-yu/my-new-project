package com.guide.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guide.annotation.LogOperation;
import com.guide.config.AvatarWebSocketHandler;
import com.guide.pojo.dto.ApiResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
@Api(tags = "数字人形象管理")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AvatarController {

    private static final String CONFIG_PATH = System.getProperty("user.dir") + "/avatar-config.json";

    private final ObjectMapper objectMapper;
    private final AvatarWebSocketHandler avatarWebSocketHandler;

    @ApiOperation("上传 VRM / GLB 模型文件")
    @PostMapping("/avatars/upload")
    @LogOperation("avatar_upload")
    public ApiResponse<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.fail("文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || (!originalName.toLowerCase().endsWith(".vrm") && !originalName.toLowerCase().endsWith(".glb"))) {
            return ApiResponse.fail("仅支持 .vrm 和 .glb 文件");
        }
        try {
            // 保存到 static 目录下
            String staticDir = System.getProperty("user.dir") + "/guide-server/src/main/resources/static/";
            Path dest = new File(staticDir + originalName).toPath();
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("形象文件上传成功: {}", originalName);
            return ApiResponse.ok(Map.of(
                    "filename", originalName,
                    "size", file.getSize()
            ));
        } catch (IOException e) {
            log.error("上传形象文件失败", e);
            return ApiResponse.fail("上传失败: " + e.getMessage());
        }
    }

    @ApiOperation("获取可用形象列表")
    @GetMapping("/avatars")
    @LogOperation("avatar_list")
    public ApiResponse<Map<String, Object>> listAvatars() {
        try {
            List<Map<String, Object>> files = new ArrayList<>();
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(
                    "classpath:/static/*.vrm");
            for (Resource r : resources) {
                String filename = r.getFilename();
                if (filename == null) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("filename", filename);
                m.put("url", "/avatars/" + filename);
                m.put("size", r.contentLength());
                m.put("type", "vrm");
                files.add(m);
            }
            resources = resolver.getResources("classpath:/static/*.glb");
            for (Resource r : resources) {
                String filename = r.getFilename();
                if (filename == null) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("filename", filename);
                m.put("url", "/avatars/" + filename);
                m.put("size", r.contentLength());
                m.put("type", "glb");
                files.add(m);
            }

            Map<String, Object> config = loadConfig();
            return ApiResponse.ok(Map.of(
                    "avatars", files,
                    "active", config.getOrDefault("active", "")
            ));
        } catch (Exception e) {
            log.error("获取形象列表失败", e);
            return ApiResponse.fail("获取形象列表失败: " + e.getMessage());
        }
    }

    @ApiOperation("获取当前激活的形象文件名（小程序端调用）")
    @GetMapping("/avatars/active")
    public ApiResponse<Map<String, String>> getActiveAvatar() {
        Map<String, Object> config = loadConfig();
        String active = (String) config.getOrDefault("active", "");
        return ApiResponse.ok(Map.of("active", active));
    }

    @SuppressWarnings("unchecked")
    @ApiOperation("获取形象部件选择器配置（男女分步，规则由 .vrm 自动生成）")
    @GetMapping("/avatars/part-selector")
    public ApiResponse<Map<String, Object>> getPartSelector() {
        Map<String, Object> config = loadConfig();
        String activeVrm = (String) config.getOrDefault("active", "");

        // 收集所有 .vrm 文件
        Set<String> vrmFiles = new HashSet<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            for (Resource r : resolver.getResources("classpath:/static/*.vrm")) {
                String fn = r.getFilename();
                if (fn != null && fn.endsWith(".vrm")) vrmFiles.add(fn);
            }
        } catch (Exception ignored) {}

        // 读取 genders 配置
        List<Map<String, Object>> genders = (List<Map<String, Object>>) config.getOrDefault("genders", List.of());

        // 为每个 gender 生成 rules
        Map<String, List<Map<String, Object>>> genderRules = new LinkedHashMap<>();
        for (Map<String, Object> gender : genders) {
            String gid = (String) gender.get("id");
            List<Map<String, Object>> cats = (List<Map<String, Object>>) gender.getOrDefault("categories", List.of());
            List<Map<String, Object>> rules = new ArrayList<>();
            if (cats.size() >= 3) {
                List<Map<String, Object>> faceOpts = (List<Map<String, Object>>) cats.get(0).getOrDefault("options", List.of());
                List<Map<String, Object>> hairOpts = (List<Map<String, Object>>) cats.get(1).getOrDefault("options", List.of());
                List<Map<String, Object>> outfitOpts = (List<Map<String, Object>>) cats.get(2).getOrDefault("options", List.of());
                for (Map<String, Object> fo : faceOpts) {
                    String fid = (String) fo.get("id");
                    for (Map<String, Object> ho : hairOpts) {
                        String hid = (String) ho.get("id");
                        for (Map<String, Object> oo : outfitOpts) {
                            String oid = (String) oo.get("id");
                            String vrmName = fid + hid + oid + ".vrm";
                            if (vrmFiles.contains(vrmName)) {
                                Map<String, Object> rule = new LinkedHashMap<>();
                                rule.put("parts", List.of(fid, hid, oid));
                                rule.put("vrmFile", vrmName);
                                rule.put("name", "造型 " + vrmName.replace(".vrm", ""));
                                rules.add(rule);
                            }
                        }
                    }
                }
            }
            genderRules.put(gid, rules);
        }

        // 当前激活的组合
        Map<String, Object> activeRule = null;
        for (List<Map<String, Object>> rules : genderRules.values()) {
            for (Map<String, Object> rule : rules) {
                if (rule.get("vrmFile") != null && rule.get("vrmFile").equals(activeVrm)) {
                    activeRule = rule;
                    break;
                }
            }
            if (activeRule != null) break;
        }

        return ApiResponse.ok(Map.of(
            "genders", genders,
            "genderRules", genderRules,
            "activeVrm", activeVrm,
            "activeRule", activeRule != null ? activeRule : Map.of()
        ));
    }

    @ApiOperation("设置当前使用的形象（支持组合 ID，comboId 就是 .vrm 文件名去掉后缀）")
    @PutMapping("/avatars/active")
    @LogOperation("avatar_set_active")
    public ApiResponse<String> setActiveAvatar(@RequestBody Map<String, String> body) {
        String comboId = body.get("comboId");
        String filename = body.get("filename");
        if ((comboId == null || comboId.isBlank()) && (filename == null || filename.isBlank())) {
            return ApiResponse.fail("comboId 或 filename 不能为空");
        }
        try {
            if (comboId != null && !comboId.isBlank()) {
                filename = comboId + ".vrm";
            }
            Map<String, Object> config = loadConfig();
            config.put("active", filename);
            config.put("active_combo", comboId != null ? comboId : "");
            saveConfig(config);
            // 通过 WebSocket 实时通知所有客户端（小程序）
            avatarWebSocketHandler.broadcastAvatarChange(filename);
            log.info("已切换数字人形象: {} (combo: {})", filename, comboId);
            return ApiResponse.ok("已切换形象为: " + filename);
        } catch (Exception e) {
            log.error("切换形象失败", e);
            return ApiResponse.fail("切换失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadConfig() {
        try {
            File file = new File(CONFIG_PATH);
            if (file.exists()) {
                return objectMapper.readValue(file, Map.class);
            }
        } catch (Exception e) {
            log.warn("读取形象配置文件失败，使用默认配置", e);
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("active", "");
        return defaults;
    }

    private void saveConfig(Map<String, Object> config) {
        try {
            objectMapper.writeValue(new File(CONFIG_PATH), config);
        } catch (Exception e) {
            log.error("保存形象配置文件失败", e);
        }
    }
}
