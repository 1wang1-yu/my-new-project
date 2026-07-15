package com.guide.controller;

import com.guide.entity.User;
import com.guide.mapper.GuideUserMapper;
import com.guide.pojo.dto.ApiResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Api(tags = "用户管理")
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final GuideUserMapper guideUserMapper;
    private final JdbcTemplate jdbcTemplate;

    @ApiOperation("注册")
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();
        String nickName = body.getOrDefault("nickName", username);
        Integer age = body.containsKey("age") ? Integer.parseInt(body.getOrDefault("age", "0")) : null;
        String gender = body.getOrDefault("gender", "");

        if (username.isEmpty() || password.isEmpty()) {
            return ApiResponse.fail("用户名和密码不能为空");
        }
        if (guideUserMapper.findByUsername(username).isPresent()) {
            return ApiResponse.fail("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setOpenId("");  // 账号注册不需要 openId
        user.setNickName(nickName);
        user.setAge(age != null && age > 0 ? age : null);
        user.setGender("male".equals(gender) || "female".equals(gender) ? gender : "");
        user.setVisitCount(1);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        guideUserMapper.save(user);

        return ApiResponse.ok(Map.of(
                "user_id", user.getId(),
                "username", user.getUsername(),
                "nick_name", user.getNickName(),
                "age", user.getAge(),
                "gender", user.getGender()
        ));
    }

    @ApiOperation("登录")
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();

        if (username.isEmpty() || password.isEmpty()) {
            return ApiResponse.fail("用户名和密码不能为空");
        }

        User user = guideUserMapper.findByUsername(username).orElse(null);
        if (user == null) {
            return ApiResponse.fail("用户不存在");
        }
        if (!password.equals(user.getPassword())) {
            return ApiResponse.fail("密码错误");
        }

        user.setVisitCount(user.getVisitCount() == null ? 1 : user.getVisitCount() + 1);
        user.setUpdateTime(LocalDateTime.now());
        guideUserMapper.save(user);

        return ApiResponse.ok(Map.of(
                "user_id", user.getId(),
                "username", user.getUsername(),
                "nick_name", user.getNickName(),
                "age", user.getAge(),
                "gender", user.getGender()
        ));
    }

    @ApiOperation("获取用户信息")
    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> getUserInfo(@RequestParam Long userId) {
        User user = guideUserMapper.findById(userId).orElse(null);
        if (user == null) {
            return ApiResponse.fail("用户不存在");
        }
        return ApiResponse.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "nick_name", user.getNickName(),
                "age", user.getAge(),
                "gender", user.getGender()
        ));
    }

    @ApiOperation("更新用户信息（年龄、性别、地区、昵称、头像）")
    @PostMapping("/update")
    public ApiResponse<Map<String, Object>> updateUser(@RequestBody Map<String, Object> body) {
        try {
            Long userId = body.get("user_id") != null ? Long.valueOf(body.get("user_id").toString()) : null;
            if (userId == null) return ApiResponse.fail("user_id 不能为空");
            java.util.Optional<User> opt = guideUserMapper.findById(userId);
            if (opt.isEmpty()) return ApiResponse.fail("用户不存在");
            User user = opt.get();
            if (body.containsKey("age")) { try { user.setAge(Integer.parseInt(body.get("age").toString())); } catch (Exception e) {} }
            if (body.containsKey("gender")) user.setGender(body.get("gender").toString());
            if (body.containsKey("nick_name")) user.setNickName(body.get("nick_name").toString());
            if (body.containsKey("avatar_url")) user.setAvatarUrl(body.get("avatar_url").toString());
            user.setUpdateTime(java.time.LocalDateTime.now());
            guideUserMapper.save(user);
            return ApiResponse.ok(Map.of(
                    "user_id", user.getId(),
                    "age", user.getAge() != null ? user.getAge() : 0,
                    "gender", user.getGender() != null ? user.getGender() : "",
                    "nick_name", user.getNickName() != null ? user.getNickName() : "",
                    "avatar_url", user.getAvatarUrl() != null ? user.getAvatarUrl() : ""
            ));
        } catch (Exception e) {
            log.error("更新用户失败", e);
            return ApiResponse.fail("更新失败: " + e.getMessage());
        }
    }

    // ====== 管理后台接口 ======

    @Transactional(readOnly = true)
    @ApiOperation("后台-用户列表（支持模糊搜索+排序，限制50条）")
    @GetMapping("/admin/list")
    public ApiResponse<List<Map<String, Object>>> adminUserList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder) {
        StringBuilder sql = new StringBuilder(
            "SELECT TOP 50 id, username, nick_name, age, gender, visit_count, create_time FROM t_user_info WHERE 1=1");
        if (keyword != null && !keyword.isBlank()) {
            String safe = keyword.replace("'", "''");
            sql.append(" AND (CAST(id AS VARCHAR) LIKE '%" + safe + "%'")
               .append(" OR username LIKE N'%" + safe + "%'")
               .append(" OR nick_name LIKE N'%" + safe + "%')");
        }
        String validSort = switch (sortBy) {
            case "age", "visit_count", "create_time" -> sortBy;
            default -> "id";
        };
        String validOrder = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(validSort).append(" ").append(validOrder);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString());
        return ApiResponse.ok(rows);
    }

    @Transactional(readOnly = true)
    @ApiOperation("后台-用户统计（优化后使用 SQL 聚合）")
    @GetMapping("/admin/stats")
    public ApiResponse<Map<String, Object>> adminUserStats() {
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_user_info", Integer.class);
        int t = total != null ? total : 0;

        // 性别统计
        List<Map<String, Object>> genderRows = jdbcTemplate.queryForList(
            "SELECT gender, COUNT(*) as cnt FROM t_user_info GROUP BY gender");
        long male = 0, female = 0, unknownG = 0;
        for (Map<String, Object> row : genderRows) {
            String g = (String) row.get("gender");
            long cnt = ((Number) row.get("cnt")).longValue();
            if ("male".equalsIgnoreCase(g)) male = cnt;
            else if ("female".equalsIgnoreCase(g)) female = cnt;
            else unknownG = cnt;
        }
        int denom = Math.max(t, 1);

        Map<String, Object> gender = new LinkedHashMap<>();
        gender.put("male", male);
        gender.put("male_percent", (int) Math.round(male * 100.0 / denom));
        gender.put("female", female);
        gender.put("female_percent", (int) Math.round(female * 100.0 / denom));
        gender.put("unknown", unknownG);
        gender.put("unknown_percent", (int) Math.round(unknownG * 100.0 / denom));

        // 年龄统计 — SQL CASE 聚合
        List<Map<String, Object>> ageRows = jdbcTemplate.queryForList(
            "SELECT " +
            "  CASE " +
            "    WHEN age IS NULL OR age <= 0 THEN 'unknown' " +
            "    WHEN age < 18 THEN 'under_18' " +
            "    WHEN age <= 30 THEN '18_30' " +
            "    WHEN age <= 50 THEN '31_50' " +
            "    ELSE 'over_50' " +
            "  END as age_group, " +
            "  COUNT(*) as cnt " +
            "FROM t_user_info " +
            "GROUP BY " +
            "  CASE " +
            "    WHEN age IS NULL OR age <= 0 THEN 'unknown' " +
            "    WHEN age < 18 THEN 'under_18' " +
            "    WHEN age <= 30 THEN '18_30' " +
            "    WHEN age <= 50 THEN '31_50' " +
            "    ELSE 'over_50' " +
            "  END");
        Map<String, Long> ageMap = new LinkedHashMap<>();
        ageMap.put("under_18", 0L); ageMap.put("18_30", 0L); ageMap.put("31_50", 0L);
        ageMap.put("over_50", 0L); ageMap.put("unknown", 0L);
        for (Map<String, Object> row : ageRows) {
            String group = (String) row.get("age_group");
            ageMap.put(group, ((Number) row.get("cnt")).longValue());
        }

        Map<String, Object> age = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : ageMap.entrySet()) {
            age.put(e.getKey(), e.getValue());
            age.put(e.getKey() + "_percent", (int) Math.round(e.getValue() * 100.0 / denom));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", t);
        result.put("gender", gender);
        result.put("age", age);
        return ApiResponse.ok(result);
    }

    @ApiOperation("后台-删除用户")
    @DeleteMapping("/admin/delete")
    public ApiResponse<Void> adminDeleteUser(@RequestParam Long userId) {
        guideUserMapper.deleteById(userId);
        return ApiResponse.ok(null);
    }
}
