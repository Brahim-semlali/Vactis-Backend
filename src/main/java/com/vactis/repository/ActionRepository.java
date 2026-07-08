package com.vactis.repository;

import com.vactis.model.Action;
import com.vactis.model.EtatAction;
import com.vactis.model.SegmentMedecin;
import com.vactis.model.StatutPilotage;
import com.vactis.model.UrgenceAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActionRepository extends JpaRepository<Action, Long> {

    @Query("""
        select count(a)
        from Action a
    """)
    Long countAllActions();

    Long countByEtatAction(EtatAction etatAction);

    Long countByBacklogTrue();

    Long countByUrgenceSilenceTrue();

    @Query("""
        select a
        from Action a
        join fetch a.medecin m
        where (:search is null or :search = '' or
               lower(concat(m.nom, ' ', m.prenom)) like lower(concat('%', :search, '%')) or
               lower(m.specialite) like lower(concat('%', :search, '%')) or
               lower(a.actionRecommandee) like lower(concat('%', :search, '%')) or
               lower(a.commercial) like lower(concat('%', :search, '%')) or
               lower(a.lieuOrganisme) like lower(concat('%', :search, '%')))
          and (:statut is null or a.statut = :statut)
          and (:segment is null or a.segment = :segment)
          and (:action is null or :action = '' or a.actionRecommandee = :action)
          and (:urgence is null or a.urgence = :urgence)
          and (:etatAction is null or a.etatAction = :etatAction)
          and (:backlog is null or a.backlog = :backlog)
          and (:commercial is null or :commercial = '' or a.commercial = :commercial)
          and (:lieuOrganisme is null or :lieuOrganisme = '' or a.lieuOrganisme = :lieuOrganisme)
        order by a.dateVisite desc nulls last, m.nom, m.prenom
    """)
    List<Action> searchActions(
            @Param("search") String search,
            @Param("statut") StatutPilotage statut,
            @Param("segment") SegmentMedecin segment,
            @Param("action") String action,
            @Param("urgence") UrgenceAction urgence,
            @Param("etatAction") EtatAction etatAction,
            @Param("backlog") Boolean backlog,
            @Param("commercial") String commercial,
            @Param("lieuOrganisme") String lieuOrganisme
    );

    @Query("""
        select distinct a.actionRecommandee
        from Action a
        where a.actionRecommandee is not null
        order by a.actionRecommandee
    """)
    List<String> findDistinctActions();

    @Query("""
        select distinct a.commercial
        from Action a
        where a.commercial is not null
        order by a.commercial
    """)
    List<String> findDistinctCommerciaux();

    @Query("""
        select distinct a.lieuOrganisme
        from Action a
        where a.lieuOrganisme is not null
        order by a.lieuOrganisme
    """)
    List<String> findDistinctLieuxOrganismes();
}
