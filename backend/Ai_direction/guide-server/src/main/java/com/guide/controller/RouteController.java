package com.guide.controller;

import com.guide.annotation.LogOperation;
import com.guide.entity.ScenicSpot;
import com.guide.mapper.KnowledgeDocMapper;
import com.guide.mapper.ScenicSpotMapper;
import com.guide.pojo.dto.ApiResponse;
import com.guide.pojo.dto.RouteRecommendDTO;
import com.guide.service.AnalyticsService;
import com.guide.service.RouteService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(tags = "游客端路线推荐")
@RestController
@RequestMapping("/api/v1/route")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    private final KnowledgeDocMapper knowledgeDocMapper;
    private final ScenicSpotMapper scenicSpotMapper;
    private final AnalyticsService analyticsService;

    @ApiOperation(value = "获取所有景点列表", notes = "返回知识库中所有可选的景点名称，供前端景点选择组件使用。")
    @GetMapping("/attractions")
    @LogOperation("route_attractions")
    public ApiResponse<List<String>> getAttractions() {
        List<String> attractions = knowledgeDocMapper.findDistinctScenicTitles();
        return ApiResponse.ok(attractions);
    }

    @ApiOperation(value = "按景点名称查询坐标", notes = "返回景点的经纬度，用于前端导航跳转。")
    @GetMapping("/attraction/coordinate")
    @LogOperation("route_attraction_coordinate")
    public ApiResponse<Map<String, Object>> getAttractionCoordinate(@RequestParam String name) {
        List<ScenicSpot> spots = scenicSpotMapper.findByStatusOrderBySortOrderAsc((short) 1);
        for (ScenicSpot spot : spots) {
            if (spot.getName() != null && spot.getName().contains(name)) {
                Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("name", spot.getName());
                result.put("latitude", spot.getLocationLat());
                result.put("longitude", spot.getLocationLng());
                return ApiResponse.ok(result);
            }
        }
        // 模糊匹配：按字符逐个匹配（处理用户输入"樊宫"→"梵宫"等常见错误）
        String bestName = null;
        ScenicSpot bestSpot = null;
        int bestScore = 0;
        for (ScenicSpot spot : spots) {
            if (spot.getName() == null) continue;
            int score = 0;
            String spotName = spot.getName();
            for (char c : name.toCharArray()) {
                if (spotName.indexOf(c) >= 0) score++;
            }
            if (score > bestScore && score >= Math.max(name.length() - 1, 1)) {
                bestScore = score;
                bestName = spotName;
                bestSpot = spot;
            }
        }
        if (bestSpot != null) {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("name", bestName);
            result.put("latitude", bestSpot.getLocationLat());
            result.put("longitude", bestSpot.getLocationLng());
            result.put("fuzzy", !name.equals(bestName));
            return ApiResponse.ok(result);
        }

        // 从完整景点坐标表查找（覆盖所有知识库景点）
        Map.Entry<String, double[]> coord = findApproximateCoord(name);
        if (coord != null) {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("name", coord.getKey());
            result.put("latitude", coord.getValue()[0]);
            result.put("longitude", coord.getValue()[1]);
            result.put("approx", true);
            return ApiResponse.ok(result);
        }
        return ApiResponse.fail("未找到景点: " + name);
    }

    /** 所有景点的近似坐标（覆盖知识库全部景点） */
    private static final Map<String, double[]> ALL_SPOT_COORDS = buildAllSpotCoords();
    private static Map<String, double[]> buildAllSpotCoords() {
        Map<String, double[]> m = new HashMap<>();
        // 灵山胜境核心景点
        m.put("灵山大照壁", new double[]{31.4230, 120.0900});
        m.put("五明桥", new double[]{31.4232, 120.0903});
        m.put("佛足坛", new double[]{31.4235, 120.0905});
        m.put("五智门", new double[]{31.4238, 120.0908});
        m.put("菩提大道", new double[]{31.4240, 120.0910});
        m.put("九龙灌浴", new double[]{31.4242, 120.0915});
        m.put("降魔浮雕", new double[]{31.4243, 120.0912});
        m.put("阿育王柱", new double[]{31.4240, 120.0918});
        m.put("百子戏弥勒", new double[]{31.4245, 120.0920});
        m.put("祥符禅寺", new double[]{31.4250, 120.0915});
        m.put("灵山大佛", new double[]{31.4255, 120.0918});
        m.put("佛教文化博览馆", new double[]{31.4252, 120.0916});
        m.put("灵山梵宫", new double[]{31.4248, 120.0925});
        m.put("五印坛城", new double[]{31.4250, 120.0930});
        m.put("曼飞龙塔", new double[]{31.4243, 120.0935});
        m.put("无尽意斋", new double[]{31.4246, 120.0928});
        m.put("佛手广场", new double[]{31.4240, 120.0912});
        // 拈花湾景点
        m.put("拈花广场", new double[]{31.4210, 120.0850});
        m.put("梵天花海", new double[]{31.4215, 120.0845});
        m.put("香月花街", new double[]{31.4218, 120.0855});
        m.put("拈花堂", new double[]{31.4220, 120.0860});
        m.put("五灯湖", new double[]{31.4212, 120.0848});
        m.put("鹿鸣谷", new double[]{31.4205, 120.0835});
        // 补充别名/简写
        m.put("梵宫", new double[]{31.4248, 120.0925});
        m.put("大佛", new double[]{31.4255, 120.0918});
        m.put("坛城", new double[]{31.4250, 120.0930});
        m.put("大照壁", new double[]{31.4230, 120.0900});
        return m;
    }

    /** 在完整坐标表中查找（精确+模糊） */
    private Map.Entry<String, double[]> findApproximateCoord(String name) {
        // 精确匹配
        for (Map.Entry<String, double[]> e : ALL_SPOT_COORDS.entrySet()) {
            if (e.getKey().equals(name) || e.getKey().contains(name) || name.contains(e.getKey())) {
                return e;
            }
        }
        // 模糊匹配（逐字符匹配，允许错别字）
        String bestKey = null;
        double[] bestVal = null;
        int bestScore = 0;
        for (Map.Entry<String, double[]> e : ALL_SPOT_COORDS.entrySet()) {
            int score = 0;
            for (char c : name.toCharArray()) {
                if (e.getKey().indexOf(c) >= 0) score++;
            }
            if (score > bestScore && score >= Math.max(name.length() - 1, 1)) {
                bestScore = score;
                bestKey = e.getKey();
                bestVal = e.getValue();
            }
        }
        if (bestKey != null) {
            return new java.util.AbstractMap.SimpleEntry<>(bestKey, bestVal);
        }
        return null;
    }

    @ApiOperation(value = "个性化路线推荐", notes = "根据游客兴趣、自定义偏好、指定景点、人数和时间段生成结构化路线。支持从聊天历史提取偏好。")
    @PostMapping("/recommend")
    @LogOperation("route_recommend")
    public ApiResponse<Map<String, Object>> recommend(@Valid @RequestBody RouteRecommendDTO req) {
        Map<String, Object> routeResult = routeService.generateRoutes(req);

        analyticsService.record("route", "{\"userId\":\"" + req.getUserId() + "\"}");
        // 返回路线列表 + 实际使用的起止时间（前端可用来更新时间选择器）
        return ApiResponse.ok(routeResult);
    }
}
