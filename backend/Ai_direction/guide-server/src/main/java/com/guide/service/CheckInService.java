package com.guide.service;

import com.guide.entity.CheckIn;
import com.guide.entity.ScenicSpot;
import com.guide.mapper.CheckInMapper;
import com.guide.mapper.ScenicSpotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInService {

    private static final double CHECK_IN_RADIUS_M = 500; // TODO: 测试完后改回 500

    private final ScenicSpotMapper scenicSpotMapper;
    private final CheckInMapper checkInMapper;

    /**
     * 获取所有景点及当前用户的打卡状态和距离。
     * 如果用户在景点 500m 内，自动记录打卡。
     */
    @Transactional
    public List<Map<String, Object>> getSpotsWithStatus(Long userId, Double userLat, Double userLng) {
        List<ScenicSpot> spots = scenicSpotMapper.findByStatusOrderBySortOrderAsc((short) 1);
        Set<Long> checkedInSpotIds = checkInMapper.findByUserIdOrderByCheckInTimeDesc(userId)
                .stream()
                .map(CheckIn::getSpotId)
                .collect(Collectors.toSet());

        List<Map<String, Object>> result = new ArrayList<>();
        boolean anyNewCheckIn = false;

        for (ScenicSpot spot : spots) {
            double spotLat = spot.getLocationLat().doubleValue();
            double spotLng = spot.getLocationLng().doubleValue();
            int distanceM = (int) haversineDistance(userLat, userLng, spotLat, spotLng);

            boolean checkedIn = checkedInSpotIds.contains(spot.getId());

            // 自动打卡：在范围内且未打卡
            if (!checkedIn && distanceM <= CHECK_IN_RADIUS_M) {
                CheckIn ci = new CheckIn();
                ci.setUserId(userId);
                ci.setSpotId(spot.getId());
                ci.setUserLat(userLat);
                ci.setUserLng(userLng);
                ci.setCheckInTime(LocalDateTime.now());
                checkInMapper.save(ci);
                checkedIn = true;
                anyNewCheckIn = true;
                log.info("自动打卡: userId={}, spot={}, distance={}m", userId, spot.getName(), distanceM);
            }

            Map<String, Object> spotMap = new LinkedHashMap<>();
            spotMap.put("id", spot.getId());
            spotMap.put("name", spot.getName());
            spotMap.put("category", spot.getCategory());
            spotMap.put("description", spot.getDescription());
            spotMap.put("openTime", spot.getOpenTime());
            spotMap.put("distanceM", distanceM);
            spotMap.put("checkedIn", checkedIn);
            spotMap.put("inRange", distanceM <= CHECK_IN_RADIUS_M);
            result.add(spotMap);
        }

        if (anyNewCheckIn) {
            log.info("本次新增打卡记录，userId={}", userId);
        }

        return result;
    }

    public List<Map<String, Object>> getCheckInHistory(Long userId) {
        List<CheckIn> checkIns = checkInMapper.findByUserIdOrderByCheckInTimeDesc(userId);
        List<ScenicSpot> spots = scenicSpotMapper.findAll();
        Map<Long, ScenicSpot> spotMap = spots.stream()
                .collect(Collectors.toMap(ScenicSpot::getId, s -> s));

        List<Map<String, Object>> result = new ArrayList<>();
        for (CheckIn ci : checkIns) {
            ScenicSpot spot = spotMap.get(ci.getSpotId());
            if (spot == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("spotName", spot.getName());
            m.put("category", spot.getCategory());
            m.put("checkInTime", ci.getCheckInTime().toString());
            result.add(m);
        }
        return result;
    }

    public Map<String, Object> getCheckInSummary(Long userId) {
        long totalSpots = scenicSpotMapper.count();
        long checkedCount = checkInMapper.findByUserIdOrderByCheckInTimeDesc(userId).stream()
                .map(CheckIn::getSpotId)
                .distinct()
                .count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalSpots", totalSpots);
        summary.put("checkedIn", checkedCount);
        summary.put("notCheckedIn", totalSpots - checkedCount);
        return summary;
    }

    @Transactional
    public void clearAllCheckIns(Long userId) {
        checkInMapper.deleteAllByUserId(userId);
        log.info("已清除所有打卡记录: userId={}", userId);
    }

    /**
     * Haversine公式计算两点间的球面距离（米）
     */
    static double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000; // 地球半径（米）
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
