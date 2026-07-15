package com.guide.config;

import com.guide.entity.KnowledgeDoc;
import com.guide.mapper.KnowledgeDocMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 首次启动时向知识库插入示例数据，确保关键词检索有结果可返。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeDataSeeder implements CommandLineRunner {

    private final KnowledgeDocMapper knowledgeDocMapper;

    private static final List<Map<String, String>> SEED_DATA = List.of(
            Map.of("title", "灵山胜境概况",
                    "content", "灵山胜境位于江苏省无锡市，是国家5A级旅游景区，以佛教文化为主题。核心景点包括灵山大佛、梵宫、九龙灌浴、五印坛城等。景区占地面积约30公顷，集自然风光与人文景观于一体，是华东地区著名的佛教文化旅游目的地。"),
            Map.of("title", "灵山大佛",
                    "content", "灵山大佛高达88米，是迄今为止我国最高的巨型佛像之一。大佛采用锡青铜铸造，总用铜量达700余吨。佛像面朝太湖，背倚灵山，左手下垂结'与愿印'，右手屈臂上伸结'施无畏印'。游客可登临佛脚平台近距离瞻仰。"),
            Map.of("title", "梵宫",
                    "content", "梵宫是灵山胜境的核心建筑之一，外观融合了佛教石窟艺术与传统宫殿建筑风格。宫内金碧辉煌，拥有世界最大的佛教艺术穹顶壁画《华藏世界》，以及大型音乐喷泉表演《九龙灌浴》。梵宫还设有素斋餐厅和禅修体验区。"),
            Map.of("title", "九龙灌浴",
                    "content", "九龙灌浴是灵山胜境的大型动态音乐喷泉表演，再现了佛祖释迦牟尼诞生时'九龙吐水、沐浴太子'的佛教传说。表演每天定时举行，配合庄严的佛教音乐和水幕效果，是游客必看的核心演出之一。"),
            Map.of("title", "五印坛城",
                    "content", "五印坛城是藏传佛教风格的建筑群，仿照西藏布达拉宫而建。坛城内供奉有五方佛，四周环绕转经筒和玛尼堆。游客可以在这里体验转经、点灯祈福等藏传佛教文化活动，是了解藏传佛教文化的窗口。"),
            Map.of("title", "游览路线推荐",
                    "content", "推荐游览路线：景区入口→九龙灌浴（上午场10:00）→灵山大佛（乘电梯登佛脚）→梵宫（午餐素斋）→五印坛城→禅修体验区→出口。全程约3-4小时。建议上午9点前到达，避开人流高峰。景区内有电瓶车可代步，单程10元。"),
            Map.of("title", "美食推荐",
                    "content", "灵山胜境及周边美食：1）梵宫素斋：灵山素面、佛跳墙素版为招牌；2）景区外灵山小镇：太湖三白（白鱼、白虾、银鱼）为特色；3）无锡市区：酱排骨、油面筋、小笼包。景区内素斋人均50-80元，建议提前预约。"),
            Map.of("title", "交通指南",
                    "content", "前往灵山胜境交通方式：1）自驾：沪宁高速无锡北出口下，沿指示牌约30分钟；2）公交：无锡火车站乘88路直达景区，约1小时；3）地铁+公交：地铁1号线到市民中心站，换乘灵山专线大巴。景区停车场约2000个车位，节假日建议早到。"),
            Map.of("title", "门票与开放时间",
                    "content", "灵山胜境开放时间：夏季（4月-10月）7:30-17:30，冬季（11月-3月）8:00-17:00。门票价格：成人票210元，学生票105元（需有效学生证），60岁以上老人半价，70岁以上免票。网上提前一天购票可享9折优惠。团队20人以上请提前预约。"),
            Map.of("title", "拍照打卡点",
                    "content", "灵山胜境最佳拍照打卡位置：1）大佛正面广场，上午顺光可拍佛身全貌；2）梵宫东侧长廊，午后光影绝佳；3）九龙灌浴喷泉开启时抓拍水幕；4）五印坛城高处可俯拍全景；5）梵宫内穹顶壁画使用广角镜头。最佳拍摄时段为上午9:00-11:00和下午3:00-5:00。")
    );

    @Override
    @Transactional
    public void run(String... args) {
        if (isDataValid()) {
            long count = knowledgeDocMapper.count();
            log.info("知识库已有 {} 条记录，跳过种子数据", count);
            return;
        }

        log.warn("检测到知识库中文数据乱码（VARCHAR 存储导致），将清除重建...");
        knowledgeDocMapper.deleteAll();
        log.info("已清除旧数据，开始插入正确的种子数据...");

        LocalDateTime now = LocalDateTime.now();
        for (Map<String, String> item : SEED_DATA) {
            KnowledgeDoc doc = new KnowledgeDoc();
            doc.setTitle(item.get("title"));
            doc.setContent(item.get("content"));
            doc.setCategory("scenic");
            doc.setChunkCount(0);
            doc.setIndexStatus(0);
            doc.setOperatorId(0L);
            doc.setCreateTime(now);
            doc.setUpdateTime(now);
            knowledgeDocMapper.save(doc);
        }
        log.info("知识库种子数据插入完成，共 {} 条（已使用 NVARCHAR 编码）", SEED_DATA.size());
    }

    /** 检查知识库数据是否有效（中文未乱码） */
    private boolean isDataValid() {
        long count = knowledgeDocMapper.count();
        if (count == 0) return true; // 空库，需要插入
        // 取第一条标题检查是否包含中文字符
        List<KnowledgeDoc> docs = knowledgeDocMapper.findTopN(PageRequest.of(0, 1));
        if (!docs.isEmpty()) {
            String title = docs.get(0).getTitle();
            if (title != null) {
                for (char c : title.toCharArray()) {
                    if (c >= '一' && c <= '鿿') {
                        return true; // 包含汉字，数据正常
                    }
                }
            }
        }
        return false; // 没有汉字，说明乱码了
    }
}
