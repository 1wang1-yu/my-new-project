package com.guide.controller;

import com.guide.annotation.LogOperation;
import com.guide.pojo.dto.ApiResponse;
import com.guide.service.CheckInService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Api(tags = "游客端打卡")
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @ApiOperation(value = "获取景点打卡状态", notes = "传入GPS坐标，返回所有景点的距离和打卡状态。500m内自动打卡。")
    @GetMapping("/spots")
    @LogOperation("checkin_spots")
    public ApiResponse<Map<String, Object>> getSpots(
            @RequestParam(defaultValue = "10001") Long userId,
            @RequestParam Double lat,
            @RequestParam Double lng) {
        List<Map<String, Object>> spots = checkInService.getSpotsWithStatus(userId, lat, lng);
        Map<String, Object> summary = checkInService.getCheckInSummary(userId);
        return ApiResponse.ok(Map.of(
                "spots", spots,
                "summary", summary
        ));
    }

    @ApiOperation(value = "获取打卡记录", notes = "返回用户的历史打卡列表")
    @GetMapping("/checkins")
    @LogOperation("checkin_history")
    public ApiResponse<Map<String, Object>> getCheckIns(@RequestParam(defaultValue = "10001") Long userId) {
        List<Map<String, Object>> history = checkInService.getCheckInHistory(userId);
        Map<String, Object> summary = checkInService.getCheckInSummary(userId);
        return ApiResponse.ok(Map.of(
                "history", history,
                "summary", summary
        ));
    }

    @ApiOperation(value = "清除所有打卡记录")
    @DeleteMapping("/checkins")
    @LogOperation("checkin_clear")
    public ApiResponse<String> clearCheckIns(@RequestParam(defaultValue = "10001") Long userId) {
        checkInService.clearAllCheckIns(userId);
        return ApiResponse.ok("已清除所有打卡记录");
    }
}
