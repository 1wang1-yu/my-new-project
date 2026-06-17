package com.guide.mapper;

import com.guide.entity.KnowledgeDoc;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeDocMapper extends JpaRepository<KnowledgeDoc, Long> {

    @Query("select k from KnowledgeDoc k where k.title like concat('%', :q, '%') "
            + "or (k.content is not null and k.content like concat('%', :q, '%'))")
    List<KnowledgeDoc> searchByKeyword(@Param("q") String q);

    @Query("select k from KnowledgeDoc k order by k.createTime desc")
    List<KnowledgeDoc> findTopN(Pageable pageable);
}
