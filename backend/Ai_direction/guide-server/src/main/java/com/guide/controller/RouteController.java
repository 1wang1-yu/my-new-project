package com.guide.controller;

import com.guide.annotation.LogOperation;
import com.guide.pojo.dto.ApiResponse;
import com.guide.pojo.dto.RouteRecommendDTO;
import com.guide.service.AnalyticsService;
import com.guide.service.KnowledgeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Api(tags = "游客端路线推荐")
@RestController
@RequestMapping("/api/v1/route")
@RequiredArgsConstructor
public class RouteController {

    private final KnowledgeService knowledgeService;
    private final AnalyticsService analyticsService;

    @ApiOperation(value = "个性化路线推荐", notes = "根据游客兴趣和游玩时长生成结构化路线，适合小程序直接渲染。")
    @PostMapping("/recommend")
    @LogOperation("route_recommend")
    public ApiResponse<Map<String, Object>> recommend(@Valid @RequestBody RouteRecommendDTO req) {
        List<String> interests = req.getInterests() == null || req.getInterests().isEmpty()
                ? List.of("文化", "拍照")
                : req.getInterests();
        int durationMinutes = req.getDurationMinutes() == null ? 120 : req.getDurationMinutes();
        List<String> context = knowledgeService.retrieveContext(String.join(" ", interests), null, 3);

        List<Map<String, Object>> routes = List.of(
                Map.of(
                        "name", "AI 定制漫游线",
                        "stops", List.of("游客中心", "主景点区", "观景平台", "文创商店"),
                        "estimated_time", durationMinutes,
                        "highlights", List.of(
                                "贴合兴趣：" + String.join("、", interests),
                                "节奏舒适，适合小程序语音导览",
                                context.isEmpty() ? "已结合默认景区知识模板" : "已参考知识库内容"
                        )
                ),
                Map.of(
                        "name", "轻松打卡线",
                        "stops", List.of("入口广场", "热门拍照点", "休闲区"),
                        "estimated_time", Math.max(60, durationMinutes - 30),
                        "highlights", List.of("步行压力较小", "适合首次到访游客", "便于快速出片")
                )
        );

        analyticsService.record("route", "{\"userId\":\"" + req.getUserId() + "\"}");
        return ApiResponse.ok(Map.of("routes", routes));
    }
}
