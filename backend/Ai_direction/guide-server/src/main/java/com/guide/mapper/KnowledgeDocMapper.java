package com.guide.mapper;

import com.guide.entity.KnowledgeDoc;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeDocMapper extends JpaRepository<KnowledgeDoc, Long> {

    /** 获取所有景点名称（去重，按名称排序） */
    @Query("SELECT DISTINCT k.title FROM KnowledgeDoc k WHERE k.category = 'scenic' AND k.title IS NOT NULL ORDER BY k.title")
    List<String> findDistinctScenicTitles();

    @Query("select k from KnowledgeDoc k where k.title like concat('%', :q, '%') "
            + "or (k.content is not null and k.content like concat('%', :q, '%'))")
    List<KnowledgeDoc> searchByKeyword(@Param("q") String q);

    @Query("select k from KnowledgeDoc k order by k.createTime desc")
    List<KnowledgeDoc> findTopN(Pageable pageable);

    List<KnowledgeDoc> findByIndexStatus(int indexStatus);

    @Query("select k from KnowledgeDoc k where k.indexStatus in (0, 2)")
    List<KnowledgeDoc> findUnindexed();

    @Modifying
    @Query("update KnowledgeDoc k set k.indexStatus = 0")
    void resetAllIndexStatus();
}
