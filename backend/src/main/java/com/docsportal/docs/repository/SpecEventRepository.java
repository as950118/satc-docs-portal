package com.docsportal.docs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docsportal.docs.persistence.SpecEvent;

@Repository
public interface SpecEventRepository extends JpaRepository<SpecEvent, Long> {

    List<SpecEvent> findBySpecIdOrderByAtAscIdAsc(String specId);
}
