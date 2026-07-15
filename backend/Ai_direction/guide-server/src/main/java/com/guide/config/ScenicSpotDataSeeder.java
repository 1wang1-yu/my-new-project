package com.guide.config;

import com.guide.entity.ScenicSpot;
import com.guide.mapper.ScenicSpotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 首次启动时向 t_scenic_spot 表插入景区景点及GPS坐标。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScenicSpotDataSeeder implements CommandLineRunner {

    private final ScenicSpotMapper scenicSpotMapper;

    private static final List<Map<String, Object>> SEED = List.of(
            Map.of("name", "灵山大佛", "category", "核心景点", "desc", "88米高青铜大佛，面朝太湖，可登佛脚平台瞻仰",
                    "lng", 120.0918, "lat", 31.4245, "openTime", "7:30-17:30", "sort", 1),
            Map.of("name", "梵宫", "category", "核心景点", "desc", "融合石窟艺术与宫殿建筑，拥有世界最大佛教穹顶壁画",
                    "lng", 120.0925, "lat", 31.4248, "openTime", "7:30-17:30", "sort", 2),
            Map.of("name", "九龙灌浴", "category", "演出", "desc", "大型动态音乐喷泉表演，再现佛祖诞生传说",
                    "lng", 120.0915, "lat", 31.4242, "openTime", "10:00/14:00", "sort", 3),
            Map.of("name", "五印坛城", "category", "核心景点", "desc", "藏传佛教风格建筑群，可体验转经、点灯祈福",
                    "lng", 120.0930, "lat", 31.4250, "openTime", "7:30-17:30", "sort", 4),
            Map.of("name", "景区入口广场", "category", "服务设施", "desc", "正门入口，设有游客中心、票务和导览服务",
                    "lng", 120.0905, "lat", 31.4235, "openTime", "7:00-18:00", "sort", 5),
            Map.of("name", "禅修体验区", "category", "体验", "desc", "提供禅修、抄经、茶道等体验活动",
                    "lng", 120.0935, "lat", 31.4255, "openTime", "9:00-16:30", "sort", 6),
            Map.of("name", "素斋餐厅", "category", "餐饮", "desc", "梵宫内素斋，灵山素面和素版佛跳墙为招牌",
                    "lng", 120.0920, "lat", 31.4246, "openTime", "11:00-14:00", "sort", 7),
            Map.of("name", "观景平台", "category", "观景", "desc", "可俯瞰太湖和灵山全景的最佳拍照位置",
                    "lng", 120.0928, "lat", 31.4240, "openTime", "全天", "sort", 8),
            Map.of("name", "文创商店", "category", "购物", "desc", "出售佛教文创产品、纪念品和本地特产",
                    "lng", 120.0908, "lat", 31.4238, "openTime", "8:30-17:00", "sort", 9),
            Map.of("name", "灵山小镇", "category", "周边", "desc", "景区外特色小镇，太湖三白和无锡小笼包等美食",
                    "lng", 120.0895, "lat", 31.4225, "openTime", "全天", "sort", 10)
    );

    @Override
    public void run(String... args) {
        long count = scenicSpotMapper.count();
        if (count > 0) {
            log.info("景区景点表已有 {} 条记录，跳过种子数据", count);
            return;
        }
        log.info("开始插入景区景点种子数据（含GPS坐标）...");
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> item : SEED) {
            ScenicSpot spot = new ScenicSpot();
            spot.setName((String) item.get("name"));
            spot.setCategory((String) item.get("category"));
            spot.setDescription((String) item.get("desc"));
            spot.setLocationLng(BigDecimal.valueOf((Double) item.get("lng")));
            spot.setLocationLat(BigDecimal.valueOf((Double) item.get("lat")));
            spot.setOpenTime((String) item.get("openTime"));
            spot.setSortOrder((Integer) item.get("sort"));
            spot.setStatus(1);
            spot.setCreateTime(now);
            spot.setUpdateTime(now);
            scenicSpotMapper.save(spot);
        }
        log.info("景区景点种子数据插入完成，共 {} 条", SEED.size());
    }
}
